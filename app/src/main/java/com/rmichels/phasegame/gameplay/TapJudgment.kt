package com.rmichels.phasegame.gameplay

private const val PERFECT_WINDOW_MS = 35L
private const val GOOD_WINDOW_MS = 70L
private const val CLOSE_WINDOW_MS = 120L
internal const val INPUT_COMPENSATION_MS = 10L

internal enum class TapJudgment(val label: String) {
    PERFECT("Perfect"),
    GOOD("Good"),
    CLOSE("Close"),
    MISS("Miss");

    companion object {
        fun fromDistance(distanceMs: Long): TapJudgment = when {
            distanceMs <= PERFECT_WINDOW_MS -> PERFECT
            distanceMs <= GOOD_WINDOW_MS -> GOOD
            distanceMs <= CLOSE_WINDOW_MS -> CLOSE
            else -> MISS
        }
    }
}

internal data class ExpectedHit(
    val absoluteBarIndex: Long,
    val stepIndex: Int,
    val distanceMs: Long
)

/**
 * Finds the closest valid player attack, including adjacent bars so taps near a
 * bar boundary are credited to the intended pattern and phase.
 */
internal fun findNearestExpectedHit(
    tapTimeMs: Long,
    rhythmClock: RhythmClock,
    minimumAbsoluteBarIndex: Long = 0L,
    targetRhythmForBar: (Long) -> List<Boolean>,
): ExpectedHit {
    val barDurationMs = rhythmClock.barDurationMs
    val tapElapsedMs = rhythmClock.elapsedMs(tapTimeMs)
    val tapAbsoluteBar = rhythmClock.absoluteBarIndex(tapTimeMs)

    return (-1L..1L)
        .map { barOffset -> tapAbsoluteBar + barOffset }
        .filter { candidateBar ->
            candidateBar >= minimumAbsoluteBarIndex
        }
        .flatMap { candidateBar ->
            val targetRhythm = targetRhythmForBar(candidateBar)
            targetRhythm.indices
                .filter { targetRhythm[it] }
                .map { hitIndex ->
                    val expectedTimeMs =
                        candidateBar * barDurationMs +
                                hitIndex * rhythmClock.stepDurationMs
                    ExpectedHit(
                        absoluteBarIndex = candidateBar,
                        stepIndex = hitIndex,
                        distanceMs = kotlin.math.abs(tapElapsedMs - expectedTimeMs)
                    )
                }
        }
        .minBy { it.distanceMs }
}
