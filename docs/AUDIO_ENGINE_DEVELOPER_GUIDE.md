# Beat Phaser Audio Engine: Developer Guide

This guide explains the current audio system, how to create or import sounds,
and how to use the engine safely from Kotlin. It describes the implementation
after the Oboe migration.

## Quick Answers

### Do generated tones still need the external script?

Yes, for the current musical tone assets.

The engine plays preloaded PCM samples. It can change their playback rate,
gain, pan, and envelope, but it does not currently contain a runtime sine,
triangle, square, or noise oscillator. `tools/generate-sounds.ps1` remains the
authoring tool for the generated tone and percussion WAV files.

The script is not required when:

- replacing a sound with a WAV exported by a DAW or audio editor;
- adding a recorded/custom WAV;
- registering PCM created in Kotlin, as the allophone voice renderer does.

The script invokes FFmpeg and currently has a machine-specific `$FfmpegPath`.
Update that path before running it on another development machine.

### Can the engine play custom WAV files?

Yes. A compatible WAV can be placed in `app/src/main/res/raw`, assigned an
`AudioSampleId` in `SoundCatalog`, and scheduled through `AudioEngine`.

The native WAV reader currently accepts:

- RIFF/WAVE;
- uncompressed integer PCM (`format = 1`);
- mono;
- 16-bit samples;
- any positive sample rate.

The engine resamples to the device output rate. Prefer 48 kHz mono PCM-16 for
predictable quality and because Android low-latency devices commonly use
48 kHz.

It does not currently decode stereo WAV, float WAV, MP3, AAC, Ogg, or other
compressed formats.

## Architecture

```text
Kotlin game rules and RhythmClock
        |
        | elapsed-realtime nanosecond AudioEvents
        v
AudioEngine / NativeAudioEngine
        |
        | JNI command
        v
bounded lock-free native queue
        |
        v
Oboe real-time callback
        |
        +-- timestamp-to-frame placement
        +-- 48-voice allocation
        +-- sample interpolation/resampling
        +-- attack/release, gain, and pan
        +-- Base/Player/Percussion/Voice buses
        +-- master gain and soft limiting
        v
device output
```

Responsibilities are intentionally divided:

- Kotlin owns gameplay, scoring, patterns, phase, pitch choices, and the master
  clock.
- `GameAudioConductor` authors future Base and Percussion events on a
  background coroutine.
- Native code owns callback timing, voices, sample playback, and mixing.
- Compose displays state but does not time the musical notes.

## Important Files

```text
app/src/main/java/com/rmichels/phasegame/audio/
  AudioEngine.kt          Public engine boundary inside the app module
  AudioEvent.kt           Events, buses, sessions, and diagnostics
  AudioMix.kt             Central default master and bus gains
  GeneratedBackingTrackCatalog.kt  All editor-installed tracks/tier WAVs
  BackingTrackConfig.kt   ACTIVE_BACKING_TRACK_ID selection
  BackingTrackRuntime.kt  Permanent tier, loop, and tempo helpers
  NativeAudioEngine.kt    Resource loading, lifecycle, and JNI calls
  SoundCatalog.kt         Stable sample IDs and Android raw resources

app/src/main/java/com/rmichels/phasegame/
  GameAudioConductor.kt   Background Base/backing-track scheduling
  PopRockPhaseMelody.kt   Phase-scoped Base melody and Player harmony
  MainActivity.kt         Engine ownership and player feedback
  DesignScreen.kt         Auditions and whole-bar previews

app/src/main/cpp/
  AudioEngine.h/.cpp      Queue, stream, scheduler, voices, and mixer
  JniBridge.cpp           Kotlin/native bridge
  CMakeLists.txt          Native build and Oboe link

tools/
  generate-sounds.ps1     Generated tone/percussion asset authoring

C:/MyDocs/BeatPhaserTrackEditor/
  Adjacent Rust sequencer, tier renderer, and direct game installer
```

## Sound Creation

### Editing the existing generated tones

Open `tools/generate-sounds.ps1`. Its main controls include:

- waveform: `sine`, `square`, `triangle`, `chip`, or `noise`;
- pitch in hertz;
- volume;
- duration;
- attack;
- release;
- the 12-note Design Base palette.

Run it from the repository root:

```powershell
.\tools\generate-sounds.ps1
```

The script regenerates and overwrites the corresponding files in
`app/src/main/res/raw`. Review the changed WAV files before committing.

Changing the Design Base pitch array also regenerates the Player palette one
octave above it. Keep the array order aligned with the pitch rows displayed by
`DesignScreen`.

