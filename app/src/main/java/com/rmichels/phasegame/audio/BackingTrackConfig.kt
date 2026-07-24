package com.rmichels.phasegame.audio

/**
 * High-level backing-track selection. Change this ID to switch among tracks
 * installed by the Rust editor, then deploy the game.
 */
internal const val ACTIVE_BACKING_TRACK_ID = "new_backing_track"

internal val activeBackingTrack: BackingTrackDefinition =
    GeneratedBackingTrackCatalog.tracks
        .firstOrNull { it.id == ACTIVE_BACKING_TRACK_ID }
        ?: GeneratedBackingTrackCatalog.tracks.first()
