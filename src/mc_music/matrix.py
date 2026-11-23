"""Validated NumPy structure-of-arrays representation of note events."""
from __future__ import annotations

from dataclasses import dataclass
from fractions import Fraction
import operator

import numpy as np

from .core import Score
from .ties import resolve_ties
from .timing import PPQ_DEFAULT, fraction_to_ppq, ppq_to_game_ticks


def _integer(value, name):
    if isinstance(value, bool):
        raise TypeError(f"{name} must be an integer")
    try:
        return int(operator.index(value))
    except TypeError:
        raise TypeError(f"{name} must be an integer") from None


def _array(values, dtype, name):
    raw = np.asarray(values)
    if raw.ndim != 1 or (raw.size and raw.dtype.kind not in "iu"):
        raise TypeError(f"{name} must be a one-dimensional integer array")
    info = np.iinfo(dtype)
    if raw.size and (np.any(raw < info.min) or np.any(raw > info.max)):
        raise ValueError(f"{name} exceeds {dtype} range")
    result = raw.astype(dtype, copy=True)
    result.setflags(write=False)
    return result


@dataclass(frozen=True, slots=True)
class NoteMatrix:
    ppq: int
    start: np.ndarray
    duration: np.ndarray
    midi: np.ndarray
    velocity: np.ndarray
    instrument: np.ndarray

    def __post_init__(self):
        ppq = _integer(self.ppq, "ppq")
        if ppq <= 0:
            raise ValueError("ppq must be positive")
        object.__setattr__(self, "ppq", ppq)
        arrays = {
            "start": _array(self.start, np.int64, "start"),
            "duration": _array(self.duration, np.int64, "duration"),
            "midi": _array(self.midi, np.int8, "midi"),
            "velocity": _array(self.velocity, np.int8, "velocity"),
            "instrument": _array(self.instrument, np.int8, "instrument"),
        }
        if len({len(a) for a in arrays.values()}) != 1:
            raise ValueError("all note arrays must have equal lengths")
        if np.any(arrays["start"] < 0) or np.any(arrays["duration"] <= 0):
            raise ValueError("start must be non-negative and duration positive")
        if np.any((arrays["midi"] < 0) | (arrays["midi"] > 127)):
            raise ValueError("midi must be in 0..127")
        if np.any((arrays["velocity"] < 1) | (arrays["velocity"] > 127)):
            raise ValueError("velocity must be in 1..127")
        if np.any((arrays["instrument"] < 0) | (arrays["instrument"] > 127)):
            raise ValueError("instrument values must be in 0..127")
        for name, value in arrays.items():
            object.__setattr__(self, name, value)

    def __len__(self):
        return len(self.start)

    def sorted(self):
        order = np.lexsort((self.midi, self.start))
        return self._take(order)

    def transpose(self, semitones: int):
        semitones = _integer(semitones, "semitones")
        moved = self.midi.astype(np.int16) + semitones
        if np.any((moved < 0) | (moved > 127)):
            raise ValueError("transpose would move MIDI pitches outside 0..127")
        return self._replace(midi=moved)

    def velocity_curve(self, factor: float):
        factor = float(factor)
        if not np.isfinite(factor) or factor <= 0:
            raise ValueError("factor must be finite and positive")
        moved = np.rint(self.velocity.astype(np.float64) * factor)
        return self._replace(velocity=np.clip(moved, 1, 127))

    def quantize(self, grid: int, strength: float = 1.0):
        grid = _integer(grid, "grid")
        strength = Fraction(str(strength))
        if grid <= 0 or not 0 <= strength <= 1:
            raise ValueError("grid must be positive and strength in 0..1")
        moved = []
        for onset in self.start:
            onset = int(onset)
            target = ((2 * onset + grid) // (2 * grid)) * grid
            delta = Fraction((target - onset) * strength.numerator, strength.denominator)
            adjustment = (2 * delta.numerator + delta.denominator) // (2 * delta.denominator)
            moved.append(max(0, onset + adjustment))
        return self._replace(start=np.asarray(moved, dtype=np.int64))

    def chord_groups(self):
        if not len(self):
            return {}
        order = np.argsort(self.start, kind="stable")
        unique, first = np.unique(self.start[order], return_index=True)
        groups = np.split(order, first[1:])
        return {int(t): tuple(int(i) for i in g) for t, g in zip(unique, groups)}

    def to_game_ticks(self, bpm, ticks_per_second: int = 20):
        starts = np.asarray(
            [ppq_to_game_ticks(int(x), self.ppq, bpm, ticks_per_second) for x in self.start],
            dtype=np.int64,
        )
        durations = np.asarray(
            [ppq_to_game_ticks(int(x), self.ppq, bpm, ticks_per_second) for x in self.duration],
            dtype=np.int64,
        )
        return starts, durations

    def _take(self, order):
        return NoteMatrix(
            self.ppq, self.start[order], self.duration[order], self.midi[order],
            self.velocity[order], self.instrument[order],
        )

    def _replace(self, **changes):
        return NoteMatrix(
            self.ppq,
            changes.get("start", self.start),
            changes.get("duration", self.duration),
            changes.get("midi", self.midi),
            changes.get("velocity", self.velocity),
            changes.get("instrument", self.instrument),
        )


def score_to_matrix(
    score: Score,
    ppq: int = PPQ_DEFAULT,
    ties_by_part_index: dict | None = None,
) -> NoteMatrix:
    """Resolve ties, then convert every event to exact PPQ integer columns."""
    ties_by_part_index = ties_by_part_index or {}
    starts, durations, midis, velocities, instruments = [], [], [], [], []
    for part_index, part in enumerate(score.parts):
        events = resolve_ties(part, ties_by_part_index.get(part_index, ()))
        for event in events:
            starts.append(fraction_to_ppq(event.start, ppq))
            durations.append(fraction_to_ppq(event.duration, ppq))
            midis.append(event.midi)
            velocities.append(event.velocity)
            instruments.append(int(part.instrument))
    return NoteMatrix(
        ppq,
        np.asarray(starts, dtype=np.int64),
        np.asarray(durations, dtype=np.int64),
        np.asarray(midis, dtype=np.int8),
        np.asarray(velocities, dtype=np.int8),
        np.asarray(instruments, dtype=np.int8),
    )
