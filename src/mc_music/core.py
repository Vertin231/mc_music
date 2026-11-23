"""Immutable musical score model with exact quarter-note timing."""
from __future__ import annotations

from dataclasses import dataclass
from enum import IntEnum
from fractions import Fraction
from math import isfinite
import operator

from .timing import PPQ_DEFAULT, as_fraction


class Instrument(IntEnum):
    PIANO = 0
    VIOLIN = 1
    FLUTE = 2
    GUITAR = 3
    BASS = 4
    BELL = 5


@dataclass(frozen=True, slots=True)
class Note:
    start: Fraction
    duration: Fraction
    midi: int
    velocity: int = 100

    def __post_init__(self):
        object.__setattr__(self, "start", as_fraction(self.start, "start"))
        object.__setattr__(self, "duration", as_fraction(self.duration, "duration"))
        for name, value, low, high in (
            ("midi", self.midi, 0, 127),
            ("velocity", self.velocity, 1, 127),
        ):
            if isinstance(value, bool):
                raise TypeError(f"{name} must be an integer")
            try:
                value = operator.index(value)
            except TypeError:
                raise TypeError(f"{name} must be an integer") from None
            if not low <= value <= high:
                raise ValueError(f"{name} must be in {low}..{high}")
            object.__setattr__(self, name, int(value))
        if self.start < 0:
            raise ValueError("note start must be non-negative")
        if self.duration <= 0:
            raise ValueError("note duration must be positive")


@dataclass(frozen=True, slots=True)
class Measure:
    length: Fraction
    notes: tuple[Note, ...] = ()

    def __post_init__(self):
        length = as_fraction(self.length, "length")
        if length <= 0:
            raise ValueError("measure length must be positive")
        notes = tuple(self.notes)
        if any(not isinstance(note, Note) for note in notes):
            raise TypeError("measure notes must be Note objects")
        if any(note.start >= length for note in notes):
            raise ValueError("each note onset must lie inside its measure")
        object.__setattr__(self, "length", length)
        object.__setattr__(self, "notes", tuple(sorted(notes, key=lambda n: (n.start, n.midi))))

    def at(self, start):
        start = as_fraction(start, "start")
        return tuple(note for note in self.notes if note.start == start)

    def chords(self):
        groups = {}
        for note in self.notes:
            groups.setdefault(note.start, []).append(note)
        return {start: tuple(notes) for start, notes in sorted(groups.items())}


@dataclass(frozen=True, slots=True)
class Part:
    instrument: Instrument
    measures: tuple[Measure, ...]
    name: str = ""

    def __post_init__(self):
        if not isinstance(self.instrument, Instrument):
            raise TypeError("instrument must be an Instrument")
        measures = tuple(self.measures)
        if any(not isinstance(m, Measure) for m in measures):
            raise TypeError("measures must contain Measure objects")
        object.__setattr__(self, "measures", measures)

    def iter_absolute(self):
        offset = Fraction(0)
        for measure in self.measures:
            for note in measure.notes:
                yield offset + note.start, note
            offset += measure.length

    def total_beats(self) -> Fraction:
        return sum((m.length for m in self.measures), Fraction(0))

    def to_matrix(self, ppq: int = PPQ_DEFAULT, ties=()):
        from .matrix import score_to_matrix
        return score_to_matrix(Score(120, (self,)), ppq, {0: tuple(ties)})


@dataclass(frozen=True, slots=True)
class Score:
    bpm: float
    parts: tuple[Part, ...]
    title: str = ""

    def __post_init__(self):
        if isinstance(self.bpm, bool) or not isinstance(self.bpm, (int, float)):
            raise TypeError("bpm must be numeric")
        if not isfinite(self.bpm) or self.bpm <= 0:
            raise ValueError("bpm must be finite and positive")
        parts = tuple(self.parts)
        if not parts or any(not isinstance(p, Part) for p in parts):
            raise ValueError("score requires one or more Part objects")
        structure = tuple(m.length for m in parts[0].measures)
        for part in parts[1:]:
            if tuple(m.length for m in part.measures) != structure:
                raise ValueError("all Parts must share the same measure structure")
        object.__setattr__(self, "parts", parts)

    def total_beats(self) -> Fraction:
        return self.parts[0].total_beats()

    def absolute_events(self):
        events = []
        for part_index, part in enumerate(self.parts):
            for start, note in part.iter_absolute():
                events.append((start, part_index, part.instrument, note))
        return tuple(sorted(events, key=lambda e: (e[0], e[1], e[3].midi)))

    def to_matrix(self, ppq: int = PPQ_DEFAULT, ties_by_part_index=None):
        from .matrix import score_to_matrix
        return score_to_matrix(self, ppq, ties_by_part_index)
