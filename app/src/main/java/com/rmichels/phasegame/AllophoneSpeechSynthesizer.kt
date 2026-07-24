package com.rmichels.phasegame

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Generic renderer for data-driven [VocalBarkDefinition] arrangements.
 */
internal object AllophoneSpeechSynthesizer {
    private const val SAMPLE_RATE = 48_000

    fun playFromStart(track: AudioTrack) {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.pause()
        }
        track.setPlaybackHeadPosition(0)
        track.play()
    }

    fun createTrack(bark: VocalBark): AudioTrack {
        val pcm = render(VocalBarkCatalog.definitionFor(bark))
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
            .build()
            .also { track -> track.write(pcm, 0, pcm.size) }
    }

    private fun render(bark: VocalBarkDefinition): ShortArray {
        val output = ArrayList<Double>()
        val random = Random(0xBEE7)
        var voicePhase = 0.0

        bark.sequence.forEach { event ->
            val durationMs =
                ((60_000.0 / bark.tempoBpm) * event.beats / bark.speed)
                    .roundToInt()
                    .coerceAtLeast(1)
            val sampleCount = durationMs * SAMPLE_RATE / 1_000
            val pitchHz =
                bark.basePitchHz * 2.0.pow(event.pitchSemitones / 12.0)
            val gain = bark.emphasis * event.emphasis

            val samples = when (val model = event.sound.model) {
                is AllophoneModel.Voiced ->
                    renderVoiced(model, sampleCount, voicePhase, pitchHz)
                is AllophoneModel.Fricative ->
                    renderFricative(model.kind, sampleCount, random, voicePhase, pitchHz)
                is AllophoneModel.Stop ->
                    renderStop(model.kind, sampleCount, random, voicePhase, pitchHz)
                is AllophoneModel.Nasal ->
                    renderNasal(model.formants, sampleCount, voicePhase, pitchHz)
                is AllophoneModel.Pause -> DoubleArray(sampleCount)
            }

            samples.forEach { output += it * gain }
            voicePhase += sampleCount * pitchHz / SAMPLE_RATE
        }

        return limitPhrase(output)
    }

    private fun renderVoiced(
        model: AllophoneModel.Voiced,
        sampleCount: Int,
        startingPhase: Double,
        pitchHz: Double
    ): DoubleArray {
        val output = DoubleArray(sampleCount)
        val resonators = Array(3) { Resonator() }

        for (index in output.indices) {
            val progress = index.toDouble() / max(1, sampleCount - 1)
            val formants = doubleArrayOf(
                interpolate(model.from.firstHz, model.to.firstHz, progress),
                interpolate(model.from.secondHz, model.to.secondHz, progress),
                interpolate(model.from.thirdHz, model.to.thirdHz, progress)
            )
            val phase = 2.0 * PI * (startingPhase + index * pitchHz / SAMPLE_RATE)
            val source =
                0.72 * sin(phase) +
                    0.20 * sin(phase * 2.0) +
                    0.08 * sin(phase * 3.0)
            val filtered =
                resonators[0].process(source, formants[0], 90.0) * 0.70 +
                    resonators[1].process(source, formants[1], 120.0) * 0.22 +
                    resonators[2].process(source, formants[2], 150.0) * 0.08
            output[index] = filtered * envelope(index, sampleCount, 7)
        }
        return output
    }

    private fun renderNasal(
        formants: SpeechFormants,
        sampleCount: Int,
        startingPhase: Double,
        pitchHz: Double
    ): DoubleArray {
        val voiced = renderVoiced(
            AllophoneModel.Voiced(formants),
            sampleCount,
            startingPhase,
            pitchHz
        )
        val nasalFilter = OnePoleLowPass(1_700.0)
        return DoubleArray(sampleCount) { index ->
            nasalFilter.process(voiced[index]) * 0.72
        }
    }

    private fun renderFricative(
        kind: FricativeKind,
        sampleCount: Int,
        random: Random,
        startingPhase: Double,
        pitchHz: Double
    ): DoubleArray {
        val output = DoubleArray(sampleCount)
        val lowerHz = when (kind) {
            FricativeKind.F -> 700.0
            FricativeKind.S -> 2_600.0
            FricativeKind.Z -> 1_800.0
        }
        val upperHz = when (kind) {
            FricativeKind.F -> 3_200.0
            FricativeKind.S -> 5_800.0
            FricativeKind.Z -> 4_500.0
        }
        val lowerFilter = OnePoleLowPass(lowerHz)
        val upperFilter = OnePoleLowPass(upperHz)

        for (index in output.indices) {
            val white = random.nextDouble(-1.0, 1.0)
            val noise = upperFilter.process(white) - lowerFilter.process(white)
            val voice =
                sin(2.0 * PI * (startingPhase + index * pitchHz / SAMPLE_RATE))
            val consonant = when (kind) {
                FricativeKind.F -> noise * 0.11
                FricativeKind.S -> noise * 0.13
                FricativeKind.Z -> voice * 0.23 + noise * 0.045
            }
            output[index] = consonant * envelope(index, sampleCount, 16)
        }
        return output
    }

    private fun renderStop(
        kind: StopKind,
        sampleCount: Int,
        random: Random,
        startingPhase: Double,
        pitchHz: Double
    ): DoubleArray {
        val output = DoubleArray(sampleCount)
        val voiced = kind == StopKind.B
        val burstMs = when (kind) {
            StopKind.B, StopKind.P -> 5
            StopKind.K, StopKind.T -> 9
        }
        val burstSamples = burstMs * SAMPLE_RATE / 1_000
        val closureEnd = sampleCount - burstSamples
        val lowerFilter = OnePoleLowPass(
            if (kind == StopKind.K) 1_700.0 else 1_200.0
        )
        val upperFilter = OnePoleLowPass(
            when (kind) {
                StopKind.B, StopKind.P -> 3_200.0
                StopKind.K -> 4_200.0
                StopKind.T -> 5_500.0
            }
        )

        for (index in output.indices) {
            output[index] = when {
                index < closureEnd && voiced ->
                    sin(
                        2.0 * PI *
                            (startingPhase + index * pitchHz / SAMPLE_RATE)
                    ) * 0.07 * envelope(index, sampleCount, 5)
                index < closureEnd -> 0.0
                else -> {
                    val progress =
                        (index - closureEnd).toDouble() / max(1, burstSamples)
                    val white = random.nextDouble(-1.0, 1.0)
                    val burst =
                        upperFilter.process(white) - lowerFilter.process(white)
                    val strength = when (kind) {
                        StopKind.B -> 0.10
                        StopKind.P -> 0.12
                        StopKind.K -> 0.14
                        StopKind.T -> 0.16
                    }
                    burst * exp(-9.0 * progress) * strength
                }
            }
        }
        return output
    }

    private fun envelope(index: Int, sampleCount: Int, edgeMs: Int): Double {
        val edgeSamples = max(1, edgeMs * SAMPLE_RATE / 1_000)
        val attack = (index.toDouble() / edgeSamples).coerceIn(0.0, 1.0)
        val release =
            ((sampleCount - 1 - index).toDouble() / edgeSamples).coerceIn(0.0, 1.0)
        return minOf(attack, release)
    }

    private fun limitPhrase(samples: List<Double>): ShortArray {
        if (samples.isEmpty()) return ShortArray(0)

        val softened = DoubleArray(samples.size) { index ->
            tanh(samples[index] * 1.25)
        }
        val peak = softened.maxOf { kotlin.math.abs(it) }
        val gain = if (peak > 0.0) 0.82 / peak else 1.0

        return ShortArray(softened.size) { index ->
            (softened[index] * gain * Short.MAX_VALUE)
                .coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                .toInt()
                .toShort()
        }
    }

    private fun interpolate(from: Double, to: Double, progress: Double): Double =
        from + (to - from) * progress

    private class Resonator {
        private var previous = 0.0
        private var previousPrevious = 0.0

        fun process(input: Double, frequencyHz: Double, bandwidthHz: Double): Double {
            val radius = exp(-PI * bandwidthHz / SAMPLE_RATE)
            val coefficient = 2.0 * radius * cos(2.0 * PI * frequencyHz / SAMPLE_RATE)
            val output =
                (1.0 - radius) * input +
                    coefficient * previous -
                    radius * radius * previousPrevious
            previousPrevious = previous
            previous = output
            return output
        }
    }

    private class OnePoleLowPass(cutoffHz: Double) {
        private val amount = 1.0 - exp(-2.0 * PI * cutoffHz / SAMPLE_RATE)
        private var value = 0.0

        fun process(input: Double): Double {
            value += amount * (input - value)
            return value
        }
    }
}
