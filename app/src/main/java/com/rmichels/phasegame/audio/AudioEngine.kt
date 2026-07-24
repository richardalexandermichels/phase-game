package com.rmichels.phasegame.audio

import android.os.SystemClock

internal interface AudioEngine {
    val isReady: Boolean

    fun prepare()
    fun start()
    fun stop()
    fun release()
    fun createSession(): AudioSessionId
    fun cancelSession(sessionId: AudioSessionId)
    fun schedule(event: AudioEvent): Boolean
    fun setBusGain(bus: AudioBus?, gain: Float)
    fun diagnostics(): AudioDiagnostics

    fun playImmediate(
        sampleId: AudioSampleId,
        bus: AudioBus,
        sessionId: AudioSessionId,
        gain: Float = 1f,
        pan: Float = 0f,
        playbackRate: Float = 1f,
        priority: Int = 0
    ): Boolean = schedule(
        AudioEvent(
            sampleId = sampleId,
            targetElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            bus = bus,
            sessionId = sessionId,
            gain = gain,
            pan = pan,
            playbackRate = playbackRate,
            priority = priority
        )
    )
}
