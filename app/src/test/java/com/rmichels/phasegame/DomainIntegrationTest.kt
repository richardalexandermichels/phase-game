package com.rmichels.phasegame

import com.rmichels.phasegame.audio.ACTIVE_BACKING_TRACK_ID
import com.rmichels.phasegame.audio.GeneratedBackingTrackCatalog
import com.rmichels.phasegame.audio.SoundCatalog
import com.rmichels.phasegame.audio.activeBackingTrack
import com.rmichels.phasegame.audio.activeBackingTrackMaxTier
import com.rmichels.phasegame.audio.activeBackingTrackStepDurationMs
import com.rmichels.phasegame.audio.isLoopBoundary
import com.rmichels.phasegame.audio.sampleForTier
import com.rmichels.phasegame.design.DESIGN_MIN_CELL_WIDTH_DP
import com.rmichels.phasegame.design.GameDesign
import com.rmichels.phasegame.design.matrixPageRanges
import com.rmichels.phasegame.gameplay.MAX_PATTERN_STEPS
import com.rmichels.phasegame.gameplay.MIN_PATTERN_STEPS
import com.rmichels.phasegame.gameplay.RhythmClock
import com.rmichels.phasegame.gameplay.shiftedRhythm
import com.rmichels.phasegame.gameplay.validateBaseRhythm
import com.rmichels.phasegame.ui.game.regularQueueSlotsPerStep
import com.rmichels.phasegame.ui.game.startupQueueSlotsPerStep
import com.rmichels.phasegame.music.BASE_PIANO_HIGH_MIDI
import com.rmichels.phasegame.music.BASE_PIANO_LOW_MIDI
import com.rmichels.phasegame.music.GameInstrument
import com.rmichels.phasegame.music.PIANO_HAND_PITCH_COUNT
import com.rmichels.phasegame.music.PLAYER_PIANO_HIGH_MIDI
import com.rmichels.phasegame.music.PLAYER_PIANO_LOW_MIDI
import com.rmichels.phasegame.music.PianoScalePreset
import com.rmichels.phasegame.music.PopRockPhaseMelody
import com.rmichels.phasegame.music.pianoNoteLabel
import com.rmichels.phasegame.music.pitchIndicesForScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DomainIntegrationTest {
    @Test
    fun shiftedRhythm_rotatesOneStepEarlier() {
        val rhythm = listOf(true, false, false, true)

        assertEquals(
            listOf(false, false, true, true),
            shiftedRhythm(rhythm, phaseIndex = 1)
        )
    }

    @Test
    fun everySupportedPatternLength_drivesClockPhasingAndQueueTiming() {
        for (stepCount in MIN_PATTERN_STEPS..MAX_PATTERN_STEPS) {
            val rhythm = validateBaseRhythm(
                List(stepCount) { index -> index == 0 }
            )
            val clock = RhythmClock(
                stepDurationMs = 250L,
                stepsPerBar = rhythm.size
            )
            clock.start(nowMs = 1_000L)

            assertEquals(250L * stepCount, clock.barDurationMs)
            assertEquals(
                0L,
                clock.absoluteBarIndex(1_000L + clock.barDurationMs - 1L)
            )
            assertEquals(
                1L,
                clock.absoluteBarIndex(1_000L + clock.barDurationMs)
            )

            for (phase in 0 until stepCount) {
                assertEquals(
                    phase,
                    clock.phaseIndexForBar(
                        absoluteBarIndex = phase * 4L,
                        repetitionsPerPhase = 4L
                    )
                )
            }
            assertEquals(
                0,
                clock.phaseIndexForBar(
                    absoluteBarIndex = stepCount * 4L,
                    repetitionsPerPhase = 4L
                )
            )

            assertEquals(
                (MAX_PATTERN_QUEUE_ITEMS_FOR_TEST - 1).toFloat(),
                startupQueueSlotsPerStep(
                    patternStepCount = stepCount,
                    queueItemCount = MAX_PATTERN_QUEUE_ITEMS_FOR_TEST
                ) * stepCount,
                0.0001f
            )
            assertEquals(
                1.2f,
                regularQueueSlotsPerStep(stepCount) * stepCount,
                0.0001f
            )
        }
    }

    @Test
    fun everySupportedPatternLength_buildsAndResizesAValidDesign() {
        for (stepCount in MIN_PATTERN_STEPS..MAX_PATTERN_STEPS) {
            val rhythm = List(stepCount) { step -> step % 3 != 2 }
            val design = GameDesign.fromBaseRhythm(rhythm)

            assertEquals(stepCount, design.stepCount)
            assertEquals(rhythm, design.baseRhythm)
            assertEquals(stepCount, design.playerNotesByPhase.size)
            assertTrue(
                design.playerNotesByPhase.all { phase ->
                    phase.size == stepCount
                }
            )

            for (resizedCount in MIN_PATTERN_STEPS..MAX_PATTERN_STEPS) {
                val resized = design.resized(resizedCount)
                assertEquals(resizedCount, resized.stepCount)
                assertEquals(resizedCount, resized.baseNotes.size)
                assertEquals(resizedCount, resized.playerNotesByPhase.size)
                assertTrue(
                    resized.playerNotesByPhase.all { phase ->
                        phase.size == resizedCount
                    }
                )
            }
        }
    }

    @Test
    fun selectingAPitch_replacesOrClearsTheSingleNoteInAColumn() {
        var design = GameDesign.fromBaseRhythm(
            listOf(true, true, false, true)
        )

        design = design.withBaseNote(stepIndex = 0, pitchIndex = 2)
        assertEquals(listOf(2), design.baseNotes[0])

        design = design.withBaseNote(stepIndex = 0, pitchIndex = 9)
        assertEquals(listOf(9), design.baseNotes[0])

        design = design.withBaseNote(stepIndex = 0, pitchIndex = null)
        assertEquals(emptyList<Int>(), design.baseNotes[0])
        assertEquals(false, design.baseRhythm[0])
    }

    @Test
    fun playerColumns_followEachPhaseAndRejectDisabledEdits() {
        val rhythm = listOf(true, false, true, false, false)
        var design = GameDesign.fromBaseRhythm(rhythm)

        for (phase in rhythm.indices) {
            assertEquals(
                shiftedRhythm(rhythm, phase),
                design.enabledPlayerColumns(phase)
            )
        }

        val phase = 1
        val enabledStep =
            design.enabledPlayerColumns(phase).indexOfFirst { it }
        val disabledStep =
            design.enabledPlayerColumns(phase).indexOfFirst { !it }

        design = design.withPlayerNote(phase, enabledStep, pitchIndex = 5)
        assertEquals(5, design.playerNote(phase, enabledStep))

        val unchanged =
            design.withPlayerNote(phase, disabledStep, pitchIndex = 7)
        assertEquals(null, unchanged.playerNote(phase, disabledStep))
    }

    @Test
    fun baseEdits_removePlayerNotesThatBecomeDisabled() {
        var design = GameDesign.fromBaseRhythm(
            listOf(true, true, true, true)
        )
        design = design.withPlayerNote(
            phaseIndex = 0,
            stepIndex = 1,
            pitchIndex = 6
        )
        assertEquals(6, design.playerNote(0, 1))

        design = design.withBaseNote(stepIndex = 1, pitchIndex = null)

        assertEquals(false, design.enabledPlayerColumns(0)[1])
        assertEquals(null, design.playerNote(0, 1))
    }

    @Test
    fun pagination_usesOnePageWhenCellsFitAndTwoBalancedPagesOtherwise() {
        for (stepCount in MIN_PATTERN_STEPS..MAX_PATTERN_STEPS) {
            val onePage = matrixPageRanges(
                stepCount = stepCount,
                availableWidthDp =
                    stepCount * DESIGN_MIN_CELL_WIDTH_DP
            )
            assertEquals(listOf(0 until stepCount), onePage)

            val twoPages = matrixPageRanges(
                stepCount = stepCount,
                availableWidthDp =
                    stepCount * DESIGN_MIN_CELL_WIDTH_DP - 1f
            )
            assertEquals(2, twoPages.size)
            assertEquals((stepCount + 1) / 2, twoPages.first().count())
            assertEquals(stepCount / 2, twoPages.last().count())
            assertEquals(
                (0 until stepCount).toList(),
                twoPages.flatMap { it.toList() }
            )
        }
    }

    @Test
    fun blankPlayerCells_remainAvailableForTheRuntimeMotifFallback() {
        val design = GameDesign.fromBaseRhythm(
            listOf(true, false, true, true)
        )

        for (phase in 0 until design.stepCount) {
            for (step in 0 until design.stepCount) {
                assertEquals(null, design.playerNote(phase, step))
            }
        }
    }

    @Test
    fun chords_toggleInPitchOrderAndRespectTheMaximumSize() {
        var design = GameDesign.fromBaseRhythm(
            listOf(true, false, true, true)
        )
        design = design.withBaseNote(0, null)

        listOf(9, 2, 7, 4, 11).forEach { pitch ->
            design = design.toggledBasePitch(0, pitch)
        }

        assertEquals(listOf(2, 4, 7, 9), design.baseNotes[0])
        design = design.toggledBasePitch(0, 7)
        assertEquals(listOf(2, 4, 9), design.baseNotes[0])

        design = design.toggledPlayerPitch(0, 0, 5)
        assertEquals(listOf(5), design.playerNotes(0, 0))
        design = design.toggledPlayerPitch(0, 0, 8)
        assertEquals(listOf(5, 8), design.playerNotes(0, 0))
    }

    @Test
    fun generatedBackingTrack_matchesGameplayTempoAndTierProgression() {
        assertEquals(250L, activeBackingTrackStepDurationMs)
        assertEquals(
            activeBackingTrack.tiers.maxOf { it.tier },
            activeBackingTrackMaxTier
        )
        assertEquals(ACTIVE_BACKING_TRACK_ID, activeBackingTrack.id)
        assertTrue(
            GeneratedBackingTrackCatalog.tracks.contains(
                activeBackingTrack
            )
        )
        assertEquals(null, activeBackingTrack.sampleForTier(0))
        activeBackingTrack.tiers.forEach { tier ->
            assertEquals(
                tier.tier,
                activeBackingTrack.sampleForTier(tier.tier)?.tier
            )
        }
    }

    @Test
    fun generatedBackingTrack_schedulesOnlyAtItsLoopBoundary() {
        assertTrue(activeBackingTrack.isLoopBoundary(0))
        assertTrue(
            activeBackingTrack.isLoopBoundary(
                activeBackingTrack.stepCount.toLong()
            )
        )
        assertEquals(false, activeBackingTrack.isLoopBoundary(1))
    }

    @Test
    fun pianoArrangement_remainsStableUntilTheGameplayPhaseChanges() {
        val melody = PopRockPhaseMelody(
            stepCount = 16,
            random = Random(12_345)
        )
        val firstBasePhrase = melody.snapshotBaseMidiNotes()
        val firstPlayerPhrase = melody.snapshotPlayerMidiNotes()
        val firstGeneration = melody.generationCount()

        melody.selectPhase(0)
        assertEquals(firstBasePhrase, melody.snapshotBaseMidiNotes())
        assertEquals(firstPlayerPhrase, melody.snapshotPlayerMidiNotes())
        assertEquals(firstGeneration, melody.generationCount())

        melody.selectPhase(1)
        assertEquals(firstGeneration + 1, melody.generationCount())
    }

    @Test
    fun pianoArrangement_keepsBothHandsInsideTheCurrentChord() {
        val stepCount = 16
        val melody = PopRockPhaseMelody(
            stepCount = stepCount,
            random = Random(98_765)
        )
        val baseNotes = melody.snapshotBaseMidiNotes()
        val playerNotes = melody.snapshotPlayerMidiNotes()
        val chordTones = melody.snapshotChordTones()
        val tonic = melody.snapshotTonicPitchClass()
        val scalePitchClasses = melody.snapshotScale().intervals.mapTo(
            mutableSetOf()
        ) { interval ->
            Math.floorMod(tonic + interval, 12)
        }

        assertTrue((baseNotes + playerNotes).all { midi ->
            Math.floorMod(midi, 12) in scalePitchClasses
        })
        repeat(stepCount) { step ->
            val chordPosition =
                (step * chordTones.size / stepCount)
                    .coerceAtMost(chordTones.lastIndex)
            assertTrue(Math.floorMod(baseNotes[step], 12) in
                chordTones[chordPosition])
            assertTrue(Math.floorMod(playerNotes[step], 12) in
                chordTones[chordPosition])
        }
    }

    @Test
    fun pianoArrangement_keepsLeftAndRightHandsInNonCrossingRanges() {
        for (stepCount in MIN_PATTERN_STEPS..MAX_PATTERN_STEPS) {
            val melody = PopRockPhaseMelody(
                stepCount = stepCount,
                random = Random(456 + stepCount)
            )
            repeat(8) { phase ->
                melody.selectPhase(phase)
                val baseNotes = melody.snapshotBaseMidiNotes()
                val playerNotes = melody.snapshotPlayerMidiNotes()
                assertTrue(baseNotes.all {
                    it in BASE_PIANO_LOW_MIDI..BASE_PIANO_HIGH_MIDI
                })
                assertTrue(playerNotes.all {
                    it in PLAYER_PIANO_LOW_MIDI..PLAYER_PIANO_HIGH_MIDI
                })
                assertTrue(baseNotes.max() < playerNotes.min())
                repeat(stepCount) { step ->
                    assertTrue(melody.basePitchIndexForStep(step) in
                        0 until PIANO_HAND_PITCH_COUNT)
                    assertTrue(melody.playerPitchIndexForStep(step) in
                        0 until PIANO_HAND_PITCH_COUNT)
                }
            }
        }
    }

    @Test
    fun pianoArrangement_modulatesWithAPivotToneAndCompactVoiceLeading() {
        for (seed in 0 until 32) {
            val melody = PopRockPhaseMelody(
                stepCount = 16,
                random = Random(seed)
            )
            repeat(12) { phase ->
                val previousTonic = melody.snapshotTonicPitchClass()
                val previousScale = melody.snapshotScale()
                val previousLastChord =
                    melody.snapshotChordTones().last()
                val previousBaseEnd =
                    melody.snapshotBaseMidiNotes().last()
                val previousPlayerEnd =
                    melody.snapshotPlayerMidiNotes().last()

                melody.selectPhase(phase + 1)

                val nextFirstChord =
                    melody.snapshotChordTones().first()
                assertTrue(
                    previousLastChord.intersect(nextFirstChord).isNotEmpty()
                )
                assertTrue(
                    previousTonic != melody.snapshotTonicPitchClass() ||
                        previousScale != melody.snapshotScale()
                )
                assertTrue(
                    kotlin.math.abs(
                        melody.snapshotBaseMidiNotes().first() -
                            previousBaseEnd
                    ) <= 7
                )
                assertTrue(
                    kotlin.math.abs(
                        melody.snapshotPlayerMidiNotes().first() -
                            previousPlayerEnd
                    ) <= 5
                )
            }
        }
    }

    @Test
    fun designScaleFilters_coverChromaticAndPopularScaleRows() {
        assertEquals(
            (0 until PIANO_HAND_PITCH_COUNT).toList(),
            pitchIndicesForScale(
                BASE_PIANO_LOW_MIDI,
                rootPitchClass = 0,
                scale = PianoScalePreset.CHROMATIC
            )
        )
        assertEquals(
            14,
            pitchIndicesForScale(
                PLAYER_PIANO_LOW_MIDI,
                rootPitchClass = 0,
                scale = PianoScalePreset.MAJOR
            ).size
        )
        assertEquals(
            10,
            pitchIndicesForScale(
                PLAYER_PIANO_LOW_MIDI,
                rootPitchClass = 9,
                scale = PianoScalePreset.MINOR_PENTATONIC
            ).size
        )
        assertEquals("C2", pianoNoteLabel(BASE_PIANO_LOW_MIDI))
        assertEquals("B3", pianoNoteLabel(BASE_PIANO_HIGH_MIDI))
        assertEquals("C4", pianoNoteLabel(PLAYER_PIANO_LOW_MIDI))
        assertEquals("B5", pianoNoteLabel(PLAYER_PIANO_HIGH_MIDI))
    }

    @Test
    fun selectableInstrumentSamples_haveUniqueIdsOutsideTheBackingRange() {
        assertEquals(5, GameInstrument.values().size)
        GameInstrument.values().forEach { instrument ->
            assertEquals(
                PIANO_HAND_PITCH_COUNT,
                SoundCatalog.baseFor(instrument).size
            )
            assertEquals(
                PIANO_HAND_PITCH_COUNT,
                SoundCatalog.playerFor(instrument).size
            )
        }
        val instrumentIds = GameInstrument.values().flatMap { instrument ->
            SoundCatalog.baseFor(instrument) +
                SoundCatalog.playerFor(instrument)
        }
            .map { it.value }
        val backingIds = GeneratedBackingTrackCatalog.tracks
            .flatMap { it.tiers }
            .map { it.sampleId.value }

        assertEquals(instrumentIds.size, instrumentIds.distinct().size)
        assertTrue(instrumentIds.none { it in backingIds })
        assertTrue(instrumentIds.all { it in 1 until 320 })
    }

    @Test
    fun validateBaseRhythm_rejectsTwoStepPatterns() {
        assertThrows(IllegalArgumentException::class.java) {
            validateBaseRhythm(listOf(true, false))
        }
    }

    private companion object {
        const val MAX_PATTERN_QUEUE_ITEMS_FOR_TEST = 11
    }
}
