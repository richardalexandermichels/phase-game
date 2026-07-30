# Beat Phaser Audio Engine: Architecture and Status

## Status (July 30, 2026)

The low-latency audio migration is implemented. Beat Phaser now uses one
activity-owned native Oboe engine for gameplay, Design preview/audition,
backing tracks, and generated allophone speech. The former independent
`SoundPool`/`AudioTrack` paths are no longer the runtime architecture.

Automated unit tests, native ABI builds, debug APK assembly, lint, installation,
and startup checks pass. The remaining validation is subjective and long-form
device testing: listen for balance and timbre, exercise route changes and app
lifecycle transitions, and monitor latency, jitter, xruns, or phase drift during
multi-minute play.

For a fresh AI coding session, begin with
`AUDIO_SYSTEM_AI_HANDOFF.md`. It contains the latest end-to-end review,
verified asset/runtime facts, and prioritized known risks. In particular, it
documents the remaining phase-boundary Player-pitch mismatch, missing Android
audio-focus policy, envelope timing limitation under resampling, fatal startup
failure behavior, and native/conductor test gaps.

## Current Architecture

```text
Kotlin game rules + RhythmClock
        |
        | elapsed-realtime nanosecond AudioEvents
        v
NativeAudioEngine (Kotlin/JNI boundary)
        |
        v
bounded lock-free command queue
        |
        v
Oboe real-time callback
        +-- timestamp-to-frame scheduling
        +-- PCM sample playback and linear resampling
        +-- per-voice gain, pan, attack, and release
        +-- deterministic 48-voice allocation/stealing
        +-- Base, Player, Percussion, and Voice buses
        +-- master gain and soft limiting
        v
device output
```

Kotlin remains responsible for scoring, phase rules, pitch selection, patterns,
and the authoritative monotonic clock. Native code owns time-critical event
placement, voice playback, and mixing. Compose displays state and submits
player feedback, but does not sleep to time musical notes.

`GameAudioConductor` runs on a background coroutine and schedules Base and
backing events with rolling 100 ms look-ahead. The native engine drops events
that arrive more than 40 ms late instead of producing a stale-note burst.

## Implemented Capabilities

- Oboe 1.10.0 low-latency output with Android stream recovery.
- Preloaded mono PCM-16 WAV resources plus PCM registered directly from Kotlin.
- A 2,048-command lock-free queue and 2,048 scheduled-event capacity.
- A preallocated 48-voice mixer with priority-based deterministic stealing.
- Elapsed-realtime nanosecond scheduling and callback-frame placement.
- Linear pitch resampling, per-event gain/pan, and attack/release envelopes.
- Base, Player, Percussion, Voice, and master gain controls.
- Cancellable audio sessions for previews, navigation, and stale backing audio.
- Diagnostics for stream configuration, latency, xruns, active voices, dropped
  commands, and late events.
- One shared engine for gameplay, Design Mode, and title speech.
- Designed chords of up to four notes.
- Generated backing tracks that consume one mixer voice regardless of their
  authored instrument or chord count.

Fixed native capacities currently allow sample IDs `1..63`. IDs `41..63` are
reserved by the backing-track installer, for a maximum of 23 installed tier
WAVs across all cataloged tracks.

## Current Musical Behavior

### Base and Player tones

Built-in Base and unset Player notes use short, pre-generated triangle-wave
PCM assets with a clean PICO-8-like character. `tools/generate-sounds.ps1`
generates these assets through FFmpeg; the native engine does not synthesize an
oscillator at runtime.

`PopRockPhaseMelody` generates one complete melody for each active gameplay
phase. The phrase remains unchanged for all attempts/bars in that phase and is
regenerated only when gameplay advances to a new phase. Its rules use D-major
tones, functional pop-rock progressions such as I-V-vi-IV, chord tones on
strong positions, and compact passing motion.

Built-in Player feedback harmonizes the corresponding shifted Base note by a
diatonic third. If that pitch would duplicate the simultaneous Base note, it
switches to a diatonic fifth. Built-in Perfect, Good, and Close keep the chosen
pitch in key and use different asset levels; Miss uses a separate dissonant
square-wave asset.

