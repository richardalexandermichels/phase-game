# Beat Phaser: AI Ramp-Up Context

Use this document as the compact, authoritative starting context for a new AI
coding session. Inspect the referenced source before changing behavior.

## Product

Beat Phaser is a Kotlin/Jetpack Compose Android rhythm game inspired by
Steve Reich-style phased rhythmic cycles, especially *Clapping Music*. A fixed
base pattern plays continuously while the player taps the currently phased
version. The visual language is intentionally abstract: rows of dots, timing
lines, motion, color, and minimal text.

The developer uses Windows 11, Android Studio, Gradle Kotlin DSL, and a physical
Google Pixel 8a. They are experienced with JavaScript and C#/.NET but are newer
to Kotlin/Android. Prefer clear, human-maintainable code and concise comments.
Permanent collaboration preference: make one granular change at a time and ask
before continuing, unless the user explicitly groups several changes.

## Current Gameplay

- Title screen: **Start**, **Design**, and **Perfect Mode** toggle.
- Starting jumps directly to gameplay; the falling queue shows master-clock
  synchronized `Ready`, `2`, `3`, `4`, `Go!` cues.
- Timing: `250 ms` per step. All gameplay timing derives from one monotonic
  `RhythmClock` based on `SystemClock.elapsedRealtime()`.
- Supported pattern length: arbitrary values clamped to `2..16`.
- Player pattern is the base Boolean rhythm circularly shifted by phase.
- Phase advances every `REPETITIONS_PER_PHASE` queued bars (currently `4`).
- Accuracy windows after `10 ms` input compensation:
  Perfect `<=35 ms`, Good `<=70 ms`, Close `<=120 ms`, otherwise Miss.
- Perfect Mode promotes every non-miss to Perfect.
- A bar clears only after all `true` steps in the current pattern are hit
  without a miss during that clock bar. It never clears merely because time
  elapsed.
- Each clean completed bar adds one ordered layer: bass drum, then snare, then
  open hi-hat. A miss resets layers to zero.
- Misses raise the music queue. If the active row is pushed back to the play
  area's midpoint line, the game ends.
- The queue has up to 11 rows and moves like one continuous sheet. The fixed
  base row is opaque at the top; the tap area occupies the bottom 40%.
- Active playable row is bright cyan. Upcoming rows are green with decreasing
  opacity. The moving vertical timing line fades yellow-to-gray each bar.
- Input is recognized on finger-down, not finger-up.

## Design Mode

`GameDesign` contains:

```text
stepCount: Int
baseNotes: List<Int?>                    // pitch or inactive step
playerNotesByPhase: List<List<Int?>>     // one pitch maximum per phase/step
```

- Twelve pitch rows use a D-major-pentatonic palette across registers.
- Width is adjustable from 2 through 16.
- A base column may contain one pitch or `null`; `null` makes that rhythm step
  false.
- There is one cycleable Player matrix per possible phase.
- Player columns whose shifted rhythm step is false are disabled.
- Unset enabled Player cells use the existing generated pop-motif fallback.
- Matrices use one page when cells fit and otherwise exactly two pages; an odd
  width places the larger half on page 1.
- Tap toggles a cell and auditions newly enabled notes.
- Two-dimensional drag paints a monophonic melodic contour: horizontal movement
  changes step, vertical movement changes pitch, and entering a column replaces
  its prior note. Chords are not currently representable.
- **Preview Bar** plays the current Base matrix or Player phase at game tempo.
- Design edits are draft state. Design's **Play** commits the snapshot and
  starts it. Normal Start uses the built-in rhythm until a design is committed.
- `GameDesignSaver` preserves draft/committed designs through recreation.

## Audio

- Gameplay and Design preview each use Android `SoundPool`.
- Gameplay pool limit is 8 simultaneous streams.
- Short PCM WAV files are pre-generated in `app/src/main/res/raw`.
- `tools/generate-sounds.ps1` controls waveform, frequency, volume, duration,
  attack, and release and invokes FFmpeg.
- Base/player design palettes contain 12 samples each; Player samples are one
  octave above matching Base samples.
- Built-in base and unset Player notes use `PopMotifPitchGenerator`: repeated
  3-note/2-note D-major-pentatonic motifs with constrained random mutation.
- Designed Good and Close notes preserve the chosen sample but apply small
  playback-rate detuning. Miss uses its dedicated miss sample.
- Percussion samples: bass drum, snare, open hat, closed hat.
- Native allophonic speech synthesizer currently says “Beat Phaser” on the title
  screen. Curated bark definitions also exist for “error” and “sequence
  complete,” but gameplay triggers/buttons for those were removed.
- A future dedicated low-latency/polyphonic engine is specified in
  `docs/AUDIO_ENGINE_PROJECT.md`.

## Source Map

- `app/src/main/java/com/rmichels/phasegame/MainActivity.kt`
  - navigation, title/game-over/game screens
  - `RhythmClock`, timing helpers, accuracy/scoring
  - queue movement, layers, input, current SoundPool gameplay
  - `PopMotifPitchGenerator`, dot visualization
- `app/src/main/java/com/rmichels/phasegame/GameDesign.kt`
  - design model, validation, resizing, phase masks, saver, pagination
- `app/src/main/java/com/rmichels/phasegame/DesignScreen.kt`
  - matrices, pages, preview SoundPool, tap/diagonal painting
- `app/src/main/java/com/rmichels/phasegame/PitchPalette.kt`
  - ordered design WAV resource IDs and accuracy playback rates
- `app/src/main/java/com/rmichels/phasegame/AllophoneSpeechSynthesizer.kt`
  - procedural PCM allophone rendering/playback
- `app/src/main/java/com/rmichels/phasegame/VocalBarks.kt`
  - reusable allophones and curated bark sequences
- `tools/generate-sounds.ps1`
  - editable musical/percussion WAV generation
- `tools/generate-vocal.ps1`
  - older/generated vocal tooling
- `app/src/test/java/com/rmichels/phasegame/ExampleUnitTest.kt`
  - clock, phasing, 2..16 step, queue, drawing, layer, design, and pagination
    regressions

## Important Invariants

- Do not introduce assumptions that a bar has 12 steps; gameplay must remain
  generic for every length 2..16.
- `baseRhythm`/`GameDesign.baseRhythm` is the rhythm source of truth.
- Scoring requires the number of successful unique step indices to equal the
  number of `true` values in the active phased pattern; never hard-code a hit
  count.
- Timing, animation, scheduling, phase, and start cues must share the master
  clock and must not create independent drifting timers.
- Keep tap judgment on pointer-down.
- Keep Design Mode monophonic until the data model and audio engine explicitly
  support polyphony.
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

