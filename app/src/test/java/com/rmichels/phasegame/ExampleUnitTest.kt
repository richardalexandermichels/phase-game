package com.rmichels.phasegame

import com.rmichels.phasegame.audio.ACTIVE_BACKING_TRACK_ID
import com.rmichels.phasegame.audio.GeneratedBackingTrackCatalog
import com.rmichels.phasegame.audio.activeBackingTrack
import com.rmichels.phasegame.audio.activeBackingTrackMaxTier
import com.rmichels.phasegame.audio.activeBackingTrackStepDurationMs
import com.rmichels.phasegame.audio.isLoopBoundary
import com.rmichels.phasegame.audio.sampleForTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExampleUnitTest {
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
    fun popRockMelody_remainsStableUntilTheGameplayPhaseChanges() {
        val melody = PopRockPhaseMelody(
            stepCount = 16,
            random = Random(12_345)
        )
        val firstPhrase = melody.snapshotSemitoneOffsets()
        val firstGeneration = melody.generationCount()

        melody.selectPhase(0)
        assertEquals(firstPhrase, melody.snapshotSemitoneOffsets())
        assertEquals(firstGeneration, melody.generationCount())

        melody.selectPhase(1)
        assertEquals(firstGeneration + 1, melody.generationCount())
        assertTrue(firstPhrase != melody.snapshotSemitoneOffsets())
    }

    @Test
    fun popRockMelody_usesMajorKeyTonesAndChordAnchors() {
        val stepCount = 16
        val melody = PopRockPhaseMelody(
            stepCount = stepCount,
            random = Random(98_765)
        )
        val notes = melody.snapshotSemitoneOffsets()
        val chordRoots = melody.snapshotChordRoots()
        val majorPitchClasses = setOf(0, 2, 4, 5, 7, 9, 11)

        assertTrue(
            notes.all {
                Math.floorMod(it, 12) in majorPitchClasses
            }
        )
        chordRoots.indices.forEach { chordPosition ->
            val chordStart =
                chordPosition * stepCount / chordRoots.size
            assertTrue(
                PopRockPhaseMelody.isChordTone(
                    notes[chordStart],
                    chordRoots[chordPosition]
                )
            )
        }
    }

    @Test
    fun playerPhaseNote_isDiatonicAndNeverMatchesSimultaneousBaseNote() {
        val stepCount = 12
        val phase = 3
        val melody = PopRockPhaseMelody(
            stepCount = stepCount,
            random = Random(456)
        )
        melody.selectPhase(phase)
        val baseNotes = melody.snapshotSemitoneOffsets()
        val majorPitchClasses = setOf(0, 2, 4, 5, 7, 9, 11)

        repeat(stepCount) { playerStep ->
            val playerNote = melody.playerSemitoneForStep(
                playerStep,
                phase
            )
            assertTrue(
                Math.floorMod(playerNote, 12) in majorPitchClasses
            )
            assertTrue(playerNote != baseNotes[playerStep])
        }
    }

    private companion object {
        const val MAX_PATTERN_QUEUE_ITEMS_FOR_TEST = 11
    }
}
