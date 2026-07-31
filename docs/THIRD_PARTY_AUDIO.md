# Third-Party Audio

## VCSL Steinway B Grand Piano

The pitched piano tones in `app/src/main/res/raw` are derived from:

- project: Versilian Community Sample Library (VCSL);
- instrument: Grand Piano, Steinway B;
- source: https://github.com/sgossner/VCSL/tree/sfz/Chordophones/Zithers/Grand%20Piano%2C%20Steinway%20B;
- source revision: `dfcf4a4918771eee884b96ad4493de82ef84daf6`;
- source selection: close microphone, non-sustain, medium velocity (`vl3`);
- license: Creative Commons Zero v1.0 Universal (CC0-1.0).

VCSL describes the collection as public-domain-equivalent and permits use in
commercial software without royalties, attribution, or special terms. This
file retains provenance even though attribution is not required.

The source recordings were downmixed, resampled, gain-balanced using the VCSL
SFZ region values, optionally pitch-shifted by one semitone, trimmed, faded,
limited, and encoded as 48 kHz mono PCM-16 WAVs. The reproducible transformation
is defined by `tools/import-pitched-samples.ps1` and
`tools/pitched-sample-packs/vcsl-steinway-b.json`.

## VCSL Marimba

The selectable marimba tones in `app/src/main/res/raw` are derived from:

- project: Versilian Community Sample Library (VCSL);
- instrument: Marimba;
- source: https://github.com/sgossner/VCSL/tree/sfz/Idiophones/Struck%20Idiophones/Marimba;
- source revision: `dfcf4a4918771eee884b96ad4493de82ef84daf6`;
- source selection: outrigger microphone, medium velocity;
- license: Creative Commons Zero v1.0 Universal (CC0-1.0).

VCSL's repository states that its CC0 collection may be used in commercial
software without royalties, attribution, or special terms. This provenance is
retained even though attribution is not required.

The source recordings were downmixed, resampled, gain-balanced using the VCSL
SFZ region values, pitch-shifted to fill the game's chromatic ranges, trimmed,
faded, limited, and encoded as 48 kHz mono PCM-16 WAVs. The reproducible
transformation is defined by `tools/import-pitched-samples.ps1` and
`tools/pitched-sample-packs/vcsl-marimba.json`.

## VCSL Vibraphone

The selectable vibraphone tones in `app/src/main/res/raw` are derived from:

- project: Versilian Community Sample Library (VCSL);
- instrument: Vibraphone;
- source: https://github.com/sgossner/VCSL/tree/sfz/Idiophones/Struck%20Idiophones/Vibraphone;
- source revision: `dfcf4a4918771eee884b96ad4493de82ef84daf6`;
- source selection: hard mallets, `v2` velocity, main microphone, round robin 1;
- license: Creative Commons Zero v1.0 Universal (CC0-1.0).

The source instrument begins above part of the game's Base register. Its
right-hand bank follows the authoritative SFZ regions, while the lowest
left-hand samples extend the closest recorded hard-mallet note downward through
sampler-style pitch shifting. The transformation is defined by
`tools/import-pitched-samples.ps1` and
`tools/pitched-sample-packs/vcsl-vibraphone.json`.

## VCSL Flemish Harpsichord

The selectable harpsichord tones in `app/src/main/res/raw` are derived from:

- project: Versilian Community Sample Library (VCSL);
- instrument: Flemish Harpsichord;
- source: https://github.com/sgossner/VCSL/tree/sfz/Chordophones/Zithers/Harpsichord%2C%20Flemish;
- source revision: `dfcf4a4918771eee884b96ad4493de82ef84daf6`;
- source selection: 8′ stop, sustain attacks, low/far microphone, round robin 1;
- license: Creative Commons Zero v1.0 Universal (CC0-1.0).

The 8′ stop preserves normal concert register. Exact recorded notes and the
SFZ-defined one-semitone regions fill both chromatic hand ranges. Release
trigger samples are not used by the game's fixed-duration one-shot renderer.
The transformation is defined by `tools/import-pitched-samples.ps1` and
`tools/pitched-sample-packs/vcsl-flemish-harpsichord.json`.

All four VCSL banks use the repository-wide CC0 dedication, which permits
commercial software use without royalties, attribution, or special terms.

## FreePats Distorted Electric Guitar #1

The selectable distorted-guitar tones in `app/src/main/res/raw` are derived
from:

- project: FreePats;
- instrument: FSBS Electric Guitar Distorted #1;
- source: https://freepats.zenvoid.org/ElectricGuitar/distorted-electric-guitar.html;
- source version: `2022-09-11`;
- source archive: `EGuitarFSBS-bridge-dist1-SFZ+FLAC-20220911.7z`;
- source archive SHA-256: `91746C8CE32E1FEE9AF0618503F83CD805D3638C180EAC521810F7487F115185`;
- source selection: bridge pickup, louder velocity layer, round robin 1;
- license: Creative Commons Zero v1.0 Universal (CC0-1.0).

FreePats describes the source as a directly sampled Fender-style electric
guitar processed through an amplifier and effects rack for a distorted,
overdriven sound. The game follows the supplied SFZ pitch regions across its
entire C2..B5 range. The lossless FLAC sources are resampled, pitch-mapped,
gain-balanced, trimmed, faded, limited without automatic makeup gain, and
encoded as 48 kHz mono PCM-16 WAVs. The reproducible transformation is defined
by `tools/import-pitched-samples.ps1` and
`tools/pitched-sample-packs/freepats-distorted-guitar-1.json`.
