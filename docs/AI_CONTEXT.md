# Beat Phaser: AI Ramp-Up Context

Use this document as the compact, authoritative starting context for a new AI
coding session. Inspect the referenced source before changing behavior.

## Product

Beat Phaser is a Kotlin/Jetpack Compose Android rhythm game inspired by
Steve Reich-style phased rhythmic cycles, especially *Clapping Music*. A fixed
base pattern plays continuously while the player taps the currently phased
version. The visual language is intentionally abstract: radial rhythm wedges,
inward-moving indicators, phase color, motion, and minimal text.

The developer uses Windows 11, Android Studio, Gradle Kotlin DSL, and a physical
Google Pixel 8a. They are experienced with JavaScript and C#/.NET but are newer
to Kotlin/Android. Prefer clear, human-maintainable code and concise comments.
Permanent collaboration preference: make one granular change at a time and ask
before continuing, unless the user explicitly groups several changes.

## Current Gameplay

- Title screen: **Start**, **Design**, and **Perfect Mode** toggle.
- Starting jumps directly to gameplay. A master-clock-synchronized
  `Ready`, `2`, `3`, `4`, `Go!` countdown pre-rolls the indicators so the first
  played step reaches its target at gameplay start.
- Timing: `250 ms` per step. All gameplay timing derives from one monotonic
  `RhythmClock` based on `SystemClock.elapsedRealtime()`.
- Supported pattern lengths are `3..16`, matching the minimum three sides
  required by the polygon renderer.
- Player pattern is the base Boolean rhythm circularly shifted by phase.
- Phase advances every `REPETITIONS_PER_PHASE` queued bars (currently `4`).
- Accuracy windows after `10 ms` input compensation:
  Perfect `<=35 ms`, Good `<=70 ms`, Close `<=120 ms`, otherwise Miss.
- Perfect Mode promotes every non-miss to Perfect.
- A bar clears only after all `true` steps in the current pattern are hit
  without a miss during that clock bar. It never clears merely because time
  elapsed.
- Each clean completed bar advances one generated backing-track tier. A miss
  resets the backing tier to zero. The installed catalog currently contains
  `new_backing_track`, with four complete tier mixes; treat the generated
  catalog, rather than this document, as authoritative because the editor can
  replace it at any time.
- The logical pattern queue still contains up to 11 bars and remains essential
  to scoring, retries, phase selection, and look-ahead indicators. Its former
  falling-dot composable is disabled, so the queue is no longer drawn.
- Misses still raise the hidden queue-position state, but the queue-based game
  over effect is currently disabled. `GAME_OVER` remains in navigation for a
  future explicit game-over design.
- The visible rhythm is a static radial polygon/tunnel. Played steps are opaque
  wedges; rests are ghost wedges. Trapezoid indicators travel inward and reach
  a colored threshold at the expected tap time.
- The next distinct phase fades into the same fixed wedge positions over the
  final two successful logical bars. A miss produces a visual recoil and keeps
  the current pattern at the head of the visual queue.
- A white `BlendMode.Difference` wedge sweeps with eased clock-like ticks to
  invert the current region. Phase triangle, indicator, and threshold colors
  come from `PhaseVisualTheme`; static themes are currently enabled.
- `TapArea` covers the gameplay surface except for the top-right menu control.
  Input is recognized on finger-down, not finger-up.
- The small gameplay **Menu** contains only **Back to start screen**. Returning
  disposes the active gameplay/audio sessions; pressing Start again constructs
  a fresh clock, queue, phase melody, and gameplay state.

## Design Mode

`GameDesign` contains:

```text
stepCount: Int
baseNotes: List<List<Int>>
playerNotesByPhase: List<List<List<Int>>>
```

- Each hand has 24 chromatic pitch rows across two octaves: Base C2..B3 and
  Player C4..B5. The non-overlapping boundary prevents hand crossing.
- Design can show all chromatic rows or filter them by root plus Major, Natural
  Minor, Major Pentatonic, Minor Pentatonic, or Blues. Filtering is view-only;
  it does not delete existing out-of-scale notes.
- The pitch matrix scrolls vertically; drag its note-label gutter to browse the
  full chromatic range without painting cells.
- Width is adjustable from 3 through 16.
- A column contains an ordered pitch list with a maximum of four notes.
- An empty Base chord makes that rhythm step false. An empty enabled Player
  chord uses the generated phase-melody harmony fallback.
- There is one cycleable Player matrix per possible phase.
- Player columns whose shifted rhythm step is false are disabled.
- Matrices use one page when cells fit and otherwise exactly two pages; an odd
  width places the larger half on page 1.
- Tap toggles pitches in a chord and auditions newly enabled notes.
- Two-dimensional drag paints a monophonic melodic contour: horizontal movement
  changes step, vertical movement changes pitch, and entering a column replaces
  its prior chord with one note.
