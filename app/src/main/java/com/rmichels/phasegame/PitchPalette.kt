package com.rmichels.phasegame

/**
 * Resource order must match the twelve pitch rows shown by DesignScreen.
 * Player samples are generated exactly one octave above their base counterparts.
 */
internal val DESIGN_BASE_SOUND_RESOURCES = intArrayOf(
    R.raw.design_base_00,
    R.raw.design_base_01,
    R.raw.design_base_02,
    R.raw.design_base_03,
    R.raw.design_base_04,
    R.raw.design_base_05,
    R.raw.design_base_06,
    R.raw.design_base_07,
    R.raw.design_base_08,
    R.raw.design_base_09,
    R.raw.design_base_10,
    R.raw.design_base_11
)

internal val DESIGN_PLAYER_SOUND_RESOURCES = intArrayOf(
    R.raw.design_player_00,
    R.raw.design_player_01,
    R.raw.design_player_02,
    R.raw.design_player_03,
    R.raw.design_player_04,
    R.raw.design_player_05,
    R.raw.design_player_06,
    R.raw.design_player_07,
    R.raw.design_player_08,
    R.raw.design_player_09,
    R.raw.design_player_10,
    R.raw.design_player_11
)

// Keep the selected pitch while applying the existing small accuracy detuning.
internal const val DESIGN_GOOD_PLAYBACK_RATE = 311f / 294f
internal const val DESIGN_CLOSE_PLAYBACK_RATE = 330f / 294f
