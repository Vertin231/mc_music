# mc_music

A Python-based music data model and compiler for Minecraft.

The project is being developed incrementally. The intended workflow is to represent music in Python, then translate the score into Minecraft playback instructions or structures.

## Planned targets

- **Redstone**: generate note-block/redstone arrangements.
- **Command**: generate command-based playback.

## Current data model

- `Note`: a note event with measure-relative rational beat position and duration, MIDI pitch, and velocity.
- `Measure`: a measure containing notes, sorted deterministically by onset and pitch.
- `Part`: an instrument part containing measures and supporting absolute-time iteration.
- `Score`: the complete score, containing tempo and multiple parts.

Time positions and durations use `Fraction`, measured in quarter-note units (beats). Floats are intentionally rejected for note timing.

## Recent core improvements (review branch)

- Integer validation now accepts integer-like values implementing `__index__` (including NumPy integer scalars), while rejecting floats and booleans.
- `midi` and `velocity` are normalized to Python integers.
- Added `Score.absolute_events()`, a flat, sorted event view for future compilation.
- Added a provisional instrument-to-note-block mapping table.
- Expanded docstrings to clarify velocity, measure units, and Part/Score validation boundaries.

## Planned next steps

- Define and implement cross-measure ties/hold semantics. This is not implemented yet; the current `Measure` invariant requires notes to end within their measure.
- Build a dedicated compilation layer for tick conversion and Minecraft command/redstone output.
- Add tests for the data model and the new integer-like validation behavior.

## Development approach

This is a learning project: the implementation is developed step by step, with an emphasis on understanding the data model, writing tests, and keeping each change small and reviewable.
