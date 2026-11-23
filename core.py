from dataclasses import dataclass
from enum import IntEnum
from fractions import Fraction
from math import isfinite
from collections.abc import Iterator

# Utility

def as_fraction(value, field_name: str) -> Fraction:
    """
    Convert an integer or Fraction into Fraction.
    Floats are rejected to avoid floating-point inaccuracies.
    """
    if isinstance(value, Fraction):
        return value
    if type(value) is int:
        return Fraction(value)
    raise TypeError(
        f"{field_name} must be an int or Fraction, "
        f"got {type(value).__name__}"
    )

# Instrument

class Instrument(IntEnum):
    PIANO = 0
    VIOLIN = 1
    FLUTE = 2
    GUITAR = 3
    BASS = 4
    BELL = 5

# Note

@dataclass(frozen=True, slots=True)
class Note:
    start: Fraction
    duration: Fraction
    midi: int
    velocity: int

    def __post_init__(self):

        object.__setattr__(
            self, "start", as_fraction(self.start, "start")
        )

        object.__setattr__(
            self, "duration", as_fraction(self.duration, "duration")
        )

        if self.start < 0:
            raise ValueError("start must be non-negative")

        if self.duration <= 0:
            raise ValueError("duration must be positive")

        if type(self.midi) is not int:
            raise TypeError("midi must be an integer")

        if not 0 <= self.midi <= 127:
            raise ValueError("midi must be between 0 and 127")

        if type(self.velocity) is not int:
            raise TypeError("velocity must be an integer")

        if not 1 <= self.velocity <= 127:
            raise ValueError("velocity must be between 1 and 127")

    @property
    def end(self) -> Fraction:
        return self.start + self.duration

# Measure

@dataclass(frozen=True, slots=True)
class Measure:

    length: Fraction
    notes: tuple[Note, ...] = ()

    def __post_init__(self):
        object.__setattr__(
            self, "length", as_fraction(self.length, "length")
        )

        if self.length <= 0:
            raise ValueError("measure length must be positive")

        if not isinstance(self.notes, (tuple, list)):
            raise TypeError("notes must be a tuple or list")

        notes = tuple(self.notes)

        for note in notes:

            if not isinstance(note, Note):
                raise TypeError("notes must contain Note objects")

            if note.end > self.length:
                raise ValueError(
                    f"Note exceeds measure boundary: "
                    f"start={note.start}, end={note.end}, "
                    f"length={self.length}"
                )

        notes = tuple(
            sorted(notes, key=lambda n: (n.start, n.midi))
        )

        object.__setattr__(self, "notes", notes)

    def at(self, start: Fraction) -> tuple[Note, ...]:

        start = as_fraction(start, "start")

        if start < 0:
            raise ValueError("start must be non-negative")

        return tuple(
            note for note in self.notes
            if note.start == start
        )

    def chords(self) -> dict[Fraction, tuple[Note, ...]]:

        groups: dict[Fraction, list[Note]] = {}

        for note in self.notes:
            groups.setdefault(note.start, []).append(note)

        return {
            time: tuple(groups[time])
            for time in sorted(groups)
        }

# Part

@dataclass(frozen=True, slots=True)
class Part:

    instrument: Instrument
    measures: tuple[Measure, ...] = ()

    def __post_init__(self):

        if not isinstance(self.instrument, Instrument):
            raise TypeError("instrument must be an Instrument")

        if not isinstance(self.measures, (tuple, list)):
            raise TypeError("measures must be a tuple or list")

        measures = tuple(self.measures)

        for measure in measures:
            if not isinstance(measure, Measure):
                raise TypeError(
                    "measures must contain Measure objects"
                )

        object.__setattr__(self, "measures", measures)

    def measure_start(self, index: int) -> Fraction:
        """Absolute start of the specified measure."""

        if type(index) is not int:
            raise TypeError("index must be an integer")

        if not 0 <= index < len(self.measures):
            raise IndexError("measure index out of range")

        return sum(
            (m.length for m in self.measures[:index]),
            Fraction(0)
        )

    def total_beats(self) -> Fraction:
        """Total length of this part, in quarter-note units."""

        return sum(
            (m.length for m in self.measures),
            Fraction(0)
        )

    def notes_at(
        self,
        measure: int,
        start: Fraction,
        midi: int
    ) -> tuple[Note, ...]:
        """Return all notes matching the specified location and pitch."""

        if type(measure) is not int:
            raise TypeError("measure must be an integer")

        if not 0 <= measure < len(self.measures):
            raise IndexError("measure index out of range")

        return tuple(
            note
            for note in self.measures[measure].notes
            if note.start == as_fraction(start, "start")
            and note.midi == midi
        )

    def note_at(
        self,
        measure: int,
        start: Fraction,
        midi: int
    ) -> Note:
        """Find exactly one note by measure, onset and pitch."""

        matches = self.notes_at(measure, start, midi)

        if not matches:
            raise KeyError("note not found")

        if len(matches) > 1:
            raise ValueError("multiple matching notes; use notes_at()")

        return matches[0]

    def iter_absolute(self) -> Iterator[tuple[Fraction, Note]]:
        """
        Expand measure-relative note positions into absolute positions.
        """

        offset = Fraction(0)

        for measure in self.measures:

            for note in measure.notes:
                yield offset + note.start, note

            offset += measure.length

# Score
@dataclass(frozen=True, slots=True)
class Score:

    bpm: float
    parts: tuple[Part, ...]

    def __post_init__(self):

        if isinstance(self.bpm, bool) or not isinstance(
            self.bpm, (int, float)
        ):
            raise TypeError("bpm must be a number")

        if not isfinite(self.bpm) or self.bpm <= 0:
            raise ValueError("bpm must be finite and positive")

        if not isinstance(self.parts, (tuple, list)):
            raise TypeError("parts must be a tuple or list")

        parts = tuple(self.parts)

        if not parts:
            raise ValueError("score cannot be empty")

        for part in parts:
            if not isinstance(part, Part):
                raise TypeError("parts must contain Part objects")

        object.__setattr__(self, "parts", parts)

        # Check whether all parts share the same measure structure.
        reference = tuple(
            measure.length for measure in parts[0].measures
        )

        for part in parts[1:]:

            current = tuple(
                measure.length for measure in part.measures
            )

            if current != reference:
                raise ValueError(
                    f"Measure structure mismatch in "
                    f"{part.instrument.name}: "
                    f"expected {reference}, got {current}"
                )

    def total_beats(self) -> Fraction:

        return self.parts[0].total_beats()

    def timeline(
        self
    ) -> dict[Fraction, dict[Instrument, tuple[Note, ...]]]:

        raw: dict[
            Fraction,
            dict[Instrument, list[Note]]
        ] = {}

        for part in self.parts:

            for absolute_start, note in part.iter_absolute():

                by_instrument = raw.setdefault(
                    absolute_start, {}
                )

                by_instrument.setdefault(
                    part.instrument, []
                ).append(note)

        return {
            time: {
                instrument: tuple(notes)
                for instrument, notes in sorted(
                    by_instrument.items(),
                    key=lambda item: item[0].value
                )
            }
            for time, by_instrument in sorted(raw.items())
        }
