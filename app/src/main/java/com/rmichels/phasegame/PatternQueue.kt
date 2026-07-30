package com.rmichels.phasegame

internal const val MAX_PATTERN_QUEUE_ITEMS = 11

internal class BarPerformance {
    val successfulHitIndices = mutableSetOf<Int>()
    var hadMiss = false
}

internal data class QueuedPatternBar(
    val absoluteBarIndex: Long,
    val phaseIndex: Int,
    val rhythm: List<Boolean>
)

internal data class CompletedBarOutcome(
    val completedWithoutMisses: Boolean,
    val nextLayerCount: Int
)

internal fun isCompletedWithoutMisses(
    performance: BarPerformance?,
    activeRhythm: List<Boolean>
): Boolean =
    performance != null &&
            !performance.hadMiss &&
            performance.successfulHitIndices.size ==
            activeRhythm.count { it }

internal fun rhythmForJudgmentBar(
    candidateClockBar: Long,
    currentClockBar: Long,
    knownRhythmsByClockBar: Map<Long, List<Boolean>>,
    patternQueue: List<QueuedPatternBar>,
    currentPerformance: BarPerformance?,
    fallbackRhythm: List<Boolean>
): List<Boolean> {
    knownRhythmsByClockBar[candidateClockBar]?.let {
        return it
    }

    val currentPattern =
        patternQueue.firstOrNull()?.rhythm ?: fallbackRhythm

    val shouldAdvanceForNextBar =
        candidateClockBar == currentClockBar + 1L &&
                isCompletedWithoutMisses(
                    performance = currentPerformance,
                    activeRhythm = currentPattern
                )

    return if (shouldAdvanceForNextBar) {
        patternQueue.getOrNull(1)?.rhythm ?: currentPattern
    } else {
        currentPattern
    }
}

internal fun completedBarOutcome(
    performance: BarPerformance?,
    activeRhythm: List<Boolean>,
    currentLayerCount: Int,
    maximumLayerCount: Int
): CompletedBarOutcome {
    val completedWithoutMisses =
        isCompletedWithoutMisses(performance, activeRhythm)
    return CompletedBarOutcome(
        completedWithoutMisses = completedWithoutMisses,
        nextLayerCount = if (completedWithoutMisses) {
            minOf(currentLayerCount + 1, maximumLayerCount)
        } else {
            0
        }
    )
}

internal data class QueueTransition(
    val patternQueue: List<QueuedPatternBar>,
    val nextQueuedBar: Long,
    val completedWithoutMisses: Boolean,
    val nextLayerCount: Int
)

internal fun advanceGameplayQueue(
    patternQueue: List<QueuedPatternBar>,
    nextQueuedBar: Long,
    performance: BarPerformance?,
    currentLayerCount: Int,
    maximumLayerCount: Int,
    rhythmClock: RhythmClock,
    baseRhythm: List<Boolean>,
    maximumQueueItems: Int = MAX_PATTERN_QUEUE_ITEMS
): QueueTransition {
    val activePattern = patternQueue.firstOrNull()
        ?: return QueueTransition(
            patternQueue = emptyList(),
            nextQueuedBar = nextQueuedBar,
            completedWithoutMisses = false,
            nextLayerCount = 0
        )
    val outcome = completedBarOutcome(
        performance = performance,
        activeRhythm = activePattern.rhythm,
        currentLayerCount = currentLayerCount,
        maximumLayerCount = maximumLayerCount
    )
    if (!outcome.completedWithoutMisses) {
        return QueueTransition(
            patternQueue = patternQueue,
            nextQueuedBar = nextQueuedBar,
            completedWithoutMisses = false,
            nextLayerCount = outcome.nextLayerCount
        )
    }

    val updatedQueue = patternQueue.drop(1).toMutableList()
    var updatedNextQueuedBar = nextQueuedBar
    if (updatedQueue.size < maximumQueueItems) {
        val queuedPhase = rhythmClock.phaseIndexForBar(nextQueuedBar)
        updatedQueue.add(
            QueuedPatternBar(
                absoluteBarIndex = nextQueuedBar,
                phaseIndex = queuedPhase,
                rhythm = shiftedRhythm(baseRhythm, queuedPhase)
            )
        )
        updatedNextQueuedBar++
    }
    return QueueTransition(
        patternQueue = updatedQueue,
        nextQueuedBar = updatedNextQueuedBar,
        completedWithoutMisses = true,
        nextLayerCount = outcome.nextLayerCount
    )
}