Gameplay tones use a clean triangle oscillator with a short, soft envelope,
similar in character to the PICO-8 triangle voice. Base fallback tones are
rooted at D3; successful Player feedback is rooted at F#3. Good and Close use
the same pitch as Perfect so timing feedback never pushes the melody out of
key. The older blended `chip` waveform remains available in the script for
experimentation.

### Replacing a sound without changing code

The simplest customization is to replace an existing WAV with a compatible WAV
using exactly the same filename. For example, replacing:

```text
app/src/main/res/raw/snare.wav
```

changes the sound behind `SoundCatalog.SNARE` without changing Kotlin code.

### Converting a custom sound

Using FFmpeg:

```powershell
ffmpeg -i input.wav -ac 1 -ar 48000 -c:a pcm_s16le output.wav
```

You can also export these settings from a DAW or audio editor:

```text
Channels: mono
Sample rate: 48000 Hz
Encoding: signed 16-bit PCM
Container: WAV
```

Short one-shot assets are the intended use. Every registered sample is fully
decoded into memory, so this engine is not a streaming music player.

## Adding a New WAV

Suppose the new file is `phase_complete.wav`.

### 1. Add the resource

Place it at:

```text
app/src/main/res/raw/phase_complete.wav
```

Android resource names must use lowercase letters, numbers, and underscores.

### 2. Assign a unique sample ID

In `SoundCatalog.kt`:

```kotlin
val PHASE_COMPLETE = AudioSampleId(41)
```

Current IDs occupy `1..33` and `40`. Native storage currently permits IDs
`1..63`. IDs must be unique. Increase `kMaxSamples` in `AudioEngine.h` if the
catalog outgrows that range.

### 3. Add it to the preload list

In `SoundCatalog.rawResources`:

```kotlin
add(PHASE_COMPLETE to R.raw.phase_complete)
```

`NativeAudioEngine.prepare()` reads and validates every catalog resource before
play. An incompatible WAV causes preparation to fail with the sample ID.

### 4. Play it

For immediate feedback:

```kotlin
val session = audioEngine.createSession()

audioEngine.playImmediate(
    sampleId = SoundCatalog.PHASE_COMPLETE,
    bus = AudioBus.PLAYER,
    sessionId = session,
    gain = 0.8f,
    priority = 3
)
```

For a clock-aligned future event:

```kotlin
audioEngine.schedule(
    AudioEvent(
        sampleId = SoundCatalog.PHASE_COMPLETE,
        targetElapsedRealtimeNanos = targetNanos,
        bus = AudioBus.PLAYER,
        sessionId = session,
        gain = 0.8f,
        playbackRate = 1f,
        priority = 3
    )
)
```

Do not schedule rhythm events with `delay()` followed by `playImmediate()`.
Calculate their absolute elapsed-realtime timestamp and submit them early.

## Audio Events

`AudioEvent` supports:

| Field | Meaning |
|---|---|
| `sampleId` | Preloaded PCM source |
| `targetElapsedRealtimeNanos` | Absolute onset on Android elapsed realtime |
| `bus` | Base, Player, Percussion, or Voice |
| `sessionId` | Group used for cancellation and ownership |
| `gain` | Linear amplitude; `1f` is the sample's normal level |
| `pan` | `-1f` left, `0f` center, `1f` right |
| `playbackRate` | Pitch/speed ratio; must be greater than zero |
| `attackMs` | Optional additional fade-in |
| `releaseMs` | Optional fade near the end of the sample |
| `priority` | Voice-stealing importance |

`playbackRate` changes pitch and duration together. It is conventional sample
resampling, not time stretching.

The mixer applies a soft `tanh` limiter after summing voices. Excessive gains
will therefore compress/distort instead of overflowing, but that is not a
substitute for sensible mix levels.

## Immediate Versus Scheduled Playback

Use `playImmediate()` for:

- finger-down feedback;
- tapping a pitch in Design Mode;
- UI sounds that should begin on the next available callback.

Use `schedule(AudioEvent)` for:

- rhythm steps;
- whole-bar previews;
- anything that must align with `RhythmClock`;
- chords, by sending several events with the same target timestamp.

`GameAudioConductor` currently schedules 100 ms ahead. If it falls behind, it
skips stale steps. Native code also rejects events more than 40 ms late, so a
stall does not cause a burst of old notes.

Always use `SystemClock.elapsedRealtimeNanos()` or a timestamp derived from the
same elapsed-realtime origin. Do not use wall-clock time.

## Chords

A chord is several events with:

- the same target timestamp;
- the same session;
- normally the same bus and priority;
- different sample IDs.

Design Mode permits four pitches per step. The engine itself is not limited to
four-note chords, but the 48-voice total and stealing policy still apply.

## Sessions and Cancellation

Create a session for a logical owner:

```kotlin
val previewSession = audioEngine.createSession()
```

Use that session for every event in the preview, and cancel it when a new
preview starts or the screen leaves:

