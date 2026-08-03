# Beat Phaser

Beat Phaser is an Android rhythm game about musical phasing: a repeating Base
pattern and the Player pattern move through cyclic offsets in the spirit of
Steve Reich's *Clapping Music*. A clean bar advances the pattern queue and adds
a backing-track tier; a miss repeats the current phase and drops the tier.

## Start here

1. Install Android Studio with the Android SDK and NDK/CMake components.
2. Open this repository root in Android Studio and allow Gradle sync to finish.
3. Select the `app` run configuration and an API 24+ device or emulator.
4. Run the JVM tests from PowerShell:

   ```powershell
   .\tools\gradle.ps1 testDebugUnitTest
   ```

5. Build the complete debug app, including the native audio engine:

   ```powershell
   .\tools\gradle.ps1 assembleDebug
   ```

`tools/gradle.ps1` uses `JAVA_HOME` when valid and otherwise discovers Android
Studio's bundled JDK. `local.properties`, `.gradle`, `.idea`, `.cxx`, and build
outputs are local-only.

## Mental model for a web developer

| Web concept | This project |
| --- | --- |
| entry component | `MainActivity` + `PhaseGameApp` |
| component state | Compose `remember` / `rememberSaveable` |
| requestAnimationFrame | Compose `withFrameNanos` |
| pure application store | `GameplaySession` snapshot |
| Web Audio scheduler | `GameAudioConductor` + native Oboe callback |
| Canvas 2D | Compose `Canvas` in `RhythmPolygon` |
| static assets | Android `res/raw` resources |

Compose is declarative like a UI framework on the web, but recomposition is
not the game loop. Game truth comes from one monotonic clock; frames only sample
that truth for display.

## Documentation

- [Documentation index](docs/README.md)
- [Architecture and source map](docs/ARCHITECTURE.md)
- [Game loop, phasing, and tap rules](docs/GAME_LOOP.md)
- [Audio engine and sound-update workflow](docs/AUDIO.md)
- [Graphics and visual-update workflow](docs/GRAPHICS.md)
- [Backing-track editor](docs/BACKING_TRACK_EDITOR.md)
- [Pitched-sample importer](docs/PITCHED_SAMPLE_IMPORTER.md)
- [Third-party audio provenance](docs/THIRD_PARTY_AUDIO.md)

## Before opening a change

```powershell
.\tools\gradle.ps1 testDebugUnitTest assembleDebug lintDebug
```

Audio timing must also be checked on a physical Android device. Emulators are
useful for layout and logic but do not represent real output latency or route
changes.
