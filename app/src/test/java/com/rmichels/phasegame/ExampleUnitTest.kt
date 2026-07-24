package com.rmichels.phasegame

import org.junit.Assert.assertEquals
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
}
