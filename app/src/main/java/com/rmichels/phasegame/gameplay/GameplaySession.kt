package com.rmichels.phasegame.gameplay

/**
 * Pure Kotlin owner for a single run's mutable rules state.
 *
 * Compose owns presentation state (animations, menus, labels); this class owns
 * the authoritative clock, queue, scoreable hits, and layer progression. Keep
 * Android and audio calls out of this class so game-loop changes remain easy to
 * unit test.
 */
internal class GameplaySession(
    baseRhythm: List<Boolean>,
    stepDurationMs: Long,
    private val maximumLayerCount: Int,
    private val maximumQueueItems: Int = MAX_PATTERN_QUEUE_ITEMS
) {
    val baseRhythm = validateBaseRhythm(baseRhythm)
    val clock = RhythmClock(
        stepDurationMs = stepDurationMs,
        stepsPerBar = this.baseRhythm.size
    )

    private val mutablePatternQueue = mutableListOf<QueuedPatternBar>()
    private val performancesByClockBar = mutableMapOf<Long, BarPerformance>()
    private val activePatternByClockBar = mutableMapOf<Long, QueuedPatternBar>()
    private var lastProcessedAbsoluteBar = 0L
    private var nextQueuedBar = maximumQueueItems.toLong()

    val patternQueue: List<QueuedPatternBar>
        get() = mutablePatternQueue

    var activeLayerCount: Int = 0
        private set

    var repeatCurrentPatternNextBar: Boolean = false
        private set

    val currentPhaseIndex: Int
        get() = mutablePatternQueue.firstOrNull()?.phaseIndex ?: 0

    fun snapshot(): GameplaySnapshot = GameplaySnapshot(
        patternQueue = mutablePatternQueue.toList(),
        activeLayerCount = activeLayerCount,
        currentPhaseIndex = currentPhaseIndex,
        repeatCurrentPatternNextBar = repeatCurrentPatternNextBar
    )

    init {
        require(maximumLayerCount >= 0)
        require(maximumQueueItems > 1)
        repeat(maximumQueueItems) { absoluteBar ->
            val phase = clock.phaseIndexForBar(absoluteBar.toLong())
            mutablePatternQueue += QueuedPatternBar(
                absoluteBarIndex = absoluteBar.toLong(),
                phaseIndex = phase,
                rhythm = shiftedRhythm(this.baseRhythm, phase)
            )
        }
        activePatternByClockBar[0L] = mutablePatternQueue.first()
    }

    fun advanceTo(nowMs: Long): SessionAdvance =
        processCompletedBarsThrough(clock.absoluteBarIndex(nowMs))

    fun judgeTap(
        tapTimeMs: Long,
        perfectModeEnabled: Boolean,
        inputCompensationMs: Long = INPUT_COMPENSATION_MS
    ): PlayerTapResult {
        val compensatedTapTimeMs = tapTimeMs - inputCompensationMs
        val currentClockBar = clock.absoluteBarIndex(compensatedTapTimeMs)
        val advance = processCompletedBarsThrough(currentClockBar)
        val currentPerformance = performancesByClockBar[currentClockBar]

        fun patternForBar(candidateClockBar: Long): QueuedPatternBar =
            patternForJudgmentBar(
                candidateClockBar = candidateClockBar,
                currentClockBar = currentClockBar,
                knownPatternsByClockBar = activePatternByClockBar,
                patternQueue = mutablePatternQueue,
                currentPerformance = currentPerformance,
                fallbackRhythm = baseRhythm
            )

        val nearestHit = findNearestExpectedHit(
            tapTimeMs = compensatedTapTimeMs,
            rhythmClock = clock,
            minimumAbsoluteBarIndex = lastProcessedAbsoluteBar,
            targetRhythmForBar = { candidateClockBar ->
                patternForBar(candidateClockBar).rhythm
            }
        )
        val measuredJudgment = TapJudgment.fromDistance(nearestHit.distanceMs)
        val judgment = if (
            perfectModeEnabled && measuredJudgment != TapJudgment.MISS
        ) {
            TapJudgment.PERFECT
        } else {
            measuredJudgment
        }
        val judgedPattern = patternForBar(nearestHit.absoluteBarIndex)
        val performance = performancesByClockBar.getOrPut(
            nearestHit.absoluteBarIndex
        ) {
            BarPerformance()
        }

        if (judgment == TapJudgment.MISS) {
            performance.hadMiss = true
            repeatCurrentPatternNextBar = true
            activeLayerCount = 0
        } else {
            performance.successfulHitIndices += nearestHit.stepIndex
        }

        return PlayerTapResult(
            judgment = judgment,
            absoluteBarIndex = nearestHit.absoluteBarIndex,
            phaseIndex = judgedPattern.phaseIndex,
            stepIndex = nearestHit.stepIndex,
            advance = advance
        )
    }

    private fun processCompletedBarsThrough(
        targetAbsoluteBar: Long
    ): SessionAdvance {
        if (targetAbsoluteBar <= lastProcessedAbsoluteBar) {
            return SessionAdvance.NONE
        }

        var completedBarCount = 0
        var successfulBarCount = 0
        for (completedBar in lastProcessedAbsoluteBar until targetAbsoluteBar) {
            val activePattern = mutablePatternQueue.firstOrNull() ?: break
            activePatternByClockBar.putIfAbsent(completedBar, activePattern)
            val transition = advanceGameplayQueue(
                patternQueue = mutablePatternQueue,
                nextQueuedBar = nextQueuedBar,
                performance = performancesByClockBar.remove(completedBar),
                currentLayerCount = activeLayerCount,
                maximumLayerCount = maximumLayerCount,
                rhythmClock = clock,
                baseRhythm = baseRhythm,
                maximumQueueItems = maximumQueueItems
            )
            completedBarCount++
            activeLayerCount = transition.nextLayerCount

            if (transition.completedWithoutMisses) {
                successfulBarCount++
                mutablePatternQueue.clear()
                mutablePatternQueue.addAll(transition.patternQueue)
                nextQueuedBar = transition.nextQueuedBar
            }

            mutablePatternQueue.firstOrNull()?.let { nextActivePattern ->
                activePatternByClockBar[completedBar + 1L] = nextActivePattern
            }
        }

        repeatCurrentPatternNextBar = false
        lastProcessedAbsoluteBar = targetAbsoluteBar
        activePatternByClockBar.keys.removeAll { clockBar ->
            clockBar < targetAbsoluteBar - RETAINED_PATTERN_HISTORY_BARS
        }
        return SessionAdvance(
            completedBarCount = completedBarCount,
            successfulBarCount = successfulBarCount
        )
    }

    private companion object {
        const val RETAINED_PATTERN_HISTORY_BARS = 2L
    }
}

internal data class SessionAdvance(
    val completedBarCount: Int,
    val successfulBarCount: Int
) {
    companion object {
        val NONE = SessionAdvance(0, 0)
    }
}

internal data class PlayerTapResult(
    val judgment: TapJudgment,
    val absoluteBarIndex: Long,
    val phaseIndex: Int,
    val stepIndex: Int,
    val advance: SessionAdvance
)

internal data class GameplaySnapshot(
    val patternQueue: List<QueuedPatternBar>,
    val activeLayerCount: Int,
    val currentPhaseIndex: Int,
    val repeatCurrentPatternNextBar: Boolean
)