- **Preview Bar** plays the current Base matrix or Player phase at game tempo.
- Design edits are draft state. Design's **Play** commits the snapshot and
  starts it. Normal Start uses the built-in rhythm until a design is committed.
- `GameDesignSaver` preserves chord designs through recreation and migrates the
  older 12-row chord and monophonic saver formats into the new hand ranges.

## Audio

- A new AI session changing audio should begin with
  `docs/AUDIO_SYSTEM_AI_HANDOFF.md`; it records the reviewed runtime paths,
  native contracts, invariants, and known risks.
- Developer workflows and API examples are documented in
  `docs/AUDIO_ENGINE_DEVELOPER_GUIDE.md`.
- Backing-track authoring and installation are documented in
  `docs/BACKING_TRACK_EDITOR.md`.
- Gameplay, Design preview/audition, and title voice use one native Oboe engine.
- Kotlin submits elapsed-realtime nanosecond events through a bounded native
  queue. The callback owns onset timing, resampling, envelopes, and a
  deterministic 48-voice mixer.
- `GameAudioConductor` schedules base and generated backing tracks away from
  the UI thread using rolling look-ahead and stale-event suppression.
- Audio sessions cancel pending/active sounds during preview and navigation.
- Buses exist for Base, Player, Percussion, Voice, and master gain.
- Short PCM WAV files are pre-rendered in `app/src/main/res/raw`.
- Selectable Piano, Marimba, Vibraphone, Flemish Harpsichord, and Distorted
  Guitar gameplay banks are derived from CC0 recordings.
  `tools/import-pitched-samples.ps1` renders each complete bank from its
  checked-in manifest. Separate title-screen selectors choose the left/Base and
  right/Player timbres independently for generated and designed notes without
  changing composition generation.
- Each instrument contains 24 Base samples covering C2..B3 and 24 Player
  samples covering C4..B5.
- Built-in base and unset Player notes share `PopRockPhaseMelody`. It generates
  one full-bar phrase per active gameplay phase, repeats it unchanged until the
  player advances the phase, then creates a new phrase.
- The first phase randomly selects a tonic, Major or Natural Minor mode, and a
  functional progression. Later phases modulate through related keys/modes and
  choose a first chord with a shared pivot tone. Both hands continue from their
  prior ending notes with compact voice-leading, preserving an audible key
  change without treating the next phase as an unrelated song.
- Built-in Perfect, Good, and Close preserve the intended pitch and use
  separate piano assets at different levels. Designed successful notes
  currently use their selected pitch asset for all three judgments. Miss keeps
  its dedicated dissonant square-wave sample.
- The adjacent Rust editor renders one complete mono PCM-16 WAV per tier and
  installs it directly into the game. Runtime backing audio therefore occupies
  one native mixer voice regardless of the authored instrument/chord count.
- The editor lives at `C:\MyDocs\BeatPhaserTrackEditor`. It supports unpitched
  toggle rows, pitched piano rolls generated from a declared source-WAV note,
  chords, per-instrument gain, multiple tiered patterns, exact tier preview,
  `.bpt` project saves, portable track-pack export, and one-click game install.
- At tier N, each instrument contributes its highest pattern assigned to a tier
  less than or equal to N. A higher-tier pattern replaces the earlier pattern
  for that instrument; different instruments layer together. The editor mixes
  that complete result offline into the tier WAV.
- `GeneratedBackingTrackCatalog.kt` lists every installed track and tier WAV.
  `BackingTrackConfig.kt` contains the high-level
  `ACTIVE_BACKING_TRACK_ID` selection. `BackingTrackRuntime.kt` supplies
  permanent tempo, tier-selection, and loop-boundary behavior.
- The current track's generated catalog is authoritative for its tier count.
  Its BPM and steps-per-beat also determine the game's step duration, and its
  step count determines each backing-WAV loop boundary.
- Native allophonic speech PCM says "Beat Phaser" through the shared engine.
  Curated bark definitions also exist for "error" and "sequence complete," but
  gameplay triggers/buttons for those were removed.

## Visual System

See `docs/VISUAL_SYSTEM.md` for the polygon geometry, indicator timeline,
phase-transition preview, theme selection, input surface, and safe extension
points.

## Source Map

- `app/src/main/java/com/rmichels/phasegame/MainActivity.kt`
  - navigation, title/game-over/game screens
  - gameplay-session Compose state and orchestration
  - completed-bar processing, layers, countdown, and player audio events
- `app/src/main/java/com/rmichels/phasegame/RhythmClock.kt`
  - authoritative bar, phase, pause/resume, and animation positions
- `app/src/main/java/com/rmichels/phasegame/TapJudgment.kt`
  - nearest expected-hit search and Perfect/Good/Close/Miss windows
