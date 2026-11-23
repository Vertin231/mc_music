"""Standard MIDI File importer (PPQN timing, constant tempo/time signature)."""
from __future__ import annotations

from collections import defaultdict, deque
from dataclasses import dataclass
from fractions import Fraction
from pathlib import Path

import mido

from .core import Instrument, Measure, Note, Part, Score


@dataclass(frozen=True, slots=True)
class MidiImportResult:
    score: Score
    warnings: tuple[str, ...] = ()


def _gm_family(program: int) -> Instrument:
    """Collapse the 128 General MIDI programs into the six supported families."""
    if 0 <= program <= 7:
        return Instrument.PIANO
    if 8 <= program <= 15:
        return Instrument.BELL
    if 24 <= program <= 31:
        return Instrument.GUITAR
    if 32 <= program <= 39:
        return Instrument.BASS
    if 40 <= program <= 47:
        return Instrument.VIOLIN
    if 73 <= program <= 79:
        return Instrument.FLUTE
    # Preserve a usable output instead of silently dropping unsupported timbres.
    return Instrument.PIANO


def load_midi(path: str | Path, *, include_drums: bool = False) -> MidiImportResult:
    """Read a PPQN MIDI file and return a Score plus import warnings.

    Tempo and time signature changes after tick zero are rejected because Score
    currently models one global BPM and one measure structure. Musical positions
    remain exact Fractions of quarter-note beats.
    """
    midi_file = mido.MidiFile(filename=str(path))
    if midi_file.type == 2:
        raise ValueError("MIDI type 2 (asynchronous patterns) is not supported")
    if midi_file.ticks_per_beat <= 0:
        raise ValueError("SMPTE-timed MIDI is not supported; expected PPQN timing")

    tempo = 500_000
    numerator, denominator = 4, 4
    warnings = []
    for track in midi_file.tracks:
        tick = 0
        for msg in track:
            tick += msg.time
            if msg.type == "set_tempo":
                if tick == 0:
                    tempo = msg.tempo
                else:
                    raise ValueError("tempo changes are not yet supported; found a change after tick 0")
            elif msg.type == "time_signature":
                if tick == 0:
                    numerator, denominator = msg.numerator, msg.denominator
                else:
                    raise ValueError("time-signature changes are not yet supported; found a change after tick 0")

    bpm = 60_000_000 / tempo
    measure_beats = Fraction(numerator * 4, denominator)
    programs = defaultdict(int)
    active = defaultdict(deque)
    raw_notes = defaultdict(list)
    abs_tick = 0

    for msg in mido.merge_tracks(midi_file.tracks):
        abs_tick += msg.time
        if msg.is_meta:
            continue
        channel = getattr(msg, "channel", None)
        if channel is None:
            continue
        if msg.type == "program_change":
            programs[channel] = msg.program
        elif msg.type == "note_on" and msg.velocity > 0:
            active[(channel, msg.note)].append((abs_tick, msg.velocity, programs[channel]))
        elif msg.type in ("note_off", "note_on"):
            key = (channel, msg.note)
            if not active[key]:
                warnings.append(f"ignored unmatched note-off: channel={channel}, pitch={msg.note}, tick={abs_tick}")
                continue
            start_tick, velocity, program = active[key].popleft()
            if channel == 9 and not include_drums:
                continue
            if abs_tick <= start_tick:
                continue
            raw_notes[(channel, program)].append((
                Fraction(start_tick, midi_file.ticks_per_beat),
                Fraction(abs_tick - start_tick, midi_file.ticks_per_beat),
                int(msg.note if hasattr(msg, "note") else key[1]),
                int(velocity),
            ))

    dangling = [(key, len(queue)) for key, queue in active.items() if queue]
    if dangling:
        raise ValueError(f"MIDI contains {sum(n for _, n in dangling)} note-on events without note-off")

    if not raw_notes:
        raise ValueError("MIDI contains no pitched note events")

    max_end = max(start + duration for notes in raw_notes.values() for start, duration, _, _ in notes)
    measure_count = max(1, (max_end.numerator * measure_beats.denominator +
                            max_end.denominator * measure_beats.numerator - 1) //
                           (max_end.denominator * measure_beats.numerator))
    measure_lengths = tuple(measure_beats for _ in range(measure_count))
    parts = []

    for (channel, program), notes in sorted(raw_notes.items()):
        buckets = [[] for _ in range(measure_count)]
        for start, duration, pitch, velocity in notes:
            measure_index = int(start // measure_beats)
            local_start = start - measure_index * measure_beats
            buckets[measure_index].append(Note(local_start, duration, pitch, velocity))
        measures = tuple(Measure(length, tuple(bucket)) for length, bucket in zip(measure_lengths, buckets))
        family = _gm_family(program)
        if program not in (0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                           24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
                           38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 73, 74, 75, 76,
                           77, 78, 79):
            warnings.append(f"GM program {program} mapped approximately to {family.name}")
        parts.append(Part(family, measures, name=f"channel-{channel + 1}-program-{program}"))

    return MidiImportResult(Score(bpm, tuple(parts), title=Path(path).stem), tuple(warnings))
