# Beat Phaser Audio System: AI Handoff

## Purpose

This is the first document a new AI coding session should read before changing
sound, timing, music, native code, or audio lifecycle behavior. It describes
the game-side implementation reviewed on July 30, 2026. Read the referenced
source before editing; generated backing-track files may change after an editor
install.

For task-oriented examples, continue with
`docs/AUDIO_ENGINE_DEVELOPER_GUIDE.md`. For the adjacent Rust authoring tool,
see `docs/BACKING_TRACK_EDITOR.md`.

## Review Snapshot

The game uses one `MainActivity`-owned `NativeAudioEngine` for title speech,
Design audition/preview, gameplay Base notes, immediate Player feedback, and
complete backing-track tier WAVs. Kotlin owns musical rules and submits
absolute elapsed-realtime timestamps. JNI transfers fixed event fields to a
native command queue. The Oboe callback owns sample-accurate onset placement,
resampling, envelopes, voice selection, bus mixing, and limiting.

The July 30 review verified:

- 32 JVM unit tests pass;
- `assembleDebug` and `lintDebug` pass;
- CMake builds the native engine for `arm64-v8a`, `armeabi-v7a`, `x86`, and
  `x86_64`;
- every packaged WAV is RIFF integer PCM, mono, 48 kHz, and 16-bit;
- each active backing tier is exactly 3 seconds, consistent with 12 steps at
  250 ms per step.

The review covered the Android/game repository. It verified the installed Rust
editor outputs and catalog contract, but did not review the editor's Rust
source repository.

## End-to-End Architecture

```text
RhythmClock / game rules / Design screen
                |
                | AudioEvent(targetElapsedRealtimeNanos, sample, bus,
                |            session, gain, pan, playback rate, envelope,
                |            priority)
                v
AudioEngine interface
                v
NativeAudioEngine
  - loads Android raw WAV resources
  - registers procedural title speech PCM
  - owns session IDs
  - maps Kotlin calls to JNI
                v
JniBridge.cpp
                v
bounded native command queue (2,048 commands)
                v
Oboe callback
  - scheduled-event store (2,048 events)
  - elapsed-realtime timestamp -> callback-frame offset
  - 48 preallocated voices
  - linear interpolation resampling
  - event gain/pan + bus gain + master gain
  - tanh soft limiter
                v
stereo float device output
```

Kotlin and C++ deliberately have different responsibilities:

- Kotlin: rhythm, scoring, phases, melody choice, backing tier, session
  ownership, and absolute event timestamps.
- C++: time-critical playback and mixing only.
- Compose: displays state and submits immediate finger-down feedback; it must
  not time musical notes with UI delays.

## Authoritative Clock

`RhythmClock` uses `SystemClock.elapsedRealtime()` in milliseconds.
`AudioEvent` uses `SystemClock.elapsedRealtimeNanos()`. Native
`AudioEngine::bootTimeNanos()` uses `CLOCK_BOOTTIME`, which is the matching
Android monotonic time origin.

`STEP_DURATION_MS` comes from the selected backing track:

```text
60,000 / BPM / stepsPerBeat, rounded to a Long millisecond value
```

The current generated track is 60 BPM with four steps per beat, so one step is
250 ms. Gameplay animation, judgment, Base scheduling, Design preview, and
backing boundaries all use that shared duration.

Do not introduce wall-clock time, a second metronome, or `delay()` followed by
`playImmediate()` for scheduled music.

## Playback Paths

### Engine construction and title voice

`MainActivity.onCreate()`:

1. constructs `NativeAudioEngine`;
2. calls `prepare()` to load every `SoundCatalog.rawResources` WAV;
3. renders `VocalBark.BEAT_PHASER` in Kotlin and registers it as PCM sample 40;
4. enqueues the default master/bus gains;
5. starts Oboe.

`TitleScreen` creates a Voice session, plays sample 40 immediately, and cancels
the session when leaving the screen. Separate left/Base and right/Player
instrument selections are `rememberSaveable` app state and are passed into both
gameplay and Design preview. They select sample banks only; they do not alter
rhythm, harmony, progressions, modulation, or note generation.

