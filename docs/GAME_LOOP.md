# Game loop and phasing

## One clock, two consumers

`RhythmClock` derives elapsed time, step position, absolute bar, and phase from
one monotonic start time (`SystemClock.elapsedRealtime`). No subsystem advances
the song by counting rendered frames or coroutine wakeups.

The UI and audio consume that timeline differently:

- `PhaseGameScreen` samples it once per display frame with `withFrameNanos` and
  updates progress plus completed-bar rules.
- `GameAudioConductor` runs on `Dispatchers.Default`, authors events about
  100 ms ahead, and gives the native mixer exact `elapsedRealtimeNanos`
  timestamps.

This is equivalent to rendering from a server-authoritative timestamp rather
than incrementing browser state inside `requestAnimationFrame`.

## Phasing rule

The Base pattern is a cyclic `List<Boolean>`. Phase `p` is:

```kotlin
shifted[index] = base[(index + p) mod stepCount]
```

A positive phase therefore moves an attack one step earlier. The clock holds a
phase for four bars (`REPETITIONS_PER_PHASE`) before moving to the next offset,
and wraps after `stepCount` phases. Pattern lengths 3 through 16 are supported.

The queue contains logical patterns ahead of the active pattern. A clean bar:

1. removes the queue head;
2. appends the next clock-derived phase;
3. increases the backing tier, capped by the installed track;
4. lets the visual preview continue from the new queue.

An incomplete bar or miss keeps the head pattern and resets the backing tier.

## Run state

`GameplaySession` owns:

- the `RhythmClock`;
- queued logical bars;
- successful unique hits and misses per clock bar;
- clock-bar-to-pattern history around the current boundary;
- active backing tier and repeat-on-miss state.

`GameplaySession.advanceTo(nowMs)` finalizes every crossed bar. This matters
after a slow frame: no transition is lost merely because the display skipped a
boundary. The returned `SessionAdvance` lets Compose trigger presentation-only
motion without duplicating the rules.

## Tap judgment

Pointer input records `SystemClock.elapsedRealtime`, subtracts the configured
input compensation, and asks the session for the nearest expected hit across
the current and adjacent bars. The windows are:

| Judgment | Maximum distance |
| --- | ---: |
| Perfect | 35 ms |
| Good | 70 ms |
| Close | 120 ms |
| Miss | greater than 120 ms |

Already-finalized bars are excluded. A set of hit indices prevents duplicate
taps from satisfying multiple required attacks. `patternForJudgmentBar` also
returns the correct phase across a bar boundary, so custom pitch playback does
not accidentally use the old phase.

## Intro and lifecycle

The intro animation computes the future start timestamp before it moves the
queue. The audio conductor may schedule against that future timestamp while
the UI displays the countdown. On `ON_STOP`, the clock pauses and audio stops;
on `ON_START`, the origin shifts by the pause duration so the song does not jump
forward while backgrounded.

## Safe game-loop changes

- Add or change a rule in `GameplaySession` or a pure helper first, with a host
  test. Let the screen translate the result into animation or sound.
- Keep all clocks in the `elapsedRealtime` timebase. Do not mix wall time,
  `currentTimeMillis`, and audio timestamps.
- Use frame callbacks for visual sampling, not `delay(16)`.
- Do not schedule precise audio by sleeping until a beat; author a timestamped
  `AudioEvent` ahead of time.
- Handle multiple crossed bars, not only the immediately previous bar.
- Check adjacent-bar tap behavior whenever phase-transition logic changes.

Run focused and full tests after changes:

```powershell
.\tools\gradle.ps1 testDebugUnitTest
```

Then play through a phase transition, a miss, background/foreground, and a
deliberately slow frame on a physical device.
