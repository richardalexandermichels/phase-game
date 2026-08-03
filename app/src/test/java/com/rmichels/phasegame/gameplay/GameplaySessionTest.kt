package com.rmichels.phasegame.gameplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplaySessionTest {
    @Test
    fun cleanBar_advancesQueueAndBackingLayer() {
        val session = GameplaySession(
            baseRhythm = listOf(true, false, true, false),
            stepDurationMs = 250L,
            maximumLayerCount = 3,
            maximumQueueItems = 3
        )
        session.clock.start(nowMs = 1_000L)

        session.judgeTap(
            tapTimeMs = 1_000L,
            perfectModeEnabled = false,
            inputCompensationMs = 0L
        )
        session.judgeTap(
            tapTimeMs = 1_500L,
            perfectModeEnabled = false,
            inputCompensationMs = 0L
        )
        val advance = session.advanceTo(nowMs = 2_000L)
        val snapshot = session.snapshot()

        assertEquals(SessionAdvance(1, 1), advance)
        assertEquals(1, snapshot.activeLayerCount)
        assertEquals(1L, snapshot.patternQueue.first().absoluteBarIndex)
        assertFalse(snapshot.repeatCurrentPatternNextBar)
    }

    @Test
    fun miss_repeatsCurrentPatternAndResetsLayer() {
        val session = GameplaySession(
            baseRhythm = listOf(true, false, true, false),
            stepDurationMs = 250L,
            maximumLayerCount = 3,
            maximumQueueItems = 3
        )
        session.clock.start(nowMs = 1_000L)

        val result = session.judgeTap(
            tapTimeMs = 1_250L,
            perfectModeEnabled = false,
            inputCompensationMs = 0L
        )

        assertEquals(TapJudgment.MISS, result.judgment)
        assertTrue(session.snapshot().repeatCurrentPatternNextBar)
        assertEquals(0, session.snapshot().activeLayerCount)
    }

    @Test
    fun judgmentPattern_canAdvanceAcrossABarBoundary() {
        val current = QueuedPatternBar(
            absoluteBarIndex = 3L,
            phaseIndex = 0,
            rhythm = listOf(false, false, false, true)
        )
        val next = QueuedPatternBar(
            absoluteBarIndex = 4L,
            phaseIndex = 1,
            rhythm = listOf(true, false, false, false)
        )
        val completePerformance = BarPerformance().also {
            it.successfulHitIndices += 3
        }

        val judged = patternForJudgmentBar(
            candidateClockBar = 4L,
            currentClockBar = 3L,
            knownPatternsByClockBar = emptyMap(),
            patternQueue = listOf(current, next),
            currentPerformance = completePerformance,
            fallbackRhythm = current.rhythm
        )

        assertEquals(1, judged.phaseIndex)
        assertEquals(next.rhythm, judged.rhythm)
    }
}