The gameplay screen has a small top-right menu whose only action returns to the
title. Leaving gameplay disposes its Base, Percussion, and Player sessions.
Starting again creates a new `PhaseGameScreen`, so clock, queue, phase melody,
score, and other remembered gameplay state begin a new cycle.

### Scheduled gameplay Base music

`GameAudioConductor` runs on `Dispatchers.Default` with 100 ms look-ahead. It
derives absolute step timestamps from `RhythmClock.startTimeMs` and schedules:

- a Base sound on each `true` Base step;
- a complete backing-tier WAV only at a backing-loop boundary.

Built-in Base notes use the generated arrangement's left-hand sample from the
selected 24-note Base bank. Designed Base chords use the same independently
selected bank and schedule one sample per pitch at the same timestamp.

The first generated phase chooses an independent random key and mode. Each
later phase uses a scored related-key modulation. Its first chord shares at
least one pivot pitch class with the preceding final chord, and both hand
phrases seed their voice-leading from the notes where the prior phase ended.

If the conductor falls more than one step behind, it moves its authoring cursor
to the current step rather than emitting a stale burst. Native code separately
drops events more than 40 ms late.

### Immediate Player feedback

`TapArea` reports finger-down. `MainActivity` judges the compensated tap and
calls `playImmediate()`, which is still an `AudioEvent` timestamped with the
current elapsed-realtime nanoseconds.

- Built-in Perfect/Good/Close use the generated arrangement's right-hand sample
  from the independently selected 24-note Player bank; Good and Close reduce
  event gain.
- Miss uses sample 5 at playback rate 1.
- Designed successful chords use the selected `designPlayer` samples.
- Player feedback uses priority 3, the highest current convention.

Designed successful notes keep the same selected samples for Perfect, Good, and
Close. Good and Close reduce event gain rather than changing pitch or timbre.

### Design Mode

Pitch audition is immediate and belongs to one Design-screen audition session.
**Preview Bar** cancels the prior preview session, creates a new one, and
schedules a complete bar 30 ms in the future. Chord notes share a timestamp.
Leaving Design cancels both audition and preview sessions.

### Backing tracks

`GeneratedBackingTrackCatalog.kt` is generated output. The selected track is
chosen in `BackingTrackConfig.kt`; an invalid ID falls back to the first
generated track. The current track has sample IDs 41 through 44 for tiers 1
through 4.

Each tier WAV is already the editor's complete offline mix. Runtime schedules
one WAV and therefore consumes one native voice, regardless of the number of
authored instruments. Tier zero is silence. Each clean logical bar raises the
tier, capped at the catalog maximum. A miss resets the tier and rotates the
Percussion session so pending and active backing audio is canceled.

The backing loop is 12 steps while a gameplay bar may contain any supported
3..16 steps. These are intentionally separate cycles.

## Sessions, Buses, and Priorities

Sessions are unique ownership/cancellation tokens. Never reuse a canceled
session ID.

| Owner | Session behavior |
|---|---|
| Title voice | One session per title composition |
| Gameplay Base | One session for the gameplay timeline |
| Gameplay Percussion | Replaced on miss to cancel backing audio |
| Gameplay Player | One session for immediate feedback |
| Design audition | One session for screen lifetime |
| Design preview | New session for each preview |

Bus ordinals cross JNI directly and must remain synchronized with the native
four-element gain array:

```text
0 BASE
1 PLAYER
2 PERCUSSION
3 VOICE
```

Append new buses; do not reorder existing values without changing C++ and
tests. Default gains live only in `AudioMix.kt`.

Current priority convention:

```text
0 backing/percussion/default
1 gameplay Base and title voice
2 Design audition/preview
3 Player feedback
```

Priority affects voice stealing, not volume.

## Native Engine Contract

Fixed capacities in `AudioEngine.h`:

```text
samples             320 slots; valid IDs are 1..319
voices              48
scheduled events    2,048
command queue       2,048
canceled sessions   256 remembered IDs
```

