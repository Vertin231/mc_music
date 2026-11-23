"""Create a tiny example score and compile it to a Java 26.3 datapack."""
from fractions import Fraction
from pathlib import Path

from mc_music import Instrument, Measure, Note, Part, Score, compile_to_noteblock, write_datapack

melody = Part(
    Instrument.PIANO,
    (
        Measure(Fraction(4), (
            Note(Fraction(0), Fraction(1), 67),
            Note(Fraction(1), Fraction(1), 67),
            Note(Fraction(2), Fraction(1), 74),
            Note(Fraction(3), Fraction(1), 74),
        )),
        Measure(Fraction(4), (
            Note(Fraction(0), Fraction(1), 76),
            Note(Fraction(1), Fraction(1), 76),
            Note(Fraction(2), Fraction(2), 74),
        )),
    ),
    name="Twinkle melody",
)

score = Score(120, (melody,), title="Twinkle example")
compiled = compile_to_noteblock(score, origin=(0, 64, 0), pitch_mode="strict")
write_datapack(compiled, Path("twinkle_datapack"), pack_format=121)
print("Run /function mc_music:music/start in Minecraft.")
