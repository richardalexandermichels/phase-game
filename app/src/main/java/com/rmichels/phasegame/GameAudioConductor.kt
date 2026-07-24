package com.rmichels.phasegame

import android.os.SystemClock
import com.rmichels.phasegame.audio.AudioBus
import com.rmichels.phasegame.audio.AudioEngine
import com.rmichels.phasegame.audio.AudioEvent
import com.rmichels.phasegame.audio.AudioSessionId
import com.rmichels.phasegame.audio.SoundCatalog
import com.rmichels.phasegame.audio.activeBackingTrack
import com.rmichels.phasegame.audio.activeBackingTrackMaxTier
import com.rmichels.phasegame.audio.isLoopBoundary
import com.rmichels.phasegame.audio.sampleForTier
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
    private val phaseMelody: PopRockPhaseMelody
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeLayerCount = AtomicInteger(0)
    private val currentPercussionSession =
        AtomicLong(percussionSession.value)
    private var schedulingJob: Job? = null

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
            var nextAbsoluteStep = 0L
            while (isActive) {
                val nowNanos = SystemClock.elapsedRealtimeNanos()
                val startNanos = startTimeMs * 1_000_000L
                val elapsedNanos = (nowNanos - startNanos).coerceAtLeast(0L)
                val stepNanos = STEP_DURATION_MS * 1_000_000L
                val currentAbsoluteStep = elapsedNanos / stepNanos
                val scheduleThroughStep =
                    (elapsedNanos + LOOKAHEAD_NANOS) / stepNanos

                if (currentAbsoluteStep - nextAbsoluteStep > 1L) {
                    nextAbsoluteStep = currentAbsoluteStep
                }
                while (nextAbsoluteStep <= scheduleThroughStep) {
                    val step = (nextAbsoluteStep % baseRhythm.size).toInt()
                    val targetNanos =
                        startNanos + nextAbsoluteStep * stepNanos
                    if (baseRhythm[step]) {
                        scheduleBase(step, targetNanos)
                    }
                    scheduleBackingTrack(nextAbsoluteStep, targetNanos)
                    nextAbsoluteStep++
                }

                val nextTarget =
                    startNanos + nextAbsoluteStep * stepNanos -
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
                    sampleId = SoundCatalog.BASE_FALLBACK,
                    targetElapsedRealtimeNanos = targetNanos,
                    bus = AudioBus.BASE,
                    sessionId = baseSession,
                    playbackRate =
                        phaseMelody.playbackRateForBaseStep(step),
                    priority = 1
                )
            )
        } else {
            designedPitches.forEach { pitch ->
                audioEngine.schedule(
                    AudioEvent(
                        sampleId = SoundCatalog.designBase[pitch],
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
        const val LOOKAHEAD_NANOS = 100_000_000L
    }
}