The WAV loader accepts only uncompressed integer PCM, mono, 16-bit WAVs. It
parses RIFF chunks and converts samples to native float storage before the
stream starts. Procedural PCM registration also copies and converts before
playback. All sample data remains resident in memory; there is no streaming.

The stream requests low-latency, exclusive, Game/Sonification, stereo float
output and uses a buffer of two bursts. Requested modes are preferences; query
`diagnostics()` for the mode actually granted by a device.

The callback must remain allocation-free, lock-free, free of file I/O, free of
logging/JNI, and bounded. Producers communicate only through the command queue.

## Catalog and Asset Ownership

`SoundCatalog` currently assigns:

```text
1       Base fallback
2..5    Player Perfect/Good/Close/Miss
6..9    bass drum, snare, open hat, closed hat
10..21  designed Base C2..B2
22..33  designed Player C4..B4
40      procedural title voice
41..63  generated backing tiers (installer-owned range)
64..75  designed Base C3..B3
76..87  designed Player C5..B5
88..111 Marimba Base C2..B3
112..135 Marimba Player C4..B5
136..159 Vibraphone Base C2..B3
160..183 Vibraphone Player C4..B5
184..207 Flemish Harpsichord Base C2..B3
208..231 Flemish Harpsichord Player C4..B5
232..255 Distorted Guitar Base C2..B3
256..279 Distorted Guitar Player C4..B5
```

The individual drum samples 6..9 are loaded but the current conductor does not
schedule them; current percussion comes from complete backing-tier WAVs.
`res/raw/vocal_layer.wav` is present but is not registered in `SoundCatalog` and
is not a current runtime path.

The pitched assets in IDs 1..4, 10..33, and 64..87 are rendered from the CC0
VCSL Steinway B pack. IDs 88..135 are VCSL Marimba, IDs 136..183 are VCSL
hard-mallet Vibraphone, and IDs 184..231 are VCSL Flemish Harpsichord 8′.
IDs 232..279 are FreePats Distorted Electric Guitar #1.
`tools/import-pitched-samples.ps1` and the checked-in manifests own those
reproducible transformations; see
`docs/PITCHED_SAMPLE_IMPORTER.md` and `docs/THIRD_PARTY_AUDIO.md`.
`tools/generate-sounds.ps1` is the synthetic Piano alternative and overwrites
the Piano filenames if run, so rerun the Steinway importer last when the
recorded Piano bank should remain active.

## Lifecycle and Route Behavior

`MainActivity` owns exactly one engine:

```text
onCreate   prepare/register/start
onStart    start if stopped
onStop     stop and close the native stream
onDestroy  destroy the native engine
```

`PhaseGameScreen` pauses/resumes `RhythmClock` with lifecycle state and starts
or stops the conductor accordingly. Native `stop()` clears scheduled events,
voices, canceled-session history, and queued commands. After resume, the
conductor starts a new authoring cursor and skips elapsed steps.

Oboe's `onErrorAfterClose()` attempts to reopen the stream after a route/device
error. There is no app-level AudioFocus integration, noisy-route receiver, or
Bluetooth-output latency calibration.

## Review Findings and Known Risks

### High: cross-boundary tap feedback can use the old phase's pitch

Scoring now correctly predicts the upcoming queued rhythm for an early tap near
a phase boundary. Player sound selection later in `handlePlayerPress`, however,
still derives `currentPhase` from `patternQueue.firstOrNull()`. Before the clock
crosses the boundary, an early successful hit belonging to the next queued bar
can therefore use the current phase for:

- `gameDesign.playerNotes(...)`;
- `PopRockPhaseMelody.playerPitchIndexForStep(...)`.

Judgment and queue credit are correct; only feedback pitch/design lookup can be
from the wrong phase. A future fix should resolve the phase associated with
`nearestExpectedHit.absoluteBarIndex`, using the same prediction rules as
`rhythmForJudgmentBar`, and add boundary tests before changing playback.

### Medium: no Android audio-focus policy

The engine does not request or abandon audio focus and does not implement duck,
pause, or resume behavior for competing media, calls, or navigation prompts.
Lifecycle stopping is not a substitute for audio focus.

