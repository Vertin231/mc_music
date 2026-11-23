"""Compile Scores into Minecraft Java Edition note-block datapacks.

Playback uses a temporary redstone block beside each note block. A one-tick
pulse is emitted by scheduled functions; no redstone wiring is generated.
"""
from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
import json
import re

from .core import Instrument, Score
from .ties import Tie, resolve_ties
from .timing import beats_to_game_ticks

NOTE_BLOCK_INSTRUMENT = {
    Instrument.PIANO: "harp",
    Instrument.VIOLIN: "harp",
    Instrument.FLUTE: "flute",
    Instrument.GUITAR: "guitar",
    Instrument.BASS: "bass",
    Instrument.BELL: "bell",
}
INSTRUMENT_BASE_BLOCK = {
    "harp": "dirt",
    "bass": "oak_planks",
    "bell": "gold_block",
    "flute": "clay",
    "guitar": "white_wool",
}
MIDI_NOTE_MIN = 54  # F#3, note-block note property 0
MIDI_NOTE_MAX = 78  # F#5, note-block note property 24


@dataclass(frozen=True, slots=True)
class NoteBlockPlacement:
    instrument: Instrument
    midi: int
    note: int
    note_block_pos: tuple[int, int, int]
    base_pos: tuple[int, int, int]
    control_pos: tuple[int, int, int]


@dataclass(frozen=True, slots=True)
class NoteBlockOutput:
    placements: tuple[NoteBlockPlacement, ...]
    schedule: dict[int, tuple[NoteBlockPlacement, ...]]
    warnings: tuple[str, ...]
    files: dict[str, str]


def to_note_block_note(midi: int, pitch_mode: str = "strict") -> int:
    """Map MIDI pitch to vanilla note-block pitch (0..24)."""
    if pitch_mode not in {"strict", "octave_fold", "clip"}:
        raise ValueError("pitch_mode must be 'strict', 'octave_fold', or 'clip'")
    note = int(midi) - MIDI_NOTE_MIN
    if 0 <= note <= 24:
        return note
    if pitch_mode == "strict":
        raise ValueError(
            f"MIDI pitch {midi} is outside the vanilla note-block range "
            f"{MIDI_NOTE_MIN}..{MIDI_NOTE_MAX}; choose octave_fold or clip explicitly"
        )
    if pitch_mode == "clip":
        return min(24, max(0, note))
    while note < 0:
        note += 12
    while note > 24:
        note -= 12
    return note


def _add(a, b):
    return tuple(x + y for x, y in zip(a, b))


def _pos_text(pos):
    return " ".join(str(v) for v in pos)


def _function_path(tick: int) -> str:
    return f"music/tick_{tick:08d}.mcfunction"


