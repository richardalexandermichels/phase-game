# Pitched Sample Importer

`tools/import-pitched-samples.ps1` converts a multisampled instrument into the
fixed pitched assets used by gameplay and Design Mode. It stages and validates
the entire pack before replacing any game WAV.

## Installed Packs

The Piano, Marimba, Vibraphone, and Flemish Harpsichord banks come from the CC0
Versilian Community Sample Library (VCSL). The Distorted Guitar bank comes from
the CC0 FreePats collection.

`vcsl-steinway-b.json` uses:

- close microphone;
- non-sustain samples;
- medium velocity layer (`vl3`);
- VCSL `sfz` revision `dfcf4a4918771eee884b96ad4493de82ef84daf6`;
- CC0-1.0 license.

VCSL records this instrument in whole tones. The manifest uses its SFZ region
mapping: exact recordings where available and a one-semitone sampler-style
pitch shift for intervening notes.

`vcsl-marimba.json` uses the Marimba outrigger-microphone medium-velocity
recordings. Its SFZ mapping provides recorded anchor notes across F1..C6. The
manifest uses the nearest mapped anchor to fill Base C2..B3 and Player C4..B5
chromatically.

`vcsl-vibraphone.json` uses hard-mallet `v2` samples from the main microphone.
The Player range follows the SFZ map closely. Because the recorded instrument
starts above much of Base, its lowest anchor is extended downward to retain the
game's fixed C2..B3 left-hand register.

`vcsl-flemish-harpsichord.json` uses sustain attacks from the Flemish 8′ stop,
low/far microphone, and round robin 1. The SFZ provides exact/every-other-note
coverage across both game ranges.

`freepats-distorted-guitar-1.json` uses FreePats FSBS Electric Guitar Distorted
#1, bridge pickup, louder velocity layer, and round robin 1. Its 18 recorded
anchors and supplied SFZ regions cover the complete MIDI 36..83 game range.

## Acquire the Source

Use a partial, sparse clone so Git fetches only the selected medium-velocity,
non-sustain layer instead of the complete VCSL repository:

```powershell
git clone --depth 1 --branch sfz --filter=blob:none --no-checkout `
  https://github.com/sgossner/VCSL.git C:\path\to\VCSL
cd C:\path\to\VCSL
git sparse-checkout init --no-cone
git sparse-checkout set --no-cone `
  '/Chordophones/Zithers/Grand Piano, Steinway B/NoSus/*_vl3_rr1.wav' `
  '/Chordophones/Zithers/Grand Piano, Steinway B.sfz'
git checkout sfz
```

The sparse patterns download only the medium, non-sustain source layer required
by the manifest plus the authoritative SFZ mapping.

For Marimba, use these sparse paths instead:

```powershell
git sparse-checkout set --no-cone `
  '/Idiophones/Struck Idiophones/Marimba/*_med_01.wav' `
  '/Idiophones/Struck Idiophones/Marimba.sfz' `
  '/README.md'
git checkout sfz
```

For Vibraphone and Flemish Harpsichord, use:

```powershell
git sparse-checkout set --no-cone `
  '/Idiophones/Struck Idiophones/Vibraphone/Hard Mallets/*_v2_rr1_Main.wav' `
  '/Idiophones/Struck Idiophones/Vibraphone - Hard Mallets.sfz' `
  '/Chordophones/Zithers/Harpsichord, Flemish/Sustains/Low/*_rr1.wav' `
  '/Chordophones/Zithers/Harpsichord, Flemish - 8''.sfz' `
  '/README.md'
git checkout sfz
```

For FreePats Distorted Guitar #1, download and extract the lossless SFZ/FLAC
archive:

```powershell
curl.exe -L --fail -o EGuitarFSBS-bridge-dist1-SFZ-FLAC-20220911.7z `
  'https://freepats.zenvoid.org/ElectricGuitar/FSBS-EGuitar/EGuitarFSBS-bridge-dist1-SFZ%2BFLAC-20220911.7z'
```

## Import

Run from the PhaseGame repository root:

```powershell
.\tools\import-pitched-samples.ps1 `
  -SourceRoot 'C:\path\to\VCSL\Chordophones\Zithers\Grand Piano, Steinway B'
```

If local PowerShell execution policy blocks scripts, invoke it with:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\tools\import-pitched-samples.ps1 `
  -SourceRoot 'C:\path\to\VCSL\Chordophones\Zithers\Grand Piano, Steinway B'
```

If FFmpeg is not on `PATH`, pass its location explicitly:

```powershell
.\tools\import-pitched-samples.ps1 `
  -SourceRoot 'C:\path\to\VCSL\Chordophones\Zithers\Grand Piano, Steinway B' `
  -FfmpegPath 'C:\path\to\ffmpeg.exe'
```

To render the separately named Marimba bank:

```powershell
.\tools\import-pitched-samples.ps1 `
  -SourceRoot 'C:\path\to\VCSL\Idiophones\Struck Idiophones\Marimba' `
  -ManifestPath '.\tools\pitched-sample-packs\vcsl-marimba.json'
```

To render Vibraphone:

```powershell
.\tools\import-pitched-samples.ps1 `
  -SourceRoot 'C:\path\to\VCSL\Idiophones\Struck Idiophones\Vibraphone\Hard Mallets' `
  -ManifestPath '.\tools\pitched-sample-packs\vcsl-vibraphone.json'
```

To render Flemish Harpsichord:

```powershell
.\tools\import-pitched-samples.ps1 `
  -SourceRoot 'C:\path\to\VCSL\Chordophones\Zithers\Harpsichord, Flemish\Sustains\Low' `
  -ManifestPath '.\tools\pitched-sample-packs\vcsl-flemish-harpsichord.json'
```

To render FreePats Distorted Guitar #1:

```powershell
.\tools\import-pitched-samples.ps1 `
  -SourceRoot 'C:\path\to\EGuitarFSBS-bridge-dist1-SFZ+FLAC-20220911\samples' `
  -ManifestPath '.\tools\pitched-sample-packs\freepats-distorted-guitar-1.json'
```

The importer produces 850 ms, 48 kHz, mono, signed PCM-16 WAVs with a 200 ms
tail fade. The Steinway manifest updates its 48-note bank plus legacy built-in
pitched tones. Each other manifest updates its own separate 48-note bank. Miss
and percussion sounds are untouched.

To adapt another instrument, copy
`tools/pitched-sample-packs/vcsl-steinway-b.json`, update its source paths and
MIDI mappings, and pass the new file with `-ManifestPath`. Android raw-resource
filenames and the current pitch-row order must remain unchanged unless the
Kotlin catalog and Design UI are changed at the same time.

A manifest may set `globalGainDb` to balance the whole bank while preserving
the per-sample SFZ gains. The Vibraphone and Flemish Harpsichord manifests use
this to sit near the existing Piano bank's peak range.

Set `limiterAutoLevel` to `false` for already-mastered sources that need peak
limiting without FFmpeg's automatic makeup gain. It defaults to `true` so the
existing VCSL manifests retain their original rendering behavior.

## Validation

The importer refuses to install a partial pack. Every staged output must be:

- `pcm_s16le`;
- mono;
- 48 kHz;
- 16-bit;
- the manifest's expected duration.

After importing, run the normal audio validation and audition Base, Player,
Design pitches, chords, and dense 250 ms patterns on a device.

```powershell
.\tools\gradle.ps1 testDebugUnitTest assembleDebug lintDebug
```
