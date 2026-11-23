"""Validated, read-only NumPy note-event matrix for batch musical operations."""
from dataclasses import dataclass
from collections import defaultdict
from fractions import Fraction
import operator
import numpy as np
from timing import ppq_to_game_ticks, round_fraction_half_up


def _int_arg(value, name):
    if isinstance(value, bool):
        raise TypeError(f"{name} must be an integer")
    try:
        return int(operator.index(value))
    except TypeError:
        raise TypeError(f"{name} must be an integer") from None


def _readonly_array(values, dtype, name):
    raw = np.asarray(values)
    if raw.ndim != 1:
        raise ValueError(f"{name} must be one-dimensional")
    if raw.dtype.kind not in "iu":
        raise TypeError(f"{name} must contain integer values")
    info = np.iinfo(dtype)
    if raw.size and (np.any(raw < info.min) or np.any(raw > info.max)):
        raise ValueError(f"{name} values exceed {dtype} range")
    arr = raw.astype(dtype, copy=True)
    arr.setflags(write=False)
    return arr


@dataclass(frozen=True, slots=True)
class NoteMatrix:
    ppq: int
    start: np.ndarray
    duration: np.ndarray
    midi: np.ndarray
    velocity: np.ndarray
    instrument: np.ndarray | None = None

    def __post_init__(self):
        ppq = _int_arg(self.ppq, "ppq")
        if ppq <= 0:
            raise ValueError("ppq must be positive")
        object.__setattr__(self, "ppq", ppq)
        raw_midi, raw_velocity = np.asarray(self.midi), np.asarray(self.velocity)
        if raw_midi.ndim != 1 or raw_velocity.ndim != 1:
            raise ValueError("midi and velocity must be one-dimensional")
        if raw_midi.dtype.kind not in "iu" or raw_velocity.dtype.kind not in "iu":
            raise TypeError("midi and velocity must be integer arrays")
        if np.any(raw_midi < 0) or np.any(raw_midi > 127):
            raise ValueError("midi must be in 0..127")
        if np.any(raw_velocity < 1) or np.any(raw_velocity > 127):
            raise ValueError("velocity must be in 1..127")
        start = _readonly_array(self.start, np.int64, "start")
        duration = _readonly_array(self.duration, np.int64, "duration")
        midi = _readonly_array(self.midi, np.int8, "midi")
        velocity = _readonly_array(self.velocity, np.int8, "velocity")
        if not (len(start) == len(duration) == len(midi) == len(velocity)):
            raise ValueError("all note arrays must have equal lengths")
        if np.any(start < 0):
            raise ValueError("start must be non-negative")
        if np.any(duration <= 0):
            raise ValueError("duration must be positive")
        if self.instrument is not None:
            instrument = _readonly_array(self.instrument, np.int8, "instrument")
            if np.any(instrument < 0) or np.any(instrument > 127):
                raise ValueError("instrument values must be in 0..127")
            if len(instrument) != len(start):
                raise ValueError("instrument length must match note arrays")
            object.__setattr__(self, "instrument", instrument)
        for name, arr in (("start", start), ("duration", duration),
                           ("midi", midi), ("velocity", velocity)):
            object.__setattr__(self, name, arr)

    def __len__(self):
        return len(self.start)

    def sorted(self):
        """Return notes sorted by onset, pitch, and duration."""
        return self._take(np.lexsort((self.duration, self.midi, self.start)))

    def transpose(self, semitones: int):
        semitones = _int_arg(semitones, "semitones")
        shifted = self.midi.astype(np.int16) + semitones
        if np.any((shifted < 0) | (shifted > 127)):
            raise ValueError("transpose would move pitches outside MIDI 0..127")
        return self._replace(midi=shifted.astype(np.int8))

    def velocity_curve(self, factor: float):
        factor = float(factor)
        if not np.isfinite(factor) or factor <= 0:
            raise ValueError("factor must be finite and positive")
        scaled = np.rint(self.velocity.astype(np.float64) * factor)
        return self._replace(velocity=np.clip(scaled, 1, 127).astype(np.int8))

    def quantize(self, grid: int, strength: float = 1.0):
        """Quantize onset PPQ ticks; nearest grid point, exact half rounds upward."""
        grid = _int_arg(grid, "grid")
        if grid <= 0:
            raise ValueError("grid must be positive")
        strength = float(strength)
        if not np.isfinite(strength) or not 0 <= strength <= 1:
            raise ValueError("strength must be finite and in 0..1")
        target = ((2 * self.start + grid) // (2 * grid)) * grid
        strength_fraction = Fraction(str(strength))
        numer = (target - self.start) * strength_fraction.numerator
        denom = strength_fraction.denominator
        moved = np.array(
            [int(s) + round_fraction_half_up(Fraction(int(d), denom))
             for s, d in zip(self.start, numer)],
            dtype=np.int64,
        )
        return self._replace(start=np.maximum(moved, 0))

    def chord_groups(self):
        """Return indices grouped by equal onset (simultaneous attacks)."""
        groups = defaultdict(list)
        for i, onset in enumerate(self.start.tolist()):
            groups[int(onset)].append(i)
        return {time: tuple(indices) for time, indices in sorted(groups.items())}

    def to_game_ticks(self, bpm, ticks_per_second: int = 20):
        """Convert PPQ onset/duration to Minecraft ticks (rounded)."""
        return (
            np.array([ppq_to_game_ticks(int(x), self.ppq, bpm, ticks_per_second)
                      for x in self.start], dtype=np.int64),
            np.array([ppq_to_game_ticks(int(x), self.ppq, bpm, ticks_per_second)
                      for x in self.duration], dtype=np.int64),
        )

    def _take(self, order):
        return NoteMatrix(self.ppq, self.start[order], self.duration[order],
                        self.midi[order], self.velocity[order],
                        None if self.instrument is None else self.instrument[order])

    def _replace(self, **changes):
        return NoteMatrix(self.ppq, changes.get("start", self.start),
                        changes.get("duration", self.duration),
                        changes.get("midi", self.midi),
                        changes.get("velocity", self.velocity),
                        changes.get("instrument", self.instrument))
