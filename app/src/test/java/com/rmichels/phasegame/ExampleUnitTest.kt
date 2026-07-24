package com.rmichels.phasegame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun rhythmClock_derivesBarsAndPhasesFromOneOrigin() {
        val clock = RhythmClock(stepDurationMs = 250L, stepsPerBar = 12)
        clock.start(nowMs = 1_000L)

        assertEquals(0L, clock.absoluteBarIndex(nowMs = 3_999L))
        assertEquals(1L, clock.absoluteBarIndex(nowMs = 4_000L))
        assertEquals(0, clock.phaseIndexForBar(3L, repetitionsPerPhase = 4L))
        assertEquals(1, clock.phaseIndexForBar(4L, repetitionsPerPhase = 4L))
        assertEquals(0, clock.phaseIndexForBar(48L, repetitionsPerPhase = 4L))
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
    fun everySupportedPatternLength_rotatesAndDrawsGenerically() {
        for (stepCount in MIN_PATTERN_STEPS..MAX_PATTERN_STEPS) {
            val rhythm = List(stepCount) { index -> index == 0 }

            for (phase in 0 until stepCount) {
                val shifted = shiftedRhythm(rhythm, phase)
                assertEquals(stepCount, shifted.size)
                assertEquals(1, shifted.count { it })
            }
            assertEquals(rhythm, shiftedRhythm(rhythm, stepCount))

            val firstDotCenter = rhythmCursorOffsetDp(
                barProgress = 0f,
                patternStepCount = stepCount
            )
            val dotStride = rhythmDotStrideDp(stepCount)
            assertTrue(dotStride * stepCount <= 336.0001f)
            for (step in 0 until stepCount) {
                assertEquals(
                    firstDotCenter + step * dotStride,
                    rhythmCursorOffsetDp(
                        barProgress = step.toFloat() / stepCount,
                        patternStepCount = stepCount
                    ),
                    0.0001f
                )
            }
        }
    }

    @Test
    fun everySupportedPatternLength_dividesPitchAndWrapsLayersSafely() {
        val shortLayerPattern = listOf(true, false, false)

        for (stepCount in MIN_PATTERN_STEPS..MAX_PATTERN_STEPS) {
            val sections = (0 until stepCount).map { step ->
                pitchSectionForStep(step, stepCount)
            }

            assertEquals(0, sections.first())
            assertTrue(sections.all { it in 0..3 })
            assertTrue(sections.zipWithNext().all { (left, right) -> left <= right })
            assertEquals(minOf(4, stepCount), sections.distinct().size)

            for (step in 0 until stepCount) {
                assertEquals(
                    shortLayerPattern[step % shortLayerPattern.size],
                    repeatingPatternValue(shortLayerPattern, step)
                )
            }
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
        assertEquals(2, design.baseNotes[0])

        design = design.withBaseNote(stepIndex = 0, pitchIndex = 9)
        assertEquals(9, design.baseNotes[0])

        design = design.withBaseNote(stepIndex = 0, pitchIndex = null)
        assertEquals(null, design.baseNotes[0])
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

    private companion object {
        const val MAX_PATTERN_QUEUE_ITEMS_FOR_TEST = 11
    }
}
