package com.rmichels.phasegame

import org.junit.Assert.assertEquals
import org.junit.Test

class TapJudgmentTest {
    @Test
    fun nearestHit_usesThePatternAssignedToEachAdjacentBar() {
        val clock = RhythmClock(stepDurationMs = 250L, stepsPerBar = 4)
        clock.start(nowMs = 1_000L)
        val patterns = mapOf(
            0L to listOf(false, false, false, true),
            1L to listOf(true, false, false, false)
        )

        val hit = findNearestExpectedHit(
            tapTimeMs = 1_990L,
            rhythmClock = clock,
            targetRhythmForBar = { bar -> patterns.getValue(bar.coerceIn(0L, 1L)) }
        )

        assertEquals(1L, hit.absoluteBarIndex)
        assertEquals(0, hit.stepIndex)
        assertEquals(10L, hit.distanceMs)
    }

    @Test
    fun tapJudgment_usesAccuracyWindowBoundaries() {
        assertEquals(
            TapJudgment.PERFECT,
            TapJudgment.fromDistance(0L)
        )
        assertEquals(
            TapJudgment.PERFECT,
            TapJudgment.fromDistance(35L)
        )
        assertEquals(
            TapJudgment.GOOD,
            TapJudgment.fromDistance(36L)
        )
        assertEquals(
            TapJudgment.GOOD,
            TapJudgment.fromDistance(70L)
        )
        assertEquals(
            TapJudgment.CLOSE,
            TapJudgment.fromDistance(71L)
        )
        assertEquals(
            TapJudgment.CLOSE,
            TapJudgment.fromDistance(120L)
        )
        assertEquals(
            TapJudgment.MISS,
            TapJudgment.fromDistance(121L)
        )
    }
}