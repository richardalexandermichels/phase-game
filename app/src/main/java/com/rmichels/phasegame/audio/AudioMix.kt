package com.rmichels.phasegame.audio

/**
 * The game's default mix. Keep baseline bus levels together so sound balancing
 * does not become scattered across screens or playback call sites.
 */
internal object DefaultAudioMix {
    const val MASTER_GAIN = 1f
    const val BASE_GAIN = 1f
    const val PLAYER_GAIN = 1f
    const val PERCUSSION_GAIN = 0.7f
    const val VOICE_GAIN = 1f

    fun applyTo(audioEngine: AudioEngine) {
        audioEngine.setBusGain(null, MASTER_GAIN)
        audioEngine.setBusGain(AudioBus.BASE, BASE_GAIN)
        audioEngine.setBusGain(AudioBus.PLAYER, PLAYER_GAIN)
        audioEngine.setBusGain(AudioBus.PERCUSSION, PERCUSSION_GAIN)
        audioEngine.setBusGain(AudioBus.VOICE, VOICE_GAIN)
    }
}