def compile_to_noteblock(
    score: Score,
    *,
    origin: tuple[int, int, int] = (0, 64, 0),
    namespace: str = "mc_music",
    pitch_mode: str = "strict",
    ties_by_part_index: dict[int, tuple[Tie, ...]] | None = None,
    spacing: int = 3,
) -> NoteBlockOutput:
    """Compile a score to datapack function sources.

    Each (instrument, MIDI pitch) owns one note block. The note block sits one
    block above its instrument base; the temporary redstone block is placed
    beside it. The control position is cleared one game tick after each attack.
    """
    if not re.fullmatch(r"[a-z0-9_.-]+", namespace):
        raise ValueError("namespace must contain lowercase letters, digits, '_', '.', or '-'")
    if len(origin) != 3 or any(isinstance(v, bool) or not isinstance(v, int) for v in origin):
        raise TypeError("origin must be a tuple of three integers")
    if isinstance(spacing, bool) or not isinstance(spacing, int) or spacing < 2:
        raise ValueError("spacing must be an integer >= 2")

    ties_by_part_index = ties_by_part_index or {}
    events = []
    warnings = []
    for part_index, part in enumerate(score.parts):
        resolved = resolve_ties(part, ties_by_part_index.get(part_index, ()))
        for event in resolved:
            tick = beats_to_game_ticks(event.start, score.bpm)
            note = to_note_block_note(event.midi, pitch_mode)
            events.append((tick, part.instrument, event.midi, note, event.velocity))

    if not events:
        raise ValueError("score contains no notes")

    combos = sorted({(instrument, midi, note) for _, instrument, midi, note, _ in events},
                    key=lambda item: (int(item[0]), item[1]))
    ox, oy, oz = origin
    placements = []
    by_combo = {}
    for index, (instrument, midi, note) in enumerate(combos):
        sound = NOTE_BLOCK_INSTRUMENT[instrument]
        x = ox + index * spacing
        base = (x, oy, oz)
        note_pos = (x, oy + 1, oz)
        control = (x + 1, oy + 1, oz)
        placement = NoteBlockPlacement(instrument, midi, note, note_pos, base, control)
        placements.append(placement)
        by_combo[(instrument, midi, note)] = placement

    # Each attack has an ON action and a matching OFF one tick later.
    actions = defaultdict(lambda: {"off": set(), "on": set()})
    seen_attacks = set()
    for tick, instrument, midi, note, velocity in events:
        placement = by_combo[(instrument, midi, note)]
        key = (tick, placement.control_pos)
        if key in seen_attacks:
            raise ValueError(
                f"same-tick collision: instrument={instrument.name}, MIDI={midi}, tick={tick}"
            )
        seen_attacks.add(key)
        actions[tick]["on"].add(placement)
        actions[tick + 1]["off"].add(placement)
        if velocity != 100:
            warnings.append(
                f"velocity {velocity} is not represented by vanilla note blocks "
                f"(instrument={instrument.name}, MIDI={midi}, tick={tick})"
            )

    ordered_ticks = sorted(actions)
    functions = {}
    for index, tick in enumerate(ordered_ticks):
        lines = []
        current = actions[tick]
        for placement in sorted(current["off"], key=lambda p: p.control_pos):
            lines.append(f"setblock {_pos_text(placement.control_pos)} minecraft:air replace")
        for placement in sorted(current["on"], key=lambda p: p.control_pos):
            lines.append(f"setblock {_pos_text(placement.control_pos)} minecraft:redstone_block replace")
        if index + 1 < len(ordered_ticks):
            next_tick = ordered_ticks[index + 1]
            delta = next_tick - tick
            next_path = _function_path(next_tick).removesuffix(".mcfunction").replace("/", ":")
            lines.append(f"schedule function {namespace}:{next_path} {delta}t append")
        functions[_function_path(tick)] = "\n".join(lines) + "\n"

    setup_lines = []
    for placement in placements:
        sound = NOTE_BLOCK_INSTRUMENT[placement.instrument]
        base_block = INSTRUMENT_BASE_BLOCK[sound]
        setup_lines.append(f"setblock {_pos_text(placement.base_pos)} minecraft:{base_block} replace")
        setup_lines.append(
            f"setblock {_pos_text(placement.note_block_pos)} "
            f"minecraft:note_block[instrument={sound},note={placement.note},powered=false] replace"
        )
        setup_lines.append(f"setblock {_pos_text(placement.control_pos)} minecraft:air replace")

    first_tick = ordered_ticks[0]
    first_path = _function_path(first_tick).removesuffix(".mcfunction").replace("/", ":")
    start_lines = ["function " + namespace + ":music/setup"]
    if first_tick == 0:
        start_lines.append(f"function {namespace}:{first_path}")
    else:
        start_lines.append(f"schedule function {namespace}:{first_path} {first_tick}t replace")

    files = {
        "data/minecraft/tags/functions/load.json": json.dumps(
            {"values": []}, indent=2
        ) + "\n",
        f"data/{namespace}/functions/music/setup.mcfunction": "\n".join(setup_lines) + "\n",
        f"data/{namespace}/functions/music/start.mcfunction": "\n".join(start_lines) + "\n",
    }
    for path, source in functions.items():
        files[f"data/{namespace}/functions/{path}"] = source

    schedule = {
        tick: tuple(sorted(actions[tick]["on"], key=lambda p: (p.instrument.value, p.midi)))
        for tick in ordered_ticks if actions[tick]["on"]
    }
    return NoteBlockOutput(tuple(placements), schedule, tuple(dict.fromkeys(warnings)), files)


def write_datapack(
    output: NoteBlockOutput,
    destination: str | Path,
    *,
    pack_format: int,
    description: str = "Generated by mc_music",
) -> Path:
    """Write a compiled datapack directory. Zip it separately if desired."""
    if isinstance(pack_format, bool) or not isinstance(pack_format, int) or pack_format <= 0:
        raise ValueError("pack_format must be a positive integer for the target Java version")
    root = Path(destination)
    root.mkdir(parents=True, exist_ok=True)
    meta = {
        "pack": {
            "pack_format": pack_format,
            "description": description,
        }
    }
    (root / "pack.mcmeta").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    for relative, source in output.files.items():
        target = root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source, encoding="utf-8")
    return root


def midi_to_datapack(
    midi_path: str | Path,
    destination: str | Path,
    *,
    pack_format: int,
    origin: tuple[int, int, int] = (0, 64, 0),
    namespace: str = "mc_music",
    pitch_mode: str = "strict",
):
    """One-call pipeline: MIDI file -> Score -> datapack folder."""
    from .midi import load_midi

    imported = load_midi(midi_path)
    compiled = compile_to_noteblock(
        imported.score, origin=origin, namespace=namespace, pitch_mode=pitch_mode
    )
    path = write_datapack(compiled, destination, pack_format=pack_format)
    return imported, compiled, path
