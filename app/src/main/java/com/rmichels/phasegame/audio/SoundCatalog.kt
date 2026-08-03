package com.rmichels.phasegame.audio

import com.rmichels.phasegame.R
import com.rmichels.phasegame.design.DESIGN_PITCH_COUNT
import com.rmichels.phasegame.music.GameInstrument

internal object SoundCatalog {
    val BASE_FALLBACK = AudioSampleId(1)
    val PLAYER_PERFECT = AudioSampleId(2)
    val PLAYER_GOOD = AudioSampleId(3)
    val PLAYER_CLOSE = AudioSampleId(4)
    val PLAYER_MISS = AudioSampleId(5)
    val BASS_DRUM = AudioSampleId(6)
    val SNARE = AudioSampleId(7)
    val OPEN_HI_HAT = AudioSampleId(8)
    val CLOSED_HI_HAT = AudioSampleId(9)
    val TITLE_VOICE = AudioSampleId(40)

    val pianoBase = List(DESIGN_PITCH_COUNT) { index ->
        AudioSampleId(if (index < 12) 10 + index else 64 + index - 12)
    }
    val pianoPlayer = List(DESIGN_PITCH_COUNT) { index ->
        AudioSampleId(if (index < 12) 22 + index else 76 + index - 12)
    }
    val marimbaBase = List(DESIGN_PITCH_COUNT) { index ->
        AudioSampleId(88 + index)
    }
    val marimbaPlayer = List(DESIGN_PITCH_COUNT) { index ->
        AudioSampleId(112 + index)
    }
    val vibraphoneBase = List(DESIGN_PITCH_COUNT) { index ->
        AudioSampleId(136 + index)
    }
    val vibraphonePlayer = List(DESIGN_PITCH_COUNT) { index ->
        AudioSampleId(160 + index)
    }
    val flemishHarpsichordBase = List(DESIGN_PITCH_COUNT) { index ->
        AudioSampleId(184 + index)
    }
    val flemishHarpsichordPlayer = List(DESIGN_PITCH_COUNT) { index ->
        AudioSampleId(208 + index)
    }
    val distortedGuitarBase = List(DESIGN_PITCH_COUNT) { index ->
        AudioSampleId(232 + index)
    }
    val distortedGuitarPlayer = List(DESIGN_PITCH_COUNT) { index ->
        AudioSampleId(256 + index)
    }
    fun baseFor(instrument: GameInstrument): List<AudioSampleId> =
        when (instrument) {
            GameInstrument.PIANO -> pianoBase
            GameInstrument.MARIMBA -> marimbaBase
            GameInstrument.VIBRAPHONE -> vibraphoneBase
            GameInstrument.FLEMISH_HARPSICHORD ->
                flemishHarpsichordBase
            GameInstrument.DISTORTED_GUITAR -> distortedGuitarBase
        }

    fun playerFor(instrument: GameInstrument): List<AudioSampleId> =
        when (instrument) {
            GameInstrument.PIANO -> pianoPlayer
            GameInstrument.MARIMBA -> marimbaPlayer
            GameInstrument.VIBRAPHONE -> vibraphonePlayer
            GameInstrument.FLEMISH_HARPSICHORD ->
                flemishHarpsichordPlayer
            GameInstrument.DISTORTED_GUITAR -> distortedGuitarPlayer
        }