```kotlin
audioEngine.cancelSession(previewSession)
```

Cancellation removes pending events and stops active voices from that session.
Canceled session IDs must not be reused; request a new session.

Good session boundaries include:

- one gameplay Base timeline;
- the current Percussion generation;
- one screen's player feedback;
- one Design preview;
- one voice bark.

## Buses and Mix Control

The default mix is centralized in `AudioMix.kt`:

```kotlin
const val MASTER_GAIN = 1f
const val BASE_GAIN = 1f
const val PLAYER_GAIN = 1f
const val PERCUSSION_GAIN = 0.7f
const val VOICE_GAIN = 1f
```

`MainActivity` applies this configuration before starting the engine. Change
these constants for persistent game-wide balancing.

Available buses:

```kotlin
AudioBus.BASE
AudioBus.PLAYER
AudioBus.PERCUSSION
AudioBus.VOICE
```

Set a bus gain:

```kotlin
audioEngine.setBusGain(AudioBus.PERCUSSION, 0.7f)
```

Set master gain by passing `null`:

```kotlin
audioEngine.setBusGain(null, 0.8f)
```

Gains are linear:

```text
0.0 = silent
0.5 = half amplitude
1.0 = unchanged
```

There is no settings UI for these controls yet.

## Voices and Priority

The engine has 48 preallocated voices. When all are busy, selection is
deterministic:

1. use an inactive voice;
2. prefer replacing a lower-priority voice;
3. replace the oldest matching-priority voice.

Current convention:

```text
0  percussion/default
1  Base
2  Design preview/audition
3  player input feedback
```

Use higher priority sparingly. A priority is not a volume.

## Registering Procedural PCM

PCM can be created outside the callback and registered before the engine starts:

```kotlin
nativeAudioEngine.registerPcm16(
    sampleId = SoundCatalog.TITLE_VOICE,
    sampleRate = 48_000,
    samples = renderedShortArray
)
```

The allophone speech system uses this route. Registration copies/converts the
PCM into native storage. Do not register samples while the output stream is
running.

This API is also the path for a future Kotlin-side offline synthesizer.

## Diagnostics

Call:

```kotlin
val diagnostics = audioEngine.diagnostics()
```

It reports:

- granted sample rate and channel count;
- frames per burst and buffer size;
- actual audio API, sharing mode, and performance mode;
- estimated output latency when available;
- xrun/underrun count;
- active voices;
- dropped command count;
- late event count.

Configuration values must be read after the stream starts. Requested exclusive
or low-latency modes are preferences; the device may grant a fallback.

Useful warning signs:

- `xRunCount` increasing: the callback/device cannot sustain the workload;
- `droppedCommandCount` increasing: command or scheduled-event capacity is
  exhausted;
- `lateEventCount` increasing: producers are submitting events too late.

## Lifecycle

`MainActivity` owns the engine:

```text
onCreate   create, prepare, register procedural PCM, start
onStart    start if stopped
onStop     stop
onDestroy  release
```

Screens receive the shared engine; they must not create their own engine or
audio stream.

Scheduling returns `false` while the engine is stopped. The background
conductor advances past elapsed steps rather than accumulating sounds for
resume.

## Real-Time Safety

The Oboe callback must remain predictable. Do not add these operations inside
`onAudioReady()`:

- allocation or deallocation;
- file/resource access;
- JNI calls;
- logging;
- locks or waits;
- coroutine/thread operations;
- sample decoding.

Prepare data outside the callback and communicate through fixed-size commands.

## Current Limitations

The engine does not yet provide:

- runtime sine/triangle/square/noise oscillators;
- stereo or compressed asset decoding;
- streaming background music;
- looping voices;
- time-stretching independent of pitch;
- filters, reverb, delay, or compression controls;
- smooth release when canceling a session;
- a developer diagnostics UI;
- build-time validation of every WAV asset.

These can be added without moving game rules into C++, but callback safety and
bounded memory must be preserved.

## Common Development Tasks

### Import a backing track from the Rust editor

For a focused operational reference, also see
`docs/BACKING_TRACK_EDITOR.md`.

The adjacent project is normally:

```text
C:\MyDocs\BeatPhaserTrackEditor
```

Run the release executable:

```text
C:\MyDocs\BeatPhaserTrackEditor\target\release\beat-phaser-track-editor.exe
```

Or run it from source with `cargo run`. Then open or create a `.bpt` project:

1. set the project title, BPM, steps per beat, and step count;
2. add an instrument, give it a name, choose its source WAV, and set its gain;
3. leave **Pitched instrument** off for a one-row drum/percussion pattern;
4. turn **Pitched instrument** on for a piano roll, then identify the note
   actually sounding in the source WAV (for example `C4`) and choose its
   playable range;
