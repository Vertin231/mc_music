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


## NumPy and PPQ layer

- `timing.py`: exact `Fraction` to PPQ conversion and final Minecraft tick rounding. Default PPQ is 960.
- `matrix.py`: `NoteMatrix` stores onset/duration as int64 PPQ ticks and pitch/velocity/instrument as compact arrays; arrays are read-only. Includes sorting, transposition, velocity scaling, onset quantization, onset grouping, and conversion to game ticks.
- `ties.py`: `NoteRef`, `Tie`, and `resolve_ties()` merge contiguous same-pitch note segments in a Part. Tie resolution happens before PPQ/NumPy conversion.
- `Measure` allows note durations to extend past a barline, while each onset must lie within its own measure. This supports sustained events crossing barlines.
- `Part.to_matrix(ppq=960, ties=...)` and `Score.to_matrix(ppq=960, ties_by_part=...)` convert to the event matrix.

### PPQ behavior

Time remains measured in quarter-note units. PPQ is pulses per quarter note; 960 is the default. Fraction conversion must be exact or raises `ValueError`. Conversion from PPQ to Minecraft ticks is a final approximation using round-half-up. Quantization rounds onset to the nearest grid, then applies strength.

This is an initial implementation; MIDI import, user-friendly tie input parsing, and backend compiler output remain future work.
