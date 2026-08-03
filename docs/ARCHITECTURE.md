# Architecture

Beat Phaser is one Android application module. It deliberately uses package
boundaries instead of additional Gradle modules while the team and codebase are
small. The important separation is between Android lifecycle, Compose UI,
gameplay rules, musical material, and real-time audio.

## Runtime flow

```text
MainActivity
  ├─ PhaseAudioHost ── NativeAudioEngine ── JNI ── Oboe callback
  └─ PhaseGameApp
       ├─ TitleScreen
       ├─ DesignScreen
       └─ PhaseGameScreen
            ├─ GameplaySession + RhythmClock
            ├─ GameAudioConductor
            └─ RhythmPolygon + TapArea
```

`MainActivity` owns Android lifecycle and the native engine. `PhaseGameApp`
owns screen navigation and saved user selections. `PhaseGameScreen` coordinates
one run, but does not own the game rules or sample mixer. `GameplaySession` owns
the mutable rules state and publishes immutable snapshots to Compose.

## Source map

```text
app/src/main/
  java/com/rmichels/phasegame/
    MainActivity.kt              Android entry point and audio loading state
    app/                         navigation and app-scoped Compose state
    audio/                       engine contract, catalog, mix, conductor, host
      voice/                     procedural title-voice authoring
    design/                      immutable custom-game model and save adapter
    gameplay/                    clock, patterns, queue, judgment, run state
    music/                       pitch ranges, instruments, generated melody
    ui/
      design/                    design-mode Compose UI
      game/                      play screen, Canvas rendering, input surface
      home/                      title UI
      theme/                     Material theme
  cpp/                           real-time Oboe engine and JNI bridge
  res/raw/                       Android's flat packaged audio-resource folder
app/src/test/                    host-side rules, timing, and geometry tests
app/src/androidTest/             device/native integration tests
tools/                           Gradle helper and reproducible audio tooling
backing_tracks/                  generated track-install metadata
docs/                            current developer documentation
```

Android requires `res/raw` to be flat. Use filename prefixes such as
`marimba_base_` to express groups; do not create subdirectories under `raw`.

## Dependency rules

- `MainActivity` may depend on `app`, `audio`, and `ui.theme`.
- `app` composes screens; it should not contain gameplay timing or JNI calls.
- `ui` reads snapshots and emits user intent. Recomposition must not advance
  authoritative game state by itself.
- `gameplay` must not depend on Compose, UI drawing, or the audio engine.
- `design` and `music` are models/algorithms. Compose persistence stays in the
  separate `GameDesignSaver` adapter.
- `audio` may consume gameplay/design/music data to author events. Gameplay
  code must not call native APIs.
- C++ callback code must not allocate, block, log, or call Java.

These are directional rules, not ceremony. Avoid adding dependency-injection,
navigation, or entity frameworks unless the project has a concrete need that
the present state holders cannot meet.

## State ownership

| State | Owner | Lifetime |
| --- | --- | --- |
| current screen and settings | `PhaseGameApp` | activity recreation |
| draft/committed design | `PhaseGameApp` | activity recreation |
| queue, bar results, layers | `GameplaySession` | one run |
| labels, menu, recoil animation | `PhaseGameScreen` | one composition |
| sample registry and mixer | `PhaseAudioHost` / native engine | activity |
| scheduled Base/backing events | `GameAudioConductor` | one run |

When adding state, first decide which row it resembles. Do not copy the same
truth into multiple owners. `GameplaySnapshot` is a presentation copy; the
session remains authoritative.

## Testing boundaries

- Put deterministic rule tests beside the package under `app/src/test`.
- Keep geometry calculations outside `Canvas` when they deserve host tests.
- Use `AudioEngine` fakes to test Kotlin scheduling; native behavior belongs in
  `androidTest` or C++ tests when a host C++ test target is added.
- Test lifecycle, route changes, latency, and dense polyphony on a device.

The broad `DomainIntegrationTest` covers supported pattern sizes and contracts
between design, music, catalog, and backing metadata. Focused tests cover each
package's local behavior.
