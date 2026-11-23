from __future__ import annotations

"""Tie references and tie-chain resolution for a Part's note segments."""
from dataclasses import dataclass
from fractions import Fraction
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from core import Part


@dataclass(frozen=True, slots=True)
class NoteRef:
    measure: int
    note: int


@dataclass(frozen=True, slots=True)
class Tie:
    start: NoteRef
    end: NoteRef


@dataclass(frozen=True, slots=True)
class ResolvedNote:
    start: Fraction
    duration: Fraction
    midi: int
    velocity: int


def resolve_ties(part: Part, ties=()) -> tuple[ResolvedNote, ...]:
    """Merge tied segments in a Part; ties connect contiguous same-pitch notes."""
    events, locations = [], {}
    offset = Fraction(0)
    for mi, measure in enumerate(part.measures):
        for ni, note in enumerate(measure.notes):
            absolute = offset + note.start
            ref = NoteRef(mi, ni)
            locations[ref] = (absolute, note)
            events.append((ref, absolute, note))
        offset += measure.length

    outgoing, incoming = {}, {}
    for tie in ties:
        if tie.start not in locations or tie.end not in locations:
            raise IndexError("Tie references an invalid measure/note index")
        if tie.start == tie.end:
            raise ValueError("a note cannot tie to itself")
        if tie.start in outgoing or tie.end in incoming:
            raise ValueError("Tie chains cannot branch or merge")
        a_start, a_note = locations[tie.start]
        b_start, b_note = locations[tie.end]
        if a_note.midi != b_note.midi:
            raise ValueError("Tie segments must have the same MIDI pitch")
        if a_start + a_note.duration != b_start:
            raise ValueError("Tie segments must be contiguous in absolute time")
        outgoing[tie.start] = tie.end
        incoming[tie.end] = tie.start

    merged, visited = [], set()
    for ref, start, note in events:
        if ref in incoming:
            continue
        duration, current, chain = note.duration, ref, [ref]
        seen = set()
        while current in outgoing:
            if current in seen:
                raise ValueError(f"Tie chain contains a cycle at {current}")
            seen.add(current)
            current = outgoing[current]
            _, next_note = locations[current]
            duration += next_note.duration
            chain.append(current)
        merged.append(ResolvedNote(start, duration, note.midi, note.velocity))
        visited.update(chain)
    if len(visited) != len(events):
        raise ValueError("Tie graph contains a cycle or invalid chain")
    return tuple(sorted(merged, key=lambda n: (n.start, n.midi, n.duration)))
