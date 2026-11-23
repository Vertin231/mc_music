"""Compile Scores into Minecraft Java Edition note-block datapacks."""
from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
import json
import operator
import re

from .core import Instrument, Score
from .matrix import score_to_matrix
from .ties import Tie
from .timing import PPQ_DEFAULT

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
MIDI_NOTE_MIN = 54
MIDI_NOTE_MAX = 78


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
    if isinstance(midi, bool):
        raise TypeError("midi must be an integer")
    try:
        midi = operator.index(midi)
    except TypeError:
        raise TypeError("midi must be an integer") from None
    if not 0 <= midi <= 127:
        raise ValueError("midi must be in 0..127")
    if pitch_mode not in {"strict", "octave_fold", "clip"}:
        raise ValueError("pitch_mode must be 'strict', 'octave_fold', or 'clip'")
    note = midi - MIDI_NOTE_MIN
    if 0 <= note <= 24:
        return int(note)
    if pitch_mode == "strict":
        raise ValueError(
            f"MIDI pitch {midi} is outside the vanilla note-block range "
            f"{MIDI_NOTE_MIN}..{MIDI_NOTE_MAX}; choose octave_fold or clip explicitly"
        )
    if pitch_mode == "clip":
        return int(min(24, max(0, note)))
    while note < 0:
        note += 12
    while note > 24:
        note -= 12
    return int(note)


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
    ppq: int = PPQ_DEFAULT,
    ties_by_part_index: dict[int, tuple[Tie, ...]] | None = None,
    spacing: int = 3,
) -> NoteBlockOutput:
    """Resolve ties -> exact PPQ matrix -> integer game-tick buckets -> functions."""
    if not re.fullmatch(r"[a-z0-9_.-]+", namespace):
        raise ValueError("namespace must contain lowercase letters, digits, '_', '.', or '-'")
    if (not isinstance(origin, tuple) or len(origin) != 3 or
            any(isinstance(v, bool) or not isinstance(v, int) for v in origin)):
        raise TypeError("origin must be a tuple of three integers")
    if isinstance(spacing, bool) or not isinstance(spacing, int) or spacing < 3:
        raise ValueError("spacing must be an integer >= 3")
    ties_by_part_index = ties_by_part_index or {}

    # This is the precision gate: any Fraction not exactly representable at PPQ
    # fails here instead of being silently rounded before game-tick conversion.
    matrix = score_to_matrix(score, ppq=ppq, ties_by_part_index=ties_by_part_index)
    start_ticks, _duration_ticks = matrix.to_game_ticks(score.bpm)
    events = []
    warnings = []
    for i in range(len(matrix)):
        instrument = Instrument(int(matrix.instrument[i]))
        midi = int(matrix.midi[i])
        velocity = int(matrix.velocity[i])
        note = to_note_block_note(midi, pitch_mode)
        events.append((int(start_ticks[i]), instrument, midi, note, velocity))

    if not events:
        raise ValueError("score contains no notes")

    combos = sorted(
        {(instrument, midi, note) for _, instrument, midi, note, _ in events},
        key=lambda item: (int(item[0]), item[1]),
    )
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
        # OFF first lets the same note be retriggered on consecutive ticks.
        for placement in sorted(current["off"], key=lambda p: p.control_pos):
            lines.append(f"setblock {_pos_text(placement.control_pos)} minecraft:air replace")
        for placement in sorted(current["on"], key=lambda p: p.control_pos):
            lines.append(f"setblock {_pos_text(placement.control_pos)} minecraft:redstone_block replace")
        if index + 1 < len(ordered_ticks):
            next_tick = ordered_ticks[index + 1]
            delta = next_tick - tick
            next_path = _function_path(next_tick).removesuffix(".mcfunction")
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
    first_path = _function_path(first_tick).removesuffix(".mcfunction")
    start_lines = [f"function {namespace}:music/setup"]
    if first_tick == 0:
        start_lines.append(f"function {namespace}:{first_path}")
    else:
        start_lines.append(f"schedule function {namespace}:{first_path} {first_tick}t replace")

    files = {
        f"data/{namespace}/function/music/setup.mcfunction": "\n".join(setup_lines) + "\n",
        f"data/{namespace}/function/music/start.mcfunction": "\n".join(start_lines) + "\n",
    }
    for path, source in functions.items():
        files[f"data/{namespace}/function/{path}"] = source

    schedule = {
        tick: tuple(sorted(actions[tick]["on"], key=lambda p: (p.instrument.value, p.midi)))
        for tick in ordered_ticks if actions[tick]["on"]
    }
    return NoteBlockOutput(tuple(placements), schedule, tuple(dict.fromkeys(warnings)), files)


def write_datapack(
    output: NoteBlockOutput,
    destination: str | Path,
    *,
    pack_format: int = 121,
    description: str = "Generated by mc_music",
) -> Path:
    """Write a datapack directory; default data-pack format 121.0 targets Java 26.3."""
    if isinstance(pack_format, bool) or not isinstance(pack_format, int) or pack_format <= 0:
        raise ValueError("pack_format must be a positive integer")
    root = Path(destination)
    root.mkdir(parents=True, exist_ok=True)
    meta = {
        "pack": {
            "description": description,
            "min_format": [pack_format, 0],
            "max_format": pack_format,
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
    pack_format: int = 121,
    ppq: int = PPQ_DEFAULT,
    origin: tuple[int, int, int] = (0, 64, 0),
    namespace: str = "mc_music",
    pitch_mode: str = "strict",
):
    """One-call pipeline: MIDI file -> Score -> exact PPQ -> datapack folder."""
    from .midi import load_midi

    imported = load_midi(midi_path)
    compiled = compile_to_noteblock(
        imported.score, origin=origin, namespace=namespace, pitch_mode=pitch_mode, ppq=ppq
    )
    path = write_datapack(compiled, destination, pack_format=pack_format)
    return imported, compiled, path
