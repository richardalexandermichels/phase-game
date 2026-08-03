# Beat Phaser Backing Track Editor

This is the game-side operational guide for the adjacent Rust sequencer:

```text
C:\MyDocs\BeatPhaserTrackEditor
```

Its purpose is to author layered/tiered backing arrangements and install them
into PhaseGame without manually converting WAVs or editing the generated sample
catalog.

## Start the Editor

Run the existing release build:

```text
C:\MyDocs\BeatPhaserTrackEditor\target\release\beat-phaser-track-editor.exe
```

To build or run it from source:

```powershell
cd C:\MyDocs\BeatPhaserTrackEditor
cargo run
cargo build --release
```

Editable projects are saved as versioned JSON files with the `.bpt` extension.
They retain paths to their source WAVs, so keep those source files available or
use **Export Track Pack** to make a portable backup.

## Authoring Model

A project defines its title, BPM, steps per beat, step count, instruments, and
patterns.

- An unpitched instrument presents one row of cells. Every enabled cell plays
  its WAV once without transposition; use this for drums and percussion.
- A pitched instrument presents a piano roll. Set **Source note** to the note
  actually sounding in its WAV, such as `C4`. Other notes use equal-tempered
  playback rates relative to that root, and a step may contain a chord.
- Gain is stored per instrument.
- Every instrument starts with one pattern, and additional patterns may be
  assigned to later gameplay tiers.
- An instrument may have only one pattern at a given tier.

At active tier N, each instrument plays its highest pattern assigned to a tier
less than or equal to N. This means patterns replace earlier patterns for the
same instrument, while separate instruments layer together.

Example:

```text
Kick pattern A: tier 1
Snare pattern A: tier 2
Kick pattern B: tier 3
```

Tier 2 contains Kick A + Snare A. Tier 3 contains Kick B + Snare A.

Use **Preview tier** and the transport to hear this exact selection behavior
before installation.

## Install Directly into the Game

1. Save the `.bpt` project.
2. Click **Game Folder...**.
3. Choose the PhaseGame root, normally `C:\MyDocs\PhaseGame`. The editor saves
   this location in `%APPDATA%\BeatPhaserTrackEditor\settings.json`.
4. Click **Install in Game**.
5. Rebuild or deploy PhaseGame. The arrangement is available in that build.

The equivalent command-line workflow is:

```powershell
C:\MyDocs\BeatPhaserTrackEditor\target\release\beat-phaser-track-editor.exe `
  --install <project.bpt> C:\MyDocs\PhaseGame
```

The installer validates the project and WAVs, converts pitched notes, mixes the
arrangement offline, and writes:

```text
PhaseGame/
  app/src/main/res/raw/
    backing_<track_id>_tier_01.wav
    backing_<track_id>_tier_02.wav
    ...
  app/src/main/java/com/rmichels/phasegame/audio/
    GeneratedBackingTrackCatalog.kt
  backing_tracks/
    installed_tracks.json
```

Every output tier is one complete 48 kHz mono signed PCM-16 loop. It already
contains all instruments and the correct replacement patterns for that tier.
Consequently, PhaseGame schedules one backing WAV and uses exactly one native
mixer voice, regardless of how many instruments or chords were authored.

Source WAVs may have other sample rates and may be integer or floating-point.
The editor downmixes multichannel input to mono and resamples during offline
rendering. Long samples wrap at the loop boundary rather than being cut off.

## Multiple Tracks and Selection

The editor derives a resource-safe track ID from the project title. Installing
a project with the same resulting ID replaces that track's prior tier files.
Installing a different title adds another catalog entry.

Choose the track the game uses in:

```text
app/src/main/java/com/rmichels/phasegame/audio/BackingTrackConfig.kt
```

```kotlin
internal const val ACTIVE_BACKING_TRACK_ID = "new_backing_track"
```

Copy a valid ID from `GeneratedBackingTrackCatalog.kt`. If the configured ID
does not exist, the game falls back to the first catalog entry.

The installer owns `GeneratedBackingTrackCatalog.kt` and
`installed_tracks.json`; do not edit either by hand. IDs `41..63` are available
for installed tier WAVs, allowing 23 tiers total across all installed tracks.

## Runtime Tier Behavior

- Gameplay begins at tier zero, with no backing WAV.
- Each clean completed bar increases the active tier by one, up to the selected
  track's maximum tier.
- A miss resets the active tier to zero and cancels future backing playback.
- The new tier takes effect at a backing-loop boundary.
- If a requested tier has no exact WAV, runtime chooses the highest installed
  tier not exceeding it.
- The active track's BPM and steps per beat determine the game's shared step
  duration. Its step count determines the backing-loop length.

## Portable Export

**Export Track Pack** creates a standalone folder for backup or transfer:

```text
<track_id>_track_pack/
  track_pack.json
  assets/                 normalized source WAVs
  INSTALL.md
  phasegame/res/raw/      rendered complete tier WAVs
```

This does not update the game catalog. Use **Install in Game** for normal game
integration.

## After Installation

Run the normal game checks from `C:\MyDocs\PhaseGame`:

```powershell
.\tools\gradle.ps1 testDebugUnitTest assembleDebug lintDebug
```

Then verify each tier in gameplay, confirm that a miss removes the backing
track, and listen across the loop boundary for clicks, timing errors, or an
unexpected pattern replacement.