- `app/src/main/java/com/rmichels/phasegame/PatternQueue.kt`
  - queued pattern model, per-bar performance, and clean-bar transitions
- `app/src/main/java/com/rmichels/phasegame/TapArea.kt`
  - full-surface pointer-down input
- `app/src/main/java/com/rmichels/phasegame/RhythmPolygon.kt`
  - radial wedge geometry, threshold lines, phase overlays, tick wedge, and
    trapezoid indicator rendering
- `app/src/main/java/com/rmichels/phasegame/RhythmIndicators.kt`
  - pure look-ahead indicator schedule derived from queued rhythms
- `app/src/main/java/com/rmichels/phasegame/PhaseVisualTheme.kt`
  - generated/resolved phase themes and upcoming-phase fade calculation
- `app/src/main/java/com/rmichels/phasegame/StaticPhaseVisualThemes.kt`
  - opt-in static palette in phase order
- `app/src/main/java/com/rmichels/phasegame/RhythmDots.kt` and
  `PatternQueueDisplay.kt`
  - retained legacy dot/falling-queue renderer; currently not composed
- `app/src/main/java/com/rmichels/phasegame/PopRockPhaseMelody.kt`
  - phase-scoped chord progressions and unified two-hand pitch arrangement
- `app/src/main/java/com/rmichels/phasegame/PianoPitch.kt`
  - non-crossing hand ranges, note labels, and Design scale presets
- `app/src/main/java/com/rmichels/phasegame/GameInstrument.kt`
  - title-screen pitched-instrument choices
- `app/src/main/java/com/rmichels/phasegame/GameAudioConductor.kt`
  - background master-clock music scheduling and stale-event suppression
- `app/src/main/java/com/rmichels/phasegame/audio/`
  - Kotlin engine API, event/session model, catalog, generated backing-track
    data/runtime, and JNI-backed implementation
- `app/src/main/cpp/`
  - Oboe stream, PCM store, command queue, voice mixer, JNI bridge
- `app/src/main/java/com/rmichels/phasegame/GameDesign.kt`
  - design model, validation, resizing, phase masks, saver, pagination
- `app/src/main/java/com/rmichels/phasegame/DesignScreen.kt`
  - matrices, pages, shared-engine preview, chord tap/diagonal painting
- `app/src/main/java/com/rmichels/phasegame/PitchPalette.kt`
  - designed-accuracy playback policy; Good/Close are currently neutral rates
- `app/src/main/java/com/rmichels/phasegame/AllophoneSpeechSynthesizer.kt`
  - procedural PCM allophone rendering/playback
- `app/src/main/java/com/rmichels/phasegame/VocalBarks.kt`
  - reusable allophones and curated bark sequences
- `tools/generate-sounds.ps1`
  - synthetic pitched-tone and Miss WAV alternative
- `tools/import-pitched-samples.ps1` and `tools/pitched-sample-packs/`
  - current manifest-driven VCSL piano rendering workflow
- `tools/generate-vocal.ps1`
  - older/generated vocal tooling
- `C:\MyDocs\BeatPhaserTrackEditor`
  - adjacent Rust backing-track sequencer and direct-install tool
- `app/src/test/java/com/rmichels/phasegame/`
  - focused clock, judgment, queue, indicator, polygon-geometry, theme,
    design, melody, audio-runtime, and pagination unit tests

## Important Invariants

- Do not introduce assumptions that a bar has 12 steps; gameplay must remain
  generic for every length 3..16.
- `baseRhythm`/`GameDesign.baseRhythm` is the rhythm source of truth.
- Scoring requires the number of successful unique step indices to equal the
  number of `true` values in the active phased pattern; never hard-code a hit
  count.
- Timing, animation, scheduling, phase, and start cues must share the master
  clock and must not create independent drifting timers.
- The pattern queue is a gameplay model even while its old visual composable is
  disabled. Do not remove it merely because the falling rows are hidden.
- Indicator positions are expressed as steps until hit. Keep countdown and
  gameplay indicators on that same step-based timeline.
- Polygon geometry assumes every queued rhythm has the same length as the
  active rhythm.
- Keep tap judgment on pointer-down.
- Chords are limited to `DESIGN_MAX_CHORD_SIZE` (currently 4). Keep drag-paint
  monophonic unless its editing semantics are intentionally redesigned.
- Preserve unrelated user edits in a dirty worktree.

## Build and Device

From `C:\MyDocs\PhaseGame`:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest assembleDebug
.\gradlew.bat lintDebug
```

Debug APK:
`app/build/outputs/apk/debug/app-debug.apk`

ADB is normally:
`$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`

Package caveat: Kotlin namespace/source package is
`com.rmichels.phasegame`, but `applicationId` is still
`com.example.phasegame` in `app/build.gradle.kts`.
