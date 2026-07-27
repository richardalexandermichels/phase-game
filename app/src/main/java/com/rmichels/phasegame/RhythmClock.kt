package com.rmichels.phasegame

import android.os.SystemClock
private const val REPETITIONS_PER_PHASE = 4L
/**
 * Converts one monotonic start time into bar, phase, and animation positions.
 * All rhythm systems must use this clock so their timing cannot drift apart.
 */
internal class RhythmClock(
    val stepDurationMs: Long,
    private val stepsPerBar: Int
) {
    init {
        require(stepDurationMs > 0L)
        require(stepsPerBar > 0)
    }

    var startTimeMs: Long = 0L
        private set

    private var pausedAtMs: Long? = null

    val barDurationMs: Long = stepDurationMs * stepsPerBar
    val isStarted: Boolean get() = startTimeMs != 0L

    fun start(nowMs: Long = SystemClock.elapsedRealtime()) {
        startTimeMs = nowMs
        pausedAtMs = null
    }

    fun scheduleStart(startAtMs: Long) {
        startTimeMs = startAtMs
        pausedAtMs = null
    }

    fun pause(nowMs: Long = SystemClock.elapsedRealtime()) {
        if (isStarted && pausedAtMs == null) pausedAtMs = nowMs
    }

    fun resume(nowMs: Long = SystemClock.elapsedRealtime()) {
        val pauseStartedMs = pausedAtMs ?: return
        startTimeMs += (nowMs - pauseStartedMs).coerceAtLeast(0L)
        pausedAtMs = null
    }

    fun elapsedMs(nowMs: Long = SystemClock.elapsedRealtime()): Long =
        (effectiveNowMs(nowMs) - startTimeMs).coerceAtLeast(0L)

    fun progressThroughBar(nowMs: Long = SystemClock.elapsedRealtime()): Float =
        Math.floorMod(elapsedMs(nowMs), barDurationMs).toFloat() / barDurationMs

    fun positionInBarMs(nowMs: Long): Long =
        Math.floorMod(effectiveNowMs(nowMs) - startTimeMs, barDurationMs)

    fun absoluteBarIndex(nowMs: Long): Long =
        elapsedMs(nowMs) / barDurationMs

    fun phaseIndexForBar(
        absoluteBarIndex: Long,
        repetitionsPerPhase: Long = REPETITIONS_PER_PHASE
    ): Int =
        ((absoluteBarIndex / repetitionsPerPhase) % stepsPerBar).toInt()

    fun phaseIndex(
        nowMs: Long,
        repetitionsPerPhase: Long = REPETITIONS_PER_PHASE
    ): Int = phaseIndexForBar(absoluteBarIndex(nowMs), repetitionsPerPhase)

    private fun effectiveNowMs(nowMs: Long): Long = pausedAtMs ?: nowMs
}
