package com.rmichels.phasegame.audio

import android.os.SystemClock
import com.rmichels.phasegame.design.GameDesign
import com.rmichels.phasegame.music.GameInstrument
import com.rmichels.phasegame.music.PopRockPhaseMelody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Authors future music events away from the main/UI thread. Game rules stay in
 * Kotlin; the native callback only receives timestamped rendering commands.
 */
internal class GameAudioConductor(
    private val audioEngine: AudioEngine,
    private val baseSession: AudioSessionId,
    percussionSession: AudioSessionId,
    private val baseRhythm: List<Boolean>,
    private val gameDesign: GameDesign?,
    private val phaseMelody: PopRockPhaseMelody,
    baseInstrument: GameInstrument,
    stepDurationMs: Long
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeLayerCount = AtomicInteger(0)
    private val currentPercussionSession =
        AtomicLong(percussionSession.value)
    private val baseSamples = SoundCatalog.baseFor(baseInstrument)
    private val stepDurationNanos = stepDurationMs * NANOS_PER_MILLISECOND
    private var schedulingJob: Job? = null

    init {
        require(stepDurationMs > 0L)
    }

    fun setActiveLayerCount(count: Int) {
        activeLayerCount.set(
            count.coerceIn(0, activeBackingTrackMaxTier)
        )
    }

    fun cancelFuturePercussion() {
        val replacement = audioEngine.createSession()
        val canceled = AudioSessionId(
            currentPercussionSession.getAndSet(replacement.value)
        )
        audioEngine.cancelSession(canceled)
    }

    fun start(startTimeMs: Long) {
        if (schedulingJob?.isActive == true) return
        schedulingJob = scope.launch {
            val startNanos = startTimeMs * NANOS_PER_MILLISECOND
            var nextAbsoluteStep = firstStepAtOrAfter(
                elapsedNanos = SystemClock.elapsedRealtimeNanos() - startNanos,
                stepDurationNanos = stepDurationNanos
            )
            while (isActive) {
                val nowNanos = SystemClock.elapsedRealtimeNanos()
                val elapsedNanos = nowNanos - startNanos
                nextAbsoluteStep = maxOf(
                    nextAbsoluteStep,
                    firstStepAtOrAfter(elapsedNanos, stepDurationNanos)
                )
                val scheduleThroughStep = Math.floorDiv(
                    elapsedNanos + LOOKAHEAD_NANOS,
                    stepDurationNanos
                )
                while (nextAbsoluteStep <= scheduleThroughStep) {
                    val step = (nextAbsoluteStep % baseRhythm.size).toInt()
                    val targetNanos =
                        startNanos + nextAbsoluteStep * stepDurationNanos
                    if (baseRhythm[step]) {
                        scheduleBase(step, targetNanos)
                    }
                    scheduleBackingTrack(nextAbsoluteStep, targetNanos)
                    nextAbsoluteStep++
                }

                val nextTarget =
                    startNanos + nextAbsoluteStep * stepDurationNanos -
                        LOOKAHEAD_NANOS
                delay(
                    ((nextTarget - SystemClock.elapsedRealtimeNanos()) /
                        1_000_000L).coerceIn(1L, 20L)
                )
            }
        }
    }

    fun stop() {
        schedulingJob?.cancel()
        schedulingJob = null
    }

    fun release() {
        stop()
        audioEngine.cancelSession(
            AudioSessionId(currentPercussionSession.get())
        )
        scope.cancel()
    }

    private fun scheduleBase(
        step: Int,
        targetNanos: Long
    ) {
        val designedPitches = gameDesign?.baseNotes?.get(step)
        if (designedPitches.isNullOrEmpty()) {
            audioEngine.schedule(
                AudioEvent(
                    sampleId = baseSamples[
                        phaseMelody.basePitchIndexForStep(step)
                    ],
                    targetElapsedRealtimeNanos = targetNanos,
                    bus = AudioBus.BASE,
                    sessionId = baseSession,
                    priority = 1
                )
            )
        } else {
            designedPitches.forEach { pitch ->
                audioEngine.schedule(
                    AudioEvent(
                        sampleId = baseSamples[pitch],
                        targetElapsedRealtimeNanos = targetNanos,
                        bus = AudioBus.BASE,
                        sessionId = baseSession,
                        priority = 1
                    )
                )
            }
        }
    }

    private fun scheduleBackingTrack(
        absoluteStep: Long,
        targetNanos: Long
    ) {
        val tier = activeLayerCount.get()
        if (tier == 0 || !activeBackingTrack.isLoopBoundary(absoluteStep)) {
            return
        }
        val sample = activeBackingTrack.sampleForTier(tier) ?: return
        audioEngine.schedule(
            AudioEvent(
                sampleId = sample.sampleId,
                targetElapsedRealtimeNanos = targetNanos,
                bus = AudioBus.PERCUSSION,
                sessionId = AudioSessionId(
                    currentPercussionSession.get()
                )
            )
        )
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val LOOKAHEAD_NANOS = 100_000_000L
    }
}

/** Returns the first beat boundary that has not passed yet. */
internal fun firstStepAtOrAfter(
    elapsedNanos: Long,
    stepDurationNanos: Long
): Long {
    require(stepDurationNanos > 0L)
    if (elapsedNanos <= 0L) return 0L
    return 1L + (elapsedNanos - 1L) / stepDurationNanos
}
