# mc_music

**MIDI → Minecraft Java Edition note-block datapack compiler.**

The intended workflow is simple: give the tool a MIDI file; it imports the notes, assigns note-block instruments and pitches, lays out the blocks, and writes a datapack containing scheduled functions. In the world, one command block (or a manual function command) starts playback.

## Current architecture

```text
MIDI file ──> midi.py ──> Score / Part / Measure / Note
                              │
                              ▼
                    ties.py: resolve_ties()
                              │
                              ▼
                timing.py: Fraction → PPQ → game tick
                              │
                              ▼
               noteblock.py: layout + tick buckets
                              │
                              ▼
                  datapack (.mcfunction files)
                              │
                   /function mc_music:music/start
                              │
             setblock redstone_block → note-block sound
```

- `core.py`: immutable musical model; all musical times are `Fraction` quarter-note beats.
- `midi.py`: Standard MIDI File importer. MIDI PPQN ticks are converted exactly to `Fraction` beats.
- `ties.py`: `NoteRef`, `Tie`, and `resolve_ties()` merge contiguous same-pitch segments.
- `timing.py`: exact beat/PPQ conversion and final half-up rounding to Minecraft game ticks.
- `matrix.py`: NumPy structure-of-arrays representation for batch note operations.
- `noteblock.py`: physical layout, pitch mapping, tick scheduling, and datapack generation.

## Install

Python 3.10 or later:

```powershell
py -m venv .venv
.venv\Scripts\Activate.ps1
python -m pip install -e .
```

## Convert a MIDI file

For Java Edition 26.3 (data-pack format 121.0):

```powershell
mc-music .\song.mid --output .\song_datapack
```

Optional parameters:

```powershell
mc-music .\song.mid -o .\song_datapack --origin 100 64 100 --pitch-mode octave_fold
```

Pitch modes:
- `strict` (default): reject pitches outside MIDI 54–78, the 25-note range of a vanilla note block.
- `octave_fold`: move pitches by octaves into the supported range while preserving pitch class.
- `clip`: clamp out-of-range pitches to the lowest/highest supported note.

The default datapack format is 121.0 for Java 26.3. Use `--pack-format` only when targeting another Java version.

## Install and play in Minecraft

1. Copy the generated datapack folder (for example `song_datapack`) into:
   `<world>/datapacks/`.
2. Enter the world and run `/reload`.
3. Run `/function mc_music:music/start`.
4. To use a command block, put that exact function command in an impulse command block and trigger it.

The generated setup function places the instrument base blocks and note blocks at the selected origin. The note block is at `(x, y+1, z)`; its instrument base is at `(x, y, z)`; the temporary redstone control position is at `(x+1, y+1, z)`. Columns are spaced apart to prevent control blocks from powering neighboring note blocks.

**Important:** choose an empty build area. The setup function uses `/setblock ... replace` and will overwrite blocks at generated coordinates.

## Playback model and known limits

- No redstone wiring is generated. A temporary redstone block is placed beside a note block, then removed one game tick later.
- The function chain is driven by `schedule function`; the command block only starts the datapack.
- Notes that land on the same game tick and require the same physical note block are rejected as a same-tick collision.
- Vanilla note blocks have only 25 pitches (MIDI 54–78 under this mapping). Strict mode is intentionally the default.
- Vanilla note blocks do not expose MIDI velocity. Non-default velocities are reported as warnings and are not rendered.
- Note-block sounds are one-shot/percussive; MIDI note duration is retained in the score model but cannot be sustained by a vanilla note block.
- The current MIDI importer supports PPQN files with one constant tempo and one constant time signature. Tempo/time-signature changes and SMPTE-timed MIDI are rejected explicitly rather than silently misinterpreted.
- GM programs are reduced to the project's six instrument families; some timbres are approximate. MIDI channel 10 percussion is skipped by default.

## Python API

```python
from mc_music import load_midi, compile_to_noteblock, write_datapack

imported = load_midi("song.mid")
compiled = compile_to_noteblock(
    imported.score,
    origin=(100, 64, 100),
    pitch_mode="strict",
)
write_datapack(compiled, "song_datapack")  # Java 26.3 format 121.0 by default
```

For a hand-authored score, use `Note`, `Measure`, `Part`, and `Score` from `mc_music.core`.

## Development

```powershell
python -m pip install -e ".[dev]"
pytest
```

The project is being developed on the `midi-to-noteblock-datapack` branch. The original `numpy-ppq-tie-layer` branch and `main` are not modified by this work.
