# Beat Phaser documentation

Read these in order for a first contribution:

1. [Architecture](ARCHITECTURE.md) — ownership, dependencies, and source map.
2. [Game loop](GAME_LOOP.md) — clock, phasing, queue, tap judgment, and safe
   timing changes.
3. Choose the subsystem you are changing:
   [audio](AUDIO.md) or [graphics](GRAPHICS.md).

Operational references:

- [Backing-track editor](BACKING_TRACK_EDITOR.md)
- [Pitched-sample importer](PITCHED_SAMPLE_IMPORTER.md)
- [Third-party audio provenance](THIRD_PARTY_AUDIO.md)

The root [README](../README.md) contains setup, commands, and the short web-to-
Android mental model. Documentation should describe current code, not a handoff
snapshot. Update the relevant subsystem page in the same change as a contract,
workflow, or source-path change.
