package com.rmichels.phasegame

import org.junit.Assert.assertEquals
import org.junit.Test

class PatternQueueTest {
    @Test
    fun completedBarOutcome_requiresEveryUniqueHitAndNoMiss() {
        val rhythm = listOf(true, false, true, true)
        val complete = BarPerformance().also {
            it.successfulHitIndices.addAll(listOf(0, 2, 3))
        }
        val duplicateOnly = BarPerformance().also {
            it.successfulHitIndices.addAll(listOf(0, 2))
        }
        val completeWithMiss = BarPerformance().also {
            it.successfulHitIndices.addAll(listOf(0, 2, 3))
            it.hadMiss = true
        }

        assertEquals(
            CompletedBarOutcome(true, 3),
            completedBarOutcome(complete, rhythm, 2, 4)
        )
        assertEquals(
            CompletedBarOutcome(false, 0),
            completedBarOutcome(duplicateOnly, rhythm, 2, 4)
        )
        assertEquals(
            CompletedBarOutcome(false, 0),
            completedBarOutcome(completeWithMiss, rhythm, 2, 4)
        )
        assertEquals(
            CompletedBarOutcome(true, 4),
            completedBarOutcome(complete, rhythm, 4, 4)
        )
    }

    @Test
    fun advanceGameplayQueue_onlyReplacesTheHeadAfterACleanBar() {
        val baseRhythm = listOf(true, false, true, false)
        val clock = RhythmClock(stepDurationMs = 250L, stepsPerBar = 4)
        val initialQueue = List(3) { logicalBar ->
            val phase = clock.phaseIndexForBar(logicalBar.toLong())
            QueuedPatternBar(
                absoluteBarIndex = logicalBar.toLong(),
                phaseIndex = phase,
                rhythm = shiftedRhythm(baseRhythm, phase)
            )
        }
        val cleanPerformance = BarPerformance().also {
            it.successfulHitIndices.addAll(listOf(0, 2))
        }

        val cleanTransition = advanceGameplayQueue(
            patternQueue = initialQueue,
            nextQueuedBar = 3L,
            performance = cleanPerformance,
            currentLayerCount = 1,
            maximumLayerCount = 4,
            rhythmClock = clock,
            baseRhythm = baseRhythm,
            maximumQueueItems = 3
        )
        assertEquals(true, cleanTransition.completedWithoutMisses)
        assertEquals(2, cleanTransition.nextLayerCount)
        assertEquals(4L, cleanTransition.nextQueuedBar)
        assertEquals(listOf(1L, 2L, 3L), cleanTransition.patternQueue.map {
            it.absoluteBarIndex
        })

        val failedTransition = advanceGameplayQueue(
            patternQueue = initialQueue,
            nextQueuedBar = 3L,
            performance = null,
            currentLayerCount = 2,
            maximumLayerCount = 4,
            rhythmClock = clock,
            baseRhythm = baseRhythm,
            maximumQueueItems = 3
        )
        assertEquals(false, failedTransition.completedWithoutMisses)
        assertEquals(0, failedTransition.nextLayerCount)
        assertEquals(3L, failedTransition.nextQueuedBar)
        assertEquals(initialQueue, failedTransition.patternQueue)
    }
}