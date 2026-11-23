# mc_music

A Python-based music data model and compiler for Minecraft.

The project is being developed incrementally. The intended workflow is to represent music in Python, then translate the score into Minecraft playback instructions or structures.

## Planned targets

- **Redstone**: generate note-block/redstone arrangements.
- **Command**: generate command-based playback.

## Current development

The core data model is being designed around:

- `Note`: a note event with rational beat position and duration, MIDI pitch, and velocity.
- `Measure`: a measure containing notes.
- `Part`: an instrument part containing measures and supporting absolute-time iteration.
- `Score`: the complete score, containing tempo and multiple parts.

The next step is to bring the current, tested Python implementation into the repository and continue building the note-input interface.

## Development approach

This is a learning project: the implementation is developed step by step, with an emphasis on understanding the data model, writing tests, and keeping each change small and reviewable.
