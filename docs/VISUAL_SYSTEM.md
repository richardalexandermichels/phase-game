# Beat Phaser Visual System

This guide describes the current radial gameplay presentation and the data it
consumes. The visual system does not decide scoring or phase progression; it
renders state produced by the gameplay model and `RhythmClock`.

## Current Presentation

Gameplay uses a full-screen radial rhythm tunnel:

- one angular slot exists for each rhythm step;
- a played step is an opaque triangular wedge extending past the screen;
- a rest is the same wedge geometry at reduced opacity;
- a colored threshold crosses each played wedge near the center;
- future played steps appear as trapezoid indicators moving toward that
  threshold;
- an eased white difference-blend wedge acts as the clock hand by inverting
  the region it crosses;
- the next distinct phase fades into the same fixed angular slots before the
  logical queue advances.

The former horizontal timing cursor, top `RhythmDots`, and falling
`PatternQueueDisplay` are commented out in `MainActivity.kt`. Their source
files remain available for experimentation.

## Data Flow

```text
RhythmClock + patternQueue + gameplay state
                  |
                  v
            MainActivity.kt
                  |
                  v
            RhythmPolygon
             /          \
            v            v
futureRhythmIndicators  upcomingPhaseVisual
            |            |
            +------ Canvas drawing
```

`patternQueue` is still gameplay state, not obsolete display data. It supplies:

- the current rhythm and phase;
- future bars used to create travelling indicators;
- the next distinct phase used for the fade-in preview;
- retry behavior after a miss;
- scoring and clean-bar queue advancement outside the renderer.

The old `introCurrentSlot`/miss-raise state also remains active, but its
queue-based game-over effect is currently disabled. The falling queue remains
hidden.

## Clock and Indicator Timeline

During gameplay:

```kotlin
currentStepPosition = barProgress * rhythm.size
```

`futureRhythmIndicators()` flattens played notes from queued bars into
`RhythmIndicator` values containing `queuedBarOffset`, `stepIndex`, and
`stepsUntilHit`. `RhythmPolygon` converts `stepsUntilHit` into radial distance.
An indicator reaches `indicatorTargetRadius` when `stepsUntilHit == 0`.

During the countdown, `MainActivity` supplies a negative
`introIndicatorStepPosition`:

```text
-4.0 = four steps before gameplay
 0.0 = gameplay downbeat
```

This preserves normal indicator velocity through the countdown instead of
compressing several bars of travel into a short normalized animation.

## Polygon Geometry

`regularPolygonVertices()` returns unit-circle vertices. Inside the Canvas,
`RhythmPolygon` scales them by `polygonRadius` and translates them around the
Canvas center.

For each slot, the renderer:

1. selects two adjacent polygon vertices;
2. moves both endpoints inward along the side to create a gap;
3. creates rays from the center through those shortened endpoints;
4. extends the rays beyond the Canvas using `farDistance`;
5. fills the triangle between the center and the two extended points.

Indicator trapezoids use the same rays at an inner and outer radial distance,
so their width naturally narrows as they approach the center.

Important constraints:

- all active and queued rhythms must have equal lengths;
- supported rhythms contain 3 through 16 steps;
- `indicatorTravelBars` must be positive;
- `phaseVisualThemes` must not be empty;
- avoid a zero effective polygon radius because ray normalization assumes a
  non-zero direction vector.

## Phase Preview and Miss Recoil

`upcomingPhaseVisual()` locates the first queued bar whose `phaseIndex` differs
from the current head. Its opacity rises over `slideDurationBars`, currently
two bars. The upcoming phase uses the same geometry as the current phase; it no
longer rotates into place.

On an explicit miss, `MainActivity`:

- increments `phaseTransitionMissCount`;
- animates `phaseTransitionRecoil` away from and back toward zero;
- marks `repeatCurrentPatternNextBar` so indicators for the repeated pattern
  remain visible before the clock crosses the bar boundary;
- resets the active backing tier.

## Phase Themes

`PhaseVisualTheme` currently contains:

```text
triangleColor
indicatorColor
targetColor
clockHandColor
```

`resolvePhaseVisualThemes()` selects either generated shuffled hues or
`STATIC_PHASE_VISUAL_THEMES`. `USE_STATIC_PHASE_VISUAL_THEMES` is currently
`true`, and the first `stepCount` entries are used in phase order.

The current difference-blend tick wedge always draws white, so
`clockHandColor` is retained but not consumed by that renderer. Remove it or
reuse it deliberately if the clock visualization changes again.

## Input Surface

`TapArea` is a transparent full-size overlay composed after the polygon. It
uses `awaitFirstDown(requireUnconsumed = false)` and invokes its callback on
finger-down. `rememberUpdatedState` lets the long-lived pointer coroutine call
the latest callback after recomposition.

The tap position is passed as an `Offset`, but gameplay currently ignores it.
This preserves an extension point for position-sensitive input.

## Safe Change Boundaries

- Change wedge and indicator geometry in `RhythmPolygon.kt`.
- Change pure indicator scheduling in `RhythmIndicators.kt` and add unit tests.
- Change fade timing in `upcomingPhaseVisual()` and its tests.
- Change palette values in `StaticPhaseVisualThemes.kt`.
- Keep judgment windows and expected-hit selection in `TapJudgment.kt`.
- Keep logical queue advancement in `PatternQueue.kt`.
- Keep all time positions derived from `RhythmClock`; do not create a separate
  visual timer.

## Validation

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

Then smoke-test on a device:

- countdown indicators approach at normal gameplay speed;
- the first downbeat aligns with audio and the threshold;
- the eased tick wedge aligns with each slot center;
- future indicators use the queued phase's colors;
- the next phase fades without rotating;
- a miss recoils the preview and repeats the active visual pattern;
- lifecycle stop/resume does not create visible clock drift.