    val rawResources: List<Pair<AudioSampleId, Int>> = buildList {
        add(BASE_FALLBACK to R.raw.player_click)
        add(PLAYER_PERFECT to R.raw.player_perfect)
        add(PLAYER_GOOD to R.raw.player_good)
        add(PLAYER_CLOSE to R.raw.player_close)
        add(PLAYER_MISS to R.raw.player_miss)
        add(BASS_DRUM to R.raw.bass_drum)
        add(SNARE to R.raw.snare)
        add(OPEN_HI_HAT to R.raw.open_hi_hat)
        add(CLOSED_HI_HAT to R.raw.closed_hi_hat)
        val baseResources = intArrayOf(
            R.raw.design_base_00, R.raw.design_base_01,
            R.raw.design_base_02, R.raw.design_base_03,
            R.raw.design_base_04, R.raw.design_base_05,
            R.raw.design_base_06, R.raw.design_base_07,
            R.raw.design_base_08, R.raw.design_base_09,
            R.raw.design_base_10, R.raw.design_base_11,
            R.raw.design_base_12, R.raw.design_base_13,
            R.raw.design_base_14, R.raw.design_base_15,
            R.raw.design_base_16, R.raw.design_base_17,
            R.raw.design_base_18, R.raw.design_base_19,
            R.raw.design_base_20, R.raw.design_base_21,
            R.raw.design_base_22, R.raw.design_base_23
        )
        val playerResources = intArrayOf(
            R.raw.design_player_00, R.raw.design_player_01,
            R.raw.design_player_02, R.raw.design_player_03,
            R.raw.design_player_04, R.raw.design_player_05,
            R.raw.design_player_06, R.raw.design_player_07,
            R.raw.design_player_08, R.raw.design_player_09,
            R.raw.design_player_10, R.raw.design_player_11,
            R.raw.design_player_12, R.raw.design_player_13,
            R.raw.design_player_14, R.raw.design_player_15,
            R.raw.design_player_16, R.raw.design_player_17,
            R.raw.design_player_18, R.raw.design_player_19,
            R.raw.design_player_20, R.raw.design_player_21,
            R.raw.design_player_22, R.raw.design_player_23
        )
        val marimbaBaseResources = intArrayOf(
            R.raw.marimba_base_00, R.raw.marimba_base_01,
            R.raw.marimba_base_02, R.raw.marimba_base_03,
            R.raw.marimba_base_04, R.raw.marimba_base_05,
            R.raw.marimba_base_06, R.raw.marimba_base_07,
            R.raw.marimba_base_08, R.raw.marimba_base_09,
            R.raw.marimba_base_10, R.raw.marimba_base_11,
            R.raw.marimba_base_12, R.raw.marimba_base_13,
            R.raw.marimba_base_14, R.raw.marimba_base_15,
            R.raw.marimba_base_16, R.raw.marimba_base_17,
            R.raw.marimba_base_18, R.raw.marimba_base_19,
            R.raw.marimba_base_20, R.raw.marimba_base_21,
            R.raw.marimba_base_22, R.raw.marimba_base_23
        )
        val marimbaPlayerResources = intArrayOf(
            R.raw.marimba_player_00, R.raw.marimba_player_01,
            R.raw.marimba_player_02, R.raw.marimba_player_03,
            R.raw.marimba_player_04, R.raw.marimba_player_05,
            R.raw.marimba_player_06, R.raw.marimba_player_07,
            R.raw.marimba_player_08, R.raw.marimba_player_09,
            R.raw.marimba_player_10, R.raw.marimba_player_11,
            R.raw.marimba_player_12, R.raw.marimba_player_13,
            R.raw.marimba_player_14, R.raw.marimba_player_15,
            R.raw.marimba_player_16, R.raw.marimba_player_17,
            R.raw.marimba_player_18, R.raw.marimba_player_19,
            R.raw.marimba_player_20, R.raw.marimba_player_21,
            R.raw.marimba_player_22, R.raw.marimba_player_23
        )
        val vibraphoneBaseResources = intArrayOf(
            R.raw.vibraphone_base_00, R.raw.vibraphone_base_01,
            R.raw.vibraphone_base_02, R.raw.vibraphone_base_03,
            R.raw.vibraphone_base_04, R.raw.vibraphone_base_05,
            R.raw.vibraphone_base_06, R.raw.vibraphone_base_07,
            R.raw.vibraphone_base_08, R.raw.vibraphone_base_09,
            R.raw.vibraphone_base_10, R.raw.vibraphone_base_11,
            R.raw.vibraphone_base_12, R.raw.vibraphone_base_13,
            R.raw.vibraphone_base_14, R.raw.vibraphone_base_15,
            R.raw.vibraphone_base_16, R.raw.vibraphone_base_17,
            R.raw.vibraphone_base_18, R.raw.vibraphone_base_19,
            R.raw.vibraphone_base_20, R.raw.vibraphone_base_21,
            R.raw.vibraphone_base_22, R.raw.vibraphone_base_23
        )
        val vibraphonePlayerResources = intArrayOf(
            R.raw.vibraphone_player_00, R.raw.vibraphone_player_01,
            R.raw.vibraphone_player_02, R.raw.vibraphone_player_03,
            R.raw.vibraphone_player_04, R.raw.vibraphone_player_05,
            R.raw.vibraphone_player_06, R.raw.vibraphone_player_07,
            R.raw.vibraphone_player_08, R.raw.vibraphone_player_09,
            R.raw.vibraphone_player_10, R.raw.vibraphone_player_11,
            R.raw.vibraphone_player_12, R.raw.vibraphone_player_13,
            R.raw.vibraphone_player_14, R.raw.vibraphone_player_15,
            R.raw.vibraphone_player_16, R.raw.vibraphone_player_17,
            R.raw.vibraphone_player_18, R.raw.vibraphone_player_19,
            R.raw.vibraphone_player_20, R.raw.vibraphone_player_21,
            R.raw.vibraphone_player_22, R.raw.vibraphone_player_23
        )
        val flemishBaseResources = intArrayOf(
            R.raw.flemish_base_00, R.raw.flemish_base_01,
            R.raw.flemish_base_02, R.raw.flemish_base_03,
            R.raw.flemish_base_04, R.raw.flemish_base_05,
            R.raw.flemish_base_06, R.raw.flemish_base_07,
            R.raw.flemish_base_08, R.raw.flemish_base_09,
            R.raw.flemish_base_10, R.raw.flemish_base_11,
            R.raw.flemish_base_12, R.raw.flemish_base_13,
            R.raw.flemish_base_14, R.raw.flemish_base_15,
            R.raw.flemish_base_16, R.raw.flemish_base_17,
            R.raw.flemish_base_18, R.raw.flemish_base_19,
            R.raw.flemish_base_20, R.raw.flemish_base_21,
            R.raw.flemish_base_22, R.raw.flemish_base_23
        )
        val flemishPlayerResources = intArrayOf(
            R.raw.flemish_player_00, R.raw.flemish_player_01,
            R.raw.flemish_player_02, R.raw.flemish_player_03,
            R.raw.flemish_player_04, R.raw.flemish_player_05,
            R.raw.flemish_player_06, R.raw.flemish_player_07,
            R.raw.flemish_player_08, R.raw.flemish_player_09,
            R.raw.flemish_player_10, R.raw.flemish_player_11,
            R.raw.flemish_player_12, R.raw.flemish_player_13,
            R.raw.flemish_player_14, R.raw.flemish_player_15,
            R.raw.flemish_player_16, R.raw.flemish_player_17,
            R.raw.flemish_player_18, R.raw.flemish_player_19,
            R.raw.flemish_player_20, R.raw.flemish_player_21,
            R.raw.flemish_player_22, R.raw.flemish_player_23
        )
        val distortedGuitarBaseResources = intArrayOf(
            R.raw.distorted_guitar_base_00, R.raw.distorted_guitar_base_01,
            R.raw.distorted_guitar_base_02, R.raw.distorted_guitar_base_03,
            R.raw.distorted_guitar_base_04, R.raw.distorted_guitar_base_05,
            R.raw.distorted_guitar_base_06, R.raw.distorted_guitar_base_07,
            R.raw.distorted_guitar_base_08, R.raw.distorted_guitar_base_09,
            R.raw.distorted_guitar_base_10, R.raw.distorted_guitar_base_11,
            R.raw.distorted_guitar_base_12, R.raw.distorted_guitar_base_13,
            R.raw.distorted_guitar_base_14, R.raw.distorted_guitar_base_15,
            R.raw.distorted_guitar_base_16, R.raw.distorted_guitar_base_17,
            R.raw.distorted_guitar_base_18, R.raw.distorted_guitar_base_19,
            R.raw.distorted_guitar_base_20, R.raw.distorted_guitar_base_21,
            R.raw.distorted_guitar_base_22, R.raw.distorted_guitar_base_23
        )
        val distortedGuitarPlayerResources = intArrayOf(
            R.raw.distorted_guitar_player_00,
            R.raw.distorted_guitar_player_01,
            R.raw.distorted_guitar_player_02,
            R.raw.distorted_guitar_player_03,
            R.raw.distorted_guitar_player_04,
            R.raw.distorted_guitar_player_05,
            R.raw.distorted_guitar_player_06,
            R.raw.distorted_guitar_player_07,
            R.raw.distorted_guitar_player_08,
            R.raw.distorted_guitar_player_09,
            R.raw.distorted_guitar_player_10,
            R.raw.distorted_guitar_player_11,
            R.raw.distorted_guitar_player_12,
            R.raw.distorted_guitar_player_13,
            R.raw.distorted_guitar_player_14,
            R.raw.distorted_guitar_player_15,
            R.raw.distorted_guitar_player_16,
            R.raw.distorted_guitar_player_17,
            R.raw.distorted_guitar_player_18,
            R.raw.distorted_guitar_player_19,
            R.raw.distorted_guitar_player_20,
            R.raw.distorted_guitar_player_21,
            R.raw.distorted_guitar_player_22,
            R.raw.distorted_guitar_player_23
        )
        baseResources.forEachIndexed { index, resource ->
            add(pianoBase[index] to resource)
        }
        playerResources.forEachIndexed { index, resource ->
            add(pianoPlayer[index] to resource)
        }
        marimbaBaseResources.forEachIndexed { index, resource ->
            add(marimbaBase[index] to resource)
        }
        marimbaPlayerResources.forEachIndexed { index, resource ->
            add(marimbaPlayer[index] to resource)
        }
        vibraphoneBaseResources.forEachIndexed { index, resource ->
            add(vibraphoneBase[index] to resource)
        }
        vibraphonePlayerResources.forEachIndexed { index, resource ->
            add(vibraphonePlayer[index] to resource)
        }
        flemishBaseResources.forEachIndexed { index, resource ->
            add(flemishHarpsichordBase[index] to resource)
        }
        flemishPlayerResources.forEachIndexed { index, resource ->
            add(flemishHarpsichordPlayer[index] to resource)
        }
        distortedGuitarBaseResources.forEachIndexed { index, resource ->
            add(distortedGuitarBase[index] to resource)
        }
        distortedGuitarPlayerResources.forEachIndexed { index, resource ->
            add(distortedGuitarPlayer[index] to resource)
        }
        addAll(
            GeneratedBackingTrackCatalog.tracks
                .flatMap { it.tiers }
                .map { it.sampleId to it.rawResource }
        )
    }
}
