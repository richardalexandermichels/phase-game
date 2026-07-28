package com.rmichels.phasegame

import org.junit.Assert.assertEquals
import org.junit.Test

class RhythmClockTest {
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
    fun rhythmClock_pauseFreezesProgressAndResumePreservesPosition() {
        val clock = RhythmClock(stepDurationMs = 250L, stepsPerBar = 4)
        clock.start(nowMs = 1_000L)

        clock.pause(nowMs = 1_375L)
        assertEquals(375L, clock.elapsedMs(nowMs = 9_000L))
        assertEquals(0L, clock.absoluteBarIndex(nowMs = 9_000L))

        clock.resume(nowMs = 2_375L)
        assertEquals(375L, clock.elapsedMs(nowMs = 2_375L))
        assertEquals(625L, clock.elapsedMs(nowMs = 2_625L))
    }

    @Test
    fun rhythmClock_reportsPositionWithinCurrentBar(){
        val clock = RhythmClock(stepDurationMs = 250L, stepsPerBar = 16)
        clock.start(nowMs = 1_000L)
        assertEquals(
            0.25f,
            clock.progressThroughBar(nowMs = 2_000L),
            0.0001f
        )
        assertEquals(
            1_000L,
            clock.positionInBarMs(nowMs = 6_000L)
        )
        assertEquals(
            1L,
            clock.absoluteBarIndex(nowMs = 6_000L)
        )
    }
}