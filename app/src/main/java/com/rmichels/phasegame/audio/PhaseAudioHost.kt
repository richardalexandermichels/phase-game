package com.rmichels.phasegame.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.rmichels.phasegame.audio.voice.AllophoneSpeechSynthesizer
import com.rmichels.phasegame.audio.voice.VocalBark

/**
 * Activity-owned lifecycle boundary for the native engine.
 *
 * Screens receive only [AudioEngine]; sample loading, generated PCM, the
 * default mix, and native resource cleanup stay in one Android-facing class.
 */
internal class PhaseAudioHost(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    val engine = NativeAudioEngine(context)
    @Volatile
    var isPrepared = false
        private set
    private var wantsPlayback = false
    private var resumeAfterTransientLoss = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                engine.setBusGain(null, DefaultAudioMix.MASTER_GAIN)
                if (wantsPlayback && resumeAfterTransientLoss) engine.start()
                resumeAfterTransientLoss = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeAfterTransientLoss = wantsPlayback
                engine.stop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                engine.setBusGain(null, DUCKED_MASTER_GAIN)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                wantsPlayback = false
                resumeAfterTransientLoss = false
                engine.stop()
            }
        }
    }
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val focusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(focusListener)
                .setWillPauseWhenDucked(false)
                .build()
        } else {
            null
        }

    /** Safe to call from a background dispatcher before [start]. */
    @Synchronized
    fun prepare() {
        engine.prepare()
        engine.registerPcm16(
            sampleId = SoundCatalog.TITLE_VOICE,
            sampleRate = AllophoneSpeechSynthesizer.SAMPLE_RATE,
            samples = AllophoneSpeechSynthesizer.renderPcm(
                VocalBark.BEAT_PHASER
            )
        )
        DefaultAudioMix.applyTo(engine)
        isPrepared = true
    }

    fun start() {
        wantsPlayback = true
        if (!isPrepared) return
        if (requestAudioFocus()) engine.start()
    }

    fun stop() {
        wantsPlayback = false
        resumeAfterTransientLoss = false
        engine.stop()
        abandonAudioFocus()
    }

    @Synchronized
    fun release() {
        stop()
        isPrepared = false
        engine.release()
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(requireNotNull(focusRequest))
        } else {
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private companion object {
        const val DUCKED_MASTER_GAIN = 0.2f
    }
}
