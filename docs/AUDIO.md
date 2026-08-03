# Audio system

Beat Phaser uses a Kotlin scheduler and a small C++/Oboe mixer. Kotlin decides
*what* should play; the native callback decides the exact output frame. This
keeps music policy easy to edit while avoiding UI-thread timing jitter.

## Pipeline

```text
res/raw WAVs ── SoundCatalog ── NativeAudioEngine.prepare
                                      │
GameplaySession ── GameAudioConductor ── AudioEvent timestamps
Player tap ────────────────────────────┤
                                      ▼
                              lock-free command queue
                                      ▼
                                Oboe callback/mixer
                                      ▼
                                  audio device
```

`MainActivity` prepares the sample bank on `Dispatchers.IO` and shows a loading
or error state rather than blocking `onCreate`. `PhaseAudioHost` owns Android
audio focus, the default mix, procedural title PCM, and engine cleanup.

## Kotlin ownership

| File | Responsibility |
| --- | --- |
| `AudioEngine.kt` | testable playback contract |
| `AudioEvent.kt` | sample/session IDs, buses, event validation, diagnostics |
| `NativeAudioEngine.kt` | JNI wrapper and sample preload |
| `PhaseAudioHost.kt` | Android lifecycle, focus, async-preparation boundary |
| `SoundCatalog.kt` | stable ID-to-resource map and instrument banks |
| `GameAudioConductor.kt` | look-ahead Base/backing event authoring |
| `AudioMix.kt` | centralized bus gains |
| `BackingTrackConfig.kt` | selected generated backing track |

The conductor receives `stepDurationMs`; it does not read a UI constant. When
started or resumed, `firstStepAtOrAfter` skips timestamps that have already
passed, preventing an old beat from being replayed as a late event.

## Native contract

The native engine preloads mono PCM-16 WAVs into floating-point sample arrays,
opens a low-latency stereo Oboe stream, and mixes up to 48 one-shot voices. The
audio callback uses fixed-capacity arrays and a lock-free multi-producer command
queue. It applies per-event gain/pan/rate, bus and master gains, optional
attack/release, linear resampling, and soft clipping.

Real-time callback rules:

- no allocation or collection growth;
- no locks, file I/O, JNI, logging, or sleeps;
- no ownership changes to preloaded sample memory while running;
- communicate through fixed-capacity queues and atomics.

If a new feature cannot obey those rules, calculate it before the callback and
send a compact command.

## Events, sessions, and buses

An `AudioEvent` identifies a sample and exact boot-time target in nanoseconds.
It also contains a bus, session, gain, pan, playback rate, envelope, and voice
priority.

Sessions group cancellable work. A screen creates sessions for Base,
percussion, Player, or voice playback and cancels them on disposal. A miss
replaces the percussion session so already-authored backing events are removed.

The four buses are `BASE`, `PLAYER`, `PERCUSSION`, and `VOICE`. Adjust their
baseline balance only in `DefaultAudioMix`; use event gain for a musical
accent. Android focus loss stops playback, transient ducking reduces the master
gain, and focus gain restores it.

## Replace an existing sound

For a cataloged sample, keep the Android filename and format, replace the WAV
in `app/src/main/res/raw`, then rebuild. Accepted native WAV input is:

- RIFF/WAVE PCM (format 1);
- mono;
- signed 16-bit little-endian;
- any positive sample rate (48 kHz avoids runtime resampling work).

Verify duration and level as well as format. Long tails consume voices and can
make dense patterns steal older low-priority sounds.

## Add an instrument or sample

1. Add resource-safe WAV names under `res/raw` (`lowercase_underscore.wav`).
2. Reserve unique `AudioSampleId` values below the native limit of 320.
3. Add every ID/resource pair to `SoundCatalog.rawResources`.
4. For an instrument, add both 24-note banks and map them in `baseFor` and
   `playerFor`.
5. Update `GameInstrument`, catalog uniqueness tests, and audio provenance.
6. Run tests, assemble the native app, and audition on a device.

The catalog test protects ID uniqueness and separation from backing-track IDs.
Increasing the 320-sample limit requires coordinated Kotlin catalog and C++
capacity changes.

## Rebuild pitched banks

Use the reproducible importer rather than hand-editing 48 files. See
[PITCHED_SAMPLE_IMPORTER.md](PITCHED_SAMPLE_IMPORTER.md). In short:

```powershell
.\tools\import-pitched-samples.ps1 `
  -SourceRoot 'C:\path\to\source' `
  -ManifestPath '.\tools\pitched-sample-packs\vcsl-marimba.json'
```

`generate-sounds.ps1` contains a machine-local FFmpeg path; update that variable
before running it. The title voice is generated at runtime by `audio/voice`.

## Update backing tracks

The adjacent editor renders complete tier loops and owns
`GeneratedBackingTrackCatalog.kt`. Follow
[BACKING_TRACK_EDITOR.md](BACKING_TRACK_EDITOR.md). Select an installed track
only in `BackingTrackConfig.kt`; do not hand-edit the generated catalog.

The active track's BPM and `stepsPerBeat` define the shared gameplay step
duration. Changing them therefore changes the loop, visual timing, judgment
grid, and Base scheduler together.

## Validation

```powershell
.\tools\gradle.ps1 testDebugUnitTest assembleDebug lintDebug
```

On a physical device, verify cold launch, wired/Bluetooth/speaker routes,
headphone disconnect, focus loss/gain, background/foreground, dense chords,
phase boundaries, tier cancellation after a miss, and diagnostics (`xRunCount`,
late/dropped commands, active voices). Do not judge rhythm latency on an
emulator.