### Medium: event envelopes are not duration-correct under resampling

Native `attackFrames` and `releaseFrames` are calculated in output-rate frames,
but compared against `sourcePosition`, which advances in source frames scaled
by playback rate. At a playback rate other than 1, requested envelope
milliseconds do not remain exact wall-clock durations. Current call sites leave
both values at zero, so this is latent rather than an active gameplay defect.

### Medium: startup failure is fatal rather than recoverable

Malformed/missing catalog assets, PCM registration failure, or inability to
start the native stream throws through `check(...)` during `MainActivity`
creation. There is no silent-mode fallback or user-facing audio error state.

### Medium: native scheduling and conductor behavior lack direct tests

The JVM suite covers timing, phasing, melody rules, tier selection, and loop
boundaries. It does not directly test `GameAudioConductor` with a fake engine,
JNI field mapping, command-queue saturation, voice stealing, cancellation,
late-event dropping, route recovery, or mixer output. Native behavior is
currently validated by compilation and device smoke testing.

### Low: cancellation can be dropped under queue exhaustion

`cancelSession()` returns `Unit`. If the bounded command queue is full, the
native cancellation command increments `droppedCommandCount`, but Kotlin cannot
observe that specific failure. This requires extreme producer pressure under
the current capacities.

## Safe Change Rules for a New AI Session

1. Read this document and the exact source involved.
2. Preserve `RhythmClock`/elapsed-realtime as the only music timeline.
3. Keep game rules out of C++ and real-time work out of Compose.
4. Preload/decode/synthesize before starting the stream.
5. Schedule aligned events early with absolute timestamps.
6. Use a new session for each replaceable logical owner.
7. Preserve bus ordinal mapping and sample-ID uniqueness.
8. Treat generated backing files as editor-owned.
9. Do not schedule individual editor instruments in Android; schedule the
   complete tier WAV.
10. Add pure Kotlin tests where possible, then run native ABI builds and test on
    the Pixel 8a.

## Validation After Audio Changes

From `C:\MyDocs\PhaseGame`:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

Device checks should include:

- title speech and repeated title navigation;
- the first gameplay downbeat after countdown;
- Base/Player alignment over several minutes;
- Perfect/Good/Close/Miss feedback;
- the last note of one phase followed immediately by the first note of the next;
- designed Base and Player chords;
- Design audition and repeated Preview Bar cancellation;
- clean-bar tier entry and miss reset;
- background/resume;
- speaker, wired/USB, and Bluetooth routes;
- interruptions from another media app or a notification/call;
- diagnostics before and after route changes, watching xruns, dropped commands,
  and late events.

## Primary Source Map

```text
MainActivity.kt
  engine lifecycle, sessions, countdown, judgment, Player events

GameAudioConductor.kt
  rolling Base/backing scheduling and Percussion-session rotation

PopRockPhaseMelody.kt
  synchronized two-hand fallback arrangement generation

PianoPitch.kt
  hand ranges, note labels, and Design scale presets

GameInstrument.kt
  title-screen pitched-instrument choices

DesignScreen.kt
  audition and timestamped bar preview

audio/AudioEngine.kt
  app-facing engine contract and playImmediate helper

audio/AudioEvent.kt
  event, sample/session IDs, buses, diagnostics

audio/NativeAudioEngine.kt
  raw-resource loading, PCM registration, lifecycle, JNI calls

audio/SoundCatalog.kt
  stable sample IDs and Android resources

audio/AudioMix.kt
  centralized master and bus gains

audio/BackingTrackConfig.kt
  selected generated track ID

audio/BackingTrackRuntime.kt
  step duration, tier selection, loop-boundary helpers

audio/GeneratedBackingTrackCatalog.kt
  editor-generated metadata and tier resources; do not hand-edit

cpp/AudioEngine.h and AudioEngine.cpp
  Oboe stream, command queue, scheduler, voices, mixer, diagnostics

cpp/JniBridge.cpp
  Kotlin/native field mapping

AllophoneSpeechSynthesizer.kt and VocalBarks.kt
  offline procedural speech PCM rendered before stream start
```
