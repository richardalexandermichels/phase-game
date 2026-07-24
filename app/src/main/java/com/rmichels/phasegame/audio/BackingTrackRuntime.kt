package com.rmichels.phasegame.audio

import kotlin.math.roundToLong

internal val activeBackingTrackStepDurationMs: Long
    get() = (
        60_000f /
            activeBackingTrack.bpm /
            activeBackingTrack.stepsPerBeat
        ).roundToLong()

internal val activeBackingTrackMaxTier: Int
    get() = activeBackingTrack.tiers.maxOfOrNull { it.tier } ?: 0

internal fun BackingTrackDefinition.sampleForTier(
    activeTier: Int
): BackingTrackTier? =
    tiers
        .filter { it.tier <= activeTier }
        .maxByOrNull { it.tier }

internal fun BackingTrackDefinition.isLoopBoundary(
    absoluteStep: Long
): Boolean =
    stepCount > 0 &&
        Math.floorMod(absoluteStep, stepCount.toLong()) == 0L
