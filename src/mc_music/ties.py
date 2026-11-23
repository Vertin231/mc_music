"""Tie graph resolution. Tie references use sorted Measure.notes indices."""
from __future__ import annotations

from dataclasses import dataclass
from fractions import Fraction

from .core import Part


@dataclass(frozen=True, slots=True)
class NoteRef:
    measure: int
    note: int


@dataclass(frozen=True, slots=True)
class Tie:
    start: NoteRef
    end: NoteRef


@dataclass(frozen=True, slots=True)
class ResolvedEvent:
    start: Fraction
    duration: Fraction
    midi: int
    velocity: int


def resolve_ties(part: Part, ties=()) -> tuple[ResolvedEvent, ...]:
    events = []
    locations = {}
    offset = Fraction(0)
    for mi, measure in enumerate(part.measures):
        for ni, note in enumerate(measure.notes):
            ref = NoteRef(mi, ni)
            locations[ref] = (offset + note.start, note)
            events.append((ref, offset + note.start, note))
        offset += measure.length

    outgoing, incoming = {}, {}
    for tie in ties:
        if not isinstance(tie, Tie):
            raise TypeError("ties must contain Tie objects")
        if tie.start not in locations or tie.end not in locations:
            raise IndexError("tie references an invalid measure/note index")
        if tie.start == tie.end:
            raise ValueError("a note cannot tie to itself")
        if tie.start in outgoing or tie.end in incoming:
            raise ValueError("tie chains cannot branch or merge")
        a_start, a_note = locations[tie.start]
        b_start, b_note = locations[tie.end]
        if a_note.midi != b_note.midi:
            raise ValueError("tied notes must have the same MIDI pitch")
        if a_start + a_note.duration != b_start:
            raise ValueError("tied note segments must be contiguous")
        outgoing[tie.start] = tie.end
        incoming[tie.end] = tie.start

    resolved, visited = [], set()
    for ref, start, note in events:
        if ref in incoming:
            continue
        current = ref
        duration = note.duration
        chain_seen = set()
        while current in outgoing:
            if current in chain_seen:
                raise ValueError("tie graph contains a cycle")
            chain_seen.add(current)
            current = outgoing[current]
            duration += locations[current][1].duration
        visited.update(chain_seen)
        visited.add(ref)
        resolved.append(ResolvedEvent(start, duration, note.midi, note.velocity))

    if len(visited) != len(events):
        raise ValueError("tie graph contains a cycle or unreachable segment")
    return tuple(sorted(resolved, key=lambda e: (e.start, e.midi, e.duration)))


# Compatibility name used in the project plan.
resolve_part = resolve_ties