5. draw the pattern. Pitched steps may contain chords;
6. add patterns as needed and assign each one a tier;
7. choose **Preview tier** and use the transport to hear the same pattern
   selection the offline renderer will use;
8. save the editable project as `.bpt`;
9. click **Game Folder...** and select the PhaseGame project root (normally
   `C:\MyDocs\PhaseGame`); this setting persists in the editor's Windows app
   data;
10. click **Install in Game**, then rebuild/deploy PhaseGame.

At active tier N, each instrument uses its highest pattern whose assigned tier
is less than or equal to N. Therefore a new kick pattern can replace the old
kick while a snare from another instrument continues to layer. A single
instrument cannot have two patterns assigned to the same tier.

The installer:

- validates the project and source WAVs;
- transposes pitched samples with equal-tempered playback rates;
- mixes one complete 48 kHz mono PCM-16 WAV for every tier;
- copies the tier WAVs into `app/src/main/res/raw`;
- updates `backing_tracks/installed_tracks.json`;
- regenerates `GeneratedBackingTrackCatalog.kt` and assigns sample IDs from
  `41` through `63` across all installed tiers.

Reinstalling the same slugged track title replaces its old tier WAVs and
catalog entry. Installing a different title preserves both tracks. Do not edit
the generated Kotlin catalog by hand; the next install will replace it.

The game currently uses one track. Select it in `BackingTrackConfig.kt`:

```kotlin
internal const val ACTIVE_BACKING_TRACK_ID = "new_backing_track"
```

The ID is the resource-safe slug derived from the editor title and is visible
in `GeneratedBackingTrackCatalog.kt`. If the configured ID is absent, the game
falls back to the first generated catalog entry.

Each clean gameplay bar advances one tier; a miss resets to tier zero. At an
active tier, each instrument selects its highest sequence tier not exceeding
that tier. This allows a later pattern to replace an earlier pattern for the
same instrument while separate instruments continue layering.

During authoring, pitched instruments reuse one source WAV. The editor renders
each note offline using:

```text
2 ^ ((played MIDI note - source MIDI note) / 12)
```

Each tier WAV already contains the full audible mix for that tier, including
all lower-tier instruments or their applicable replacement patterns. At
runtime `GameAudioConductor` schedules one tier WAV at each backing-loop
boundary, so backing audio consumes exactly one native voice.

The selected track's BPM and steps-per-beat determine the shared gameplay step
duration. Its step count determines the backing loop boundary. Changing these
values in the editor therefore changes both backing playback and the game's
master rhythm timing after the next build.

For automation, the release executable also supports:

```powershell
beat-phaser-track-editor.exe --install <project.bpt> C:\MyDocs\PhaseGame
```

**Export Track Pack** is separate from installation. It creates a portable
folder containing `track_pack.json`, normalized source WAVs, an install note,
and rendered tier WAVs. Use it for backup or transfer; use **Install in Game**
for the normal game integration workflow.

### Change the built-in melody

`PopRockPhaseMelody.kt` owns fallback pitch generation. It chooses a functional
pop-rock progression, places chord tones on strong positions, fills between
them with nearby D-major tones, and generates one complete phrase for the
current gameplay phase.

The same phrase repeats for every attempt while that phase remains active.
When the player clears enough bars for the playable pattern to phase,
`selectPhase()` generates a new phrase. Player feedback harmonizes the
corresponding shifted Base note by a diatonic third. If that would equal the
Base pitch sounding on the same step, it uses a diatonic fifth instead. This
keeps Player notes consonant, in key, lower than the old octave-up register,
and distinct from simultaneous Base notes.

Designed pitches bypass the random phrase and play their explicitly selected
D-major palette notes.

### Add a new bus

This touches both Kotlin and C++:

1. add the enum value to `AudioBus`;
2. increase the native bus gain array;
3. verify ordinal mapping through JNI;
4. update mix controls and tests.

Because bus mapping currently uses enum ordinals, append new buses rather than
reordering existing values.

### Increase sample or voice capacity

Change the fixed constants in `AudioEngine.h`:

```cpp
kMaxSamples
kMaxVoices
kMaxScheduledEvents
kCommandQueueSize
```

Higher capacities consume more memory and may increase callback scanning work.
Measure xruns on the Pixel 8a after changing them.

## Validation Checklist

After an audio change:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

With the Pixel 8a connected:

```powershell
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat installDebug
```

Then manually verify:

- title voice;
- built-in gameplay;
- designed Base and Player pitches;
- two-, three-, and four-note chords;
- Perfect/Good/Close levels and Miss sound;
- layer entry/reset;
- repeated previews and navigation;
- background/resume;
- a multi-minute run with no drift or missing voices.
