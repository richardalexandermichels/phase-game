package com.rmichels.phasegame

import org.junit.Assert.assertEquals
import org.junit.Test

class RhythmIndicatorsTest {
    @Test
    fun futureIndicators_includeRepeatedAndShiftedQueuedNotes() {
        val indicators = futureRhythmIndicators(
            queuedRhythms = listOf(
                listOf(true, false, false, false),
                listOf(true, false, true, false)
            ),
            currentStepPosition = 0f
        )

        assertEquals(3, indicators.size)

        assertEquals(0, indicators[0].stepIndex)
        assertEquals(0f, indicators[0].stepsUntilHit)

        assertEquals(0, indicators[1].stepIndex)
        assertEquals(4f, indicators[1].stepsUntilHit)

        assertEquals(2, indicators[2].stepIndex)
        assertEquals(6f, indicators[2].stepsUntilHit)
    }
}
