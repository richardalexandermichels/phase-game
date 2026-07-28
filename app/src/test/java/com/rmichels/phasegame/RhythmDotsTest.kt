package com.rmichels.phasegame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmDotsTest {
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
}