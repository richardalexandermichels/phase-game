# Graphics and Compose UI

The gameplay view is built with Jetpack Compose. `PhaseGameScreen` coordinates
state and effects; `GameScene` lays out the play surface; `RhythmPolygon` draws
the phase visualization on one `Canvas`; `TapArea` owns pointer input.

## Frame model

Compose recomposes when observed state changes. The active play loop waits for
`withFrameNanos`, samples `RhythmClock` using `elapsedRealtime`, asks
`GameplaySession` to process crossed bars, then publishes only the values the
scene needs. Rendering never becomes the authoritative clock.

Do not restore fixed `delay(16)` loops. A device may run at 60, 90, 120 Hz, or
vary its refresh rate; frame callbacks pace work to the actual display.

## Canvas layers

`RhythmPolygon` currently draws, in order:

1. played and ghost wedges for the current phase;
2. target arcs at the hit radius;
3. the upcoming phase overlay and transition blend;
4. a difference-blended clock pulse;
5. incoming hit indicators colored for their queued phase.

`PolygonGeometry.kt` holds host-testable unit-circle geometry. Timeline helpers
live in `RhythmIndicators.kt` and `PhaseVisualTheme.kt`. Keep calculations pure
and outside `Canvas` when they can be named and tested; keep pixel conversion
and actual draw calls inside the draw scope.

## Coordinate model

The regular polygon has one side per rhythm step and begins at the top of the
screen. A `true` rhythm entry receives a full wedge and target; `false` receives
a low-alpha ghost wedge. Hit indicators travel radially from the screen edge to
the target over a configured number of bars.

`barProgress` is always derived from the clock and clamped below `1f` when it is
converted to a step index. The queued patterns—not a second phase counter—drive
upcoming colors and rhythms. Miss recoil changes presentation progress only;
it does not mutate the queue.

## Themes

`PhaseVisualTheme` contains triangle, indicator, target, and clock colors.
`StaticPhaseVisualThemes.kt` is the checked-in palette and must contain at
least 16 entries, the maximum supported pattern length. Random generation
remains available for experiments and deterministic tests.

Material typography and app colors live in `ui/theme`. Phase-specific Canvas
colors live with the game visualization; do not scatter either set through
screen coordinators.

## Make a graphics change

1. Decide whether the change is geometry/timeline logic, drawing, layout, or
   animation state.
2. Put deterministic math in a helper and add a host test under
   `app/src/test/.../ui/game`.
3. Keep `PhaseGameScreen` focused on coordination. Add a stateless component
   when a visual can be previewed independently.
4. Use `remember` only for values expensive to recreate or state that belongs
   to the composition. Never use it to hide an authoritative gameplay counter.
5. Check 3-, 12-, and 16-step patterns, multiple aspect ratios, and at least one
   high-refresh physical device.

Run:

```powershell
.\tools\gradle.ps1 testDebugUnitTest assembleDebug lintDebug
```

For visual QA, use Compose previews for static states and a device for motion.
Capture phase transition, miss recoil, countdown, and an incoming indicator at
the target; those expose most ordering and continuity mistakes.
