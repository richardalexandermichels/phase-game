package com.rmichels.phasegame.gameplay

internal const val MIN_PATTERN_STEPS = 3
internal const val MAX_PATTERN_STEPS = 16

/**
 * Returns the Reich-style phase of [baseRhythm] at [phaseIndex]. A positive
 * phase moves the material one step earlier in the repeating cycle.
 */
internal fun shiftedRhythm(
    baseRhythm: List<Boolean>,
    phaseIndex: Int
): List<Boolean> {
    require(baseRhythm.isNotEmpty())
    return List(baseRhythm.size) { index ->
        baseRhythm[Math.floorMod(index + phaseIndex, baseRhythm.size)]
    }
}

internal fun validateBaseRhythm(rhythm: List<Boolean>): List<Boolean> {
    require(rhythm.size in MIN_PATTERN_STEPS..MAX_PATTERN_STEPS) {
        "Base rhythm must contain $MIN_PATTERN_STEPS to $MAX_PATTERN_STEPS steps."
    }
    require(rhythm.any { it }) {
        "Base rhythm must contain at least one played step."
    }
    return rhythm
}
