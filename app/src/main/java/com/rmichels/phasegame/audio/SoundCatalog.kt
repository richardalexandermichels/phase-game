package com.rmichels.phasegame.audio

import com.rmichels.phasegame.R

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

    val designBase = List(12) { AudioSampleId(10 + it) }
    val designPlayer = List(12) { AudioSampleId(22 + it) }

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
            R.raw.design_base_10, R.raw.design_base_11
        )
        val playerResources = intArrayOf(
            R.raw.design_player_00, R.raw.design_player_01,
            R.raw.design_player_02, R.raw.design_player_03,
            R.raw.design_player_04, R.raw.design_player_05,
            R.raw.design_player_06, R.raw.design_player_07,
            R.raw.design_player_08, R.raw.design_player_09,
            R.raw.design_player_10, R.raw.design_player_11
        )
        baseResources.forEachIndexed { index, resource ->
            add(designBase[index] to resource)
        }
        playerResources.forEachIndexed { index, resource ->
            add(designPlayer[index] to resource)
        }
        addAll(
            GeneratedBackingTrackCatalog.tracks
                .flatMap { it.tiers }
                .map { it.sampleId to it.rawResource }
        )
    }
}
