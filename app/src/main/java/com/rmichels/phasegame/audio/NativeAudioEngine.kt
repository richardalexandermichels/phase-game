package com.rmichels.phasegame.audio

import android.content.Context
import java.util.concurrent.atomic.AtomicLong

internal class NativeAudioEngine(
    context: Context
) : AudioEngine {
    private val appContext = context.applicationContext
    private val nextSessionId = AtomicLong(1L)
    private var nativeHandle = nativeCreate()
    private var prepared = false
    @Volatile
    private var started = false

    override val isReady: Boolean
        get() = prepared && nativeHandle != 0L

    @Synchronized
    override fun prepare() {
        check(nativeHandle != 0L)
        if (prepared) return
        SoundCatalog.rawResources.forEach { (sampleId, resourceId) ->
            val wav = appContext.resources.openRawResource(resourceId).use {
                it.readBytes()
            }
            check(nativeRegisterWav(nativeHandle, sampleId.value, wav)) {
                "Unable to register audio sample ${sampleId.value}."
            }
        }
        prepared = true
    }

    @Synchronized
    fun registerPcm16(
        sampleId: AudioSampleId,
        sampleRate: Int,
        samples: ShortArray
    ) {
        check(nativeRegisterPcm16(nativeHandle, sampleId.value, sampleRate, samples))
    }

    @Synchronized
    override fun start() {
        if (isReady && !started) {
            started = nativeStart(nativeHandle)
            check(started)
        }
    }

    @Synchronized
    override fun stop() {
        started = false
        if (nativeHandle != 0L) nativeStop(nativeHandle)
    }

    @Synchronized
    override fun release() {
        val handle = nativeHandle
        if (handle == 0L) return
        nativeHandle = 0L
        prepared = false
        started = false
        nativeDestroy(handle)
    }

    override fun createSession(): AudioSessionId =
        AudioSessionId(nextSessionId.getAndIncrement())

    override fun cancelSession(sessionId: AudioSessionId) {
        if (nativeHandle != 0L) nativeCancelSession(nativeHandle, sessionId.value)
    }

    override fun schedule(event: AudioEvent): Boolean {
        if (!isReady || !started) return false
        return nativeSchedule(
            nativeHandle,
            event.sampleId.value,
            event.targetElapsedRealtimeNanos,
            event.bus.ordinal,
            event.sessionId.value,
            event.gain,
            event.pan,
            event.playbackRate,
            event.attackMs,
            event.releaseMs,
            event.priority
        )
    }

    override fun setBusGain(bus: AudioBus?, gain: Float) {
        require(gain >= 0f)
        if (nativeHandle != 0L) {
            nativeSetBusGain(nativeHandle, bus?.ordinal ?: -1, gain)
        }
    }

    override fun diagnostics(): AudioDiagnostics {
        if (nativeHandle == 0L) return AudioDiagnostics()
        val values = nativeDiagnostics(nativeHandle)
        return AudioDiagnostics(
            sampleRate = values[0].toInt(),
            channelCount = values[1].toInt(),
            framesPerBurst = values[2].toInt(),
            bufferSizeFrames = values[3].toInt(),
            audioApi = values[4].toInt(),
            sharingMode = values[5].toInt(),
            performanceMode = values[6].toInt(),
            outputLatencyMs = values[7] / 1_000.0,
            xRunCount = values[8].toInt(),
            activeVoiceCount = values[9].toInt(),
            droppedCommandCount = values[10],
            lateEventCount = values[11]
        )
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeRegisterWav(
        handle: Long,
        sampleId: Int,
        bytes: ByteArray
    ): Boolean
    private external fun nativeRegisterPcm16(
        handle: Long,
        sampleId: Int,
        sampleRate: Int,
        samples: ShortArray
    ): Boolean
    private external fun nativeStart(handle: Long): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeCancelSession(handle: Long, sessionId: Long)
    private external fun nativeSchedule(
        handle: Long,
        sampleId: Int,
        targetNanos: Long,
        bus: Int,
        sessionId: Long,
        gain: Float,
        pan: Float,
        playbackRate: Float,
        attackMs: Float,
        releaseMs: Float,
        priority: Int
    ): Boolean
    private external fun nativeSetBusGain(handle: Long, bus: Int, gain: Float)
    private external fun nativeDiagnostics(handle: Long): LongArray

    private companion object {
        init {
            System.loadLibrary("phase_audio")
        }
    }
}
