"""Exact beat-to-PPQ and PPQ-to-game-tick conversions."""
from fractions import Fraction
import operator

PPQ_DEFAULT = 960


def _positive_int(value, name):
    if isinstance(value, bool):
        raise TypeError(f"{name} must be an integer, not bool")
    try:
        value = operator.index(value)
    except TypeError:
        raise TypeError(f"{name} must be an integer") from None
    if value <= 0:
        raise ValueError(f"{name} must be positive")
    return int(value)


def fraction_to_ppq(value: Fraction, ppq: int = PPQ_DEFAULT) -> int:
    """Convert quarter-note Fraction to PPQ ticks, requiring exact representation."""
    ppq = _positive_int(ppq, "ppq")
    value = Fraction(value)
    ticks = value * ppq
    if ticks.denominator != 1:
        raise ValueError(
            f"{value} beats cannot be represented at PPQ={ppq}; "
            f"PPQ must be a multiple of {value.denominator}"
        )
    return ticks.numerator


def round_fraction_half_up(value: Fraction) -> int:
    """Nearest integer; exact halves round away from zero."""
    value = Fraction(value)
    if value >= 0:
        return (2 * value.numerator + value.denominator) // (2 * value.denominator)
    return -round_fraction_half_up(-value)


def ppq_to_game_ticks(ppq_ticks: int, ppq: int, bpm, ticks_per_second: int = 20) -> int:
    """Convert PPQ ticks to Minecraft ticks; final rounding is half-up."""
    ppq = _positive_int(ppq, "ppq")
    ticks_per_second = _positive_int(ticks_per_second, "ticks_per_second")
    if isinstance(bpm, bool):
        raise TypeError("bpm must be positive")
    bpm_fraction = Fraction(str(bpm))
    if bpm_fraction <= 0:
        raise ValueError("bpm must be positive")
    return round_fraction_half_up(
        Fraction(ppq_ticks * 60 * ticks_per_second, ppq) / bpm_fraction
    )
