"""Exact musical-time conversions.

The semantic time unit is one quarter note. Fraction -> PPQ must be exact;
only the final conversion to Minecraft's integer game ticks is rounded.
"""
from __future__ import annotations

from fractions import Fraction
import operator

PPQ_DEFAULT = 960
TICKS_PER_SECOND = 20


def positive_int(value: int, name: str) -> int:
    if isinstance(value, bool):
        raise TypeError(f"{name} must be an integer, not bool")
    try:
        result = operator.index(value)
    except TypeError:
        raise TypeError(f"{name} must be an integer") from None
    if result <= 0:
        raise ValueError(f"{name} must be positive")
    return int(result)


def as_fraction(value, name: str = "value") -> Fraction:
    if isinstance(value, Fraction):
        return value
    if isinstance(value, bool):
        raise TypeError(f"{name} must be int or Fraction, not bool")
    try:
        return Fraction(operator.index(value))
    except TypeError:
        raise TypeError(f"{name} must be int or Fraction") from None


def fraction_to_ppq(beats: Fraction, ppq: int = PPQ_DEFAULT) -> int:
    ppq = positive_int(ppq, "ppq")
    beats = as_fraction(beats, "beats")
    value = beats * ppq
    if value.denominator != 1:
        raise ValueError(
            f"{beats} beats cannot be represented exactly at PPQ={ppq}; "
            f"choose a PPQ divisible by {value.denominator}"
        )
    return value.numerator


def round_half_up(value: Fraction) -> int:
    value = Fraction(value)
    if value >= 0:
        return (2 * value.numerator + value.denominator) // (2 * value.denominator)
    return -round_half_up(-value)


def beats_to_game_ticks(beats: Fraction, bpm: int | float, tps: int = TICKS_PER_SECOND) -> int:
    """Convert quarter-note beats to game ticks, rounding half-up once."""
    beats = as_fraction(beats, "beats")
    if beats < 0:
        raise ValueError("beats must be non-negative")
    tps = positive_int(tps, "tps")
    if isinstance(bpm, bool):
        raise TypeError("bpm must be numeric")
    bpm_fraction = Fraction(str(bpm))
    if bpm_fraction <= 0:
        raise ValueError("bpm must be positive")
    return round_half_up(beats * 60 * tps / bpm_fraction)


def ppq_to_game_ticks(ppq_ticks: int, ppq: int, bpm: int | float, tps: int = TICKS_PER_SECOND) -> int:
    ppq = positive_int(ppq, "ppq")
    if isinstance(ppq_ticks, bool):
        raise TypeError("ppq_ticks must be an integer")
    try:
        ppq_ticks = operator.index(ppq_ticks)
    except TypeError:
        raise TypeError("ppq_ticks must be an integer") from None
    return beats_to_game_ticks(Fraction(ppq_ticks, ppq), bpm, tps)
