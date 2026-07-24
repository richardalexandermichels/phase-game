# Beat Phaser: Low-Latency Audio Engine Project Brief

## Implementation Status (July 24, 2026)

Implemented in the current worktree:

- One activity-owned Oboe 1.10.0 engine replaces both `SoundPool` instances and
  the title `AudioTrack`.
- All short WAVs and rendered allophone PCM are registered before playback.
- A bounded lock-free command queue feeds a preallocated 48-voice mixer.
- Events use elapsed-realtime nanosecond timestamps; callback block timing maps
  them to frame offsets and rejects events more than 40 ms late.
- Linear interpolation, gain/pan, optional attack/release, deterministic voice
  stealing, sessions, bus/master gains, diagnostics, and stream recovery exist.
- `GameAudioConductor` schedules base and percussion independently of Compose.
- Gameplay, Design preview/audition, and voice playback share the catalog.
- The design model supports ordered chords of up to four pitches, with a
  versioned saver that also restores legacy monophonic state.

Unit, native ABI, APK, and lint builds pass. Pixel 8a hands-on latency, jitter,
underrun, route-change, and multi-minute phase-lock acceptance testing remains
required before calling the migration production-validated.

## Objective

Replace the two independent `SoundPool` implementations with one dedicated
low-latency audio engine that treats the rhythm clock, sound scheduling, mixing,
pitch, envelopes, and polyphony as a coherent subsystem. Preserve current game
feel and sounds first; enable reliable chords and richer synthesis second.

This is a substantial architectural project, not a small SoundPool refactor.

## Why

Current limitations:

- `SoundPool.play()` is requested from coroutine/UI-side scheduling rather than
  sample-accurately scheduled inside an audio callback.
- Gameplay is limited to 8 simultaneous streams; base, percussion, player
  notes, releases, and future chords can cause voice stealing or dropped notes.
- Gameplay and Design preview own separate pools and loading logic.
- Timing uses a strong monotonic master clock, but audio onset is still subject
  to thread wakeup and SoundPool latency/jitter.
- Pitch quality depends on SoundPool playback-rate resampling.
- Musical state, sample IDs, judgment timbres, and mixing are spread through
  Compose code.

## Recommended Direction

Use a native Android audio callback engine through Oboe, with AAudio where the
device supports it and the appropriate fallback on older supported devices.
Keep Kotlin responsible for game rules and UI; keep real-time rendering,
scheduling, voice allocation, and mixing in the audio engine.

High-level boundary:

```text
Kotlin game/master clock
  -> timestamped AudioEvent commands
  -> lock-free/native command queue
  -> real-time audio callback
  -> voice mixer/envelopes/resampling
  -> device output
```

Do not move scoring or visual queue rules into native code.

## Required Engine Capabilities

- Low-latency output configured for game/interactive use.
- One audio frame timeline related explicitly to the existing monotonic
  `RhythmClock`.
- Schedule events at exact future audio-frame positions rather than sleeping
  until each note.
- Preload/decode all short PCM assets before play; no allocation, file I/O,
  locks, logging, or JNI calls from the real-time callback.
- Polyphonic voice pool with deterministic allocation/stealing and a limit
  comfortably above maximum percussion + base + player chords + release tails.
- Per-voice:
  - sample/synth source
  - gain and optional pan
  - pitch/resampling ratio
  - attack/release envelope
  - start frame and stop/release behavior
- Buses for base, player, percussion, voice barks, and master gain.
- Centralized pause/start/dispose and Android lifecycle handling.
- Report output latency/configuration for calibration and diagnostics.
- Reuse the same engine for gameplay and Design Mode previews.

## Preserve Existing Behavior

- `STEP_DURATION_MS = 250`.
- Existing `RhythmClock` remains the authoritative gameplay timekeeper.
- Current 10 ms input compensation and Perfect/Good/Close/Miss windows initially
  remain unchanged; recalibrate only after measured on-device testing.
- Current WAV timbres, pitch palette, pop-motif generation, accuracy detuning,
  percussion ordering, and allophone playback must continue to work during the
  migration.
- Pattern lengths remain generic from 2 through 16.
- A UI stall must not produce a burst of stale notes.
- Player input feedback should begin as soon as practical while scoring still
  uses the compensated master-clock timestamp.

## Suggested Migration Stages

1. **Define Kotlin API and event model**
   - `AudioEngine.start/stop/release`
   - load/register samples
   - schedule/cancel timestamped notes
   - immediate player-feedback event
   - bus/master gain controls

2. **Native callback and clock mapping**
   - establish stable conversion between elapsed realtime and audio frames
   - build lock-free command ingestion
   - add underrun/timing diagnostics outside the callback

3. **PCM sample mixer**
   - reproduce existing SoundPool output with mono short WAV assets
   - implement interpolation/resampling and envelopes
   - verify overlapping base/percussion/player voices

4. **Migrate gameplay**
   - extract audio scheduling from `PhaseGameScreen`
   - schedule ahead by step/bar using the master timeline
   - keep scoring and Compose state in Kotlin
   - remove gameplay SoundPool only after behavior matches

5. **Migrate Design preview**
   - remove its separate SoundPool
   - use the shared palette and engine

6. **Polyphony/chords**
   - change designed notes from `Int?` to an explicit chord-capable model such
     as an ordered immutable set/list of pitches
   - update saver, editor gestures, playback, and regression tests
   - define maximum chord size and voice-stealing policy

7. **Optional synthesis evolution**
   - generate triangle/sine/noise sources in-engine
   - reduce pre-generated pitch assets
   - migrate allophone rendering if useful, without risking gameplay callback

## Acceptance Criteria

- No audible regression in existing monophonic gameplay.
- Base, layers, and scheduled player/design notes stay phase-locked during a
  multi-minute run.
- No stale-note bursts after deliberate UI/main-thread stalls.
- No dropped voices at the defined maximum chord/layer load.
- Design preview and gameplay use the same engine and sound definitions.
- Start/stop/re-enter gameplay repeatedly without leaks, crashes, or duplicate
  audio.
- Tests cover timestamp-to-frame conversion, event ordering, cancellation,
  voice limits/stealing, arbitrary 2..16 step patterns, and lifecycle resets.
- Pixel 8a hands-on testing records output latency, onset jitter, and underruns
  before and after migration.

## Likely Files/Structure

```text
app/src/main/java/com/rmichels/phasegame/audio/
  AudioEngine.kt
  AudioEvent.kt
  SoundCatalog.kt

app/src/main/cpp/
  AudioEngine.cpp/.h
  Voice.cpp/.h
  Sample.cpp/.h
  JniBridge.cpp

app/src/main/cpp/CMakeLists.txt
```

Gradle will need Android native/CMake configuration and the selected Oboe
dependency/integration. Avoid committing to a library version without checking
the current official Android guidance at implementation time.
