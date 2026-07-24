package com.rmichels.phasegame.audio

@JvmInline
internal value class AudioSampleId(val value: Int)

@JvmInline
internal value class AudioSessionId(val value: Long)

internal enum class AudioBus {
    BASE,
    PLAYER,
    PERCUSSION,
    VOICE
}

internal data class AudioEvent(
    val sampleId: AudioSampleId,
    val targetElapsedRealtimeNanos: Long,
    val bus: AudioBus,
    val sessionId: AudioSessionId,
    val gain: Float = 1f,
    val pan: Float = 0f,
    val playbackRate: Float = 1f,
    val attackMs: Float = 0f,
    val releaseMs: Float = 0f,
    val priority: Int = 0
) {
    init {
        require(targetElapsedRealtimeNanos >= 0L)
        require(gain >= 0f)
        require(pan in -1f..1f)
        require(playbackRate > 0f)
        require(attackMs >= 0f)
        require(releaseMs >= 0f)
    }
}

internal data class AudioDiagnostics(
    val sampleRate: Int = 0,
    val channelCount: Int = 0,
    val framesPerBurst: Int = 0,
    val bufferSizeFrames: Int = 0,
    val audioApi: Int = 0,
    val sharingMode: Int = 0,
    val performanceMode: Int = 0,
    val outputLatencyMs: Double = -1.0,
    val xRunCount: Int = 0,
    val activeVoiceCount: Int = 0,
    val droppedCommandCount: Long = 0L,
    val lateEventCount: Long = 0L
)