Designed notes bypass random melody selection and use the explicit 12-row
D-major palette selected in Design Mode. Designed successful notes currently
use the same selected-pitch asset and neutral playback rate for Perfect, Good,
and Close; their judgment remains visible but does not select the built-in
judgment timbres.

### Backing tracks

Backing tracks are authored in the adjacent Rust project:

```text
C:\MyDocs\BeatPhaserTrackEditor
```

The editor supports unpitched step rows, pitched piano rolls from a single
declared source-WAV note, chords, per-instrument gain, multiple patterns, and
gameplay-tier assignment. At tier N, each instrument uses its highest pattern
assigned at or below N, allowing both additive layers and pattern replacement.

**Install in Game** renders the entire audible result of every tier offline as
one 48 kHz mono PCM-16 WAV. It copies those WAVs into Android resources, updates
`backing_tracks/installed_tracks.json`, and regenerates
`GeneratedBackingTrackCatalog.kt`. At runtime the conductor schedules only the
selected tier WAV at a loop boundary, so backing audio always occupies one
native voice.

Multiple tracks may be installed. `BackingTrackConfig.kt` contains the
high-level `ACTIVE_BACKING_TRACK_ID` switch. The active track's BPM and
steps-per-beat determine the shared gameplay step duration; its step count
determines backing-loop boundaries. See `AUDIO_ENGINE_DEVELOPER_GUIDE.md` for
the full authoring and installation workflow.

## Source Map

```text
app/src/main/java/com/rmichels/phasegame/
  MainActivity.kt                 Engine ownership and Player events
  GameAudioConductor.kt           Scheduled Base/backing authoring
  PopRockPhaseMelody.kt           Phase melody and Player harmony
  DesignScreen.kt                 Shared-engine audition and preview
  PitchPalette.kt                 Designed-note judgment pitch policy

app/src/main/java/com/rmichels/phasegame/audio/
  AudioEngine.kt                  Kotlin engine interface
  AudioEvent.kt                   Events, buses, sessions, diagnostics
  AudioMix.kt                     Central default gains
  NativeAudioEngine.kt            Resource loading and JNI calls
  SoundCatalog.kt                 Sample IDs and raw resources
  BackingTrackConfig.kt           Active track selection
  BackingTrackRuntime.kt          Tempo, tiers, and loop helpers
  GeneratedBackingTrackCatalog.kt Editor-generated track catalog

app/src/main/cpp/
  AudioEngine.h/.cpp              Queue, scheduler, voices, and mixer
  JniBridge.cpp                   Kotlin/native bridge
  CMakeLists.txt                  Native build and Oboe link

tools/
  generate-sounds.ps1             Musical/percussion WAV generation
```

## Important Invariants

- `RhythmClock` and `SystemClock.elapsedRealtime()` remain the authoritative
  game timeline.
- Do not move scoring, queue movement, or phase progression into C++.
- Do not allocate, lock, perform file I/O, log, or call JNI from the real-time
  audio callback.
- Prepare and register sample data before starting the stream.
- Use timestamped events for scheduled music and immediate events for tap
  feedback.
- Use sessions to cancel related pending/active sounds.
- Keep pattern logic generic for game lengths `3..16`.
- Every backing tier WAV is already a complete mix; never schedule its authored
  component instruments separately in the game.
- Treat `GeneratedBackingTrackCatalog.kt` as generated output and change the
  active track only through `BackingTrackConfig.kt`.

## Current Limitations

- WAV resource decoding accepts uncompressed integer PCM, mono, 16-bit files;
  there is no stereo or compressed-file decoder.
- All samples, including complete backing tier WAVs, are decoded into memory;
  there is no streaming player.
- The engine has no runtime oscillator, filter, reverb, delay, compressor, or
  pitch-independent time stretching.
- Canceling a session stops matching voices immediately rather than applying a
  smooth release.
- Diagnostics exist in code but have no dedicated developer UI.
- Hands-on long-duration and audio-route acceptance testing is still required.

## Validation

From `C:\MyDocs\PhaseGame`:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

On a connected device, verify title speech, built-in Base/Player melody,
Perfect/Good/Close/Miss feedback, designed chords, tier entry and reset,
repeated Design previews, background/resume, audio-route changes, and a
multi-minute phase-lock run.
