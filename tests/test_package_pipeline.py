from fractions import Fraction
import json

import mido
import pytest

from mc_music import (
    Instrument, Measure, Note, Part, Score,
    compile_to_noteblock, load_midi, to_note_block_note, write_datapack,
)


def test_note_block_pitch_modes():
    assert to_note_block_note(54) == 0
    assert to_note_block_note(78) == 24
    with pytest.raises(ValueError):
        to_note_block_note(80)
    assert to_note_block_note(80, "octave_fold") == 2
    assert to_note_block_note(80, "clip") == 24


def test_compiler_creates_setup_and_one_tick_pulse():
    score = Score(120, (
        Part(Instrument.PIANO, (
            Measure(4, (Note(Fraction(0), Fraction(1), 60),)),
        )),
    ))
    output = compile_to_noteblock(score)
    assert len(output.placements) == 1
    setup = output.files["data/mc_music/functions/music/setup.mcfunction"]
    assert "minecraft:note_block[instrument=harp,note=6,powered=false]" in setup
    tick0 = output.files["data/mc_music/functions/music/tick_00000000.mcfunction"]
    tick1 = output.files["data/mc_music/functions/music/tick_00000001.mcfunction"]
    assert "minecraft:redstone_block" in tick0
    assert "minecraft:air" in tick1


def test_same_tick_same_physical_note_is_rejected():
    part = Part(Instrument.PIANO, (
        Measure(4, (
            Note(0, 1, 60),
            Note(0, 1, 60),
        )),
    ))
    with pytest.raises(ValueError, match="same-tick collision"):
        compile_to_noteblock(Score(120, (part,)))


def test_midi_import(tmp_path):
    midi = mido.MidiFile(ticks_per_beat=480)
    track = mido.MidiTrack()
    midi.tracks.append(track)
    track.append(mido.Message("program_change", channel=0, program=0, time=0))
    track.append(mido.Message("note_on", channel=0, note=60, velocity=90, time=0))
    track.append(mido.Message("note_off", channel=0, note=60, velocity=0, time=480))
    path = tmp_path / "one_note.mid"
    midi.save(path)

    imported = load_midi(path)
    assert imported.score.bpm == 120
    assert imported.score.parts[0].instrument is Instrument.PIANO
    start, note = next(imported.score.parts[0].iter_absolute())
    assert start == 0
    assert note.duration == 1
    assert note.midi == 60


def test_write_datapack(tmp_path):
    score = Score(120, (Part(Instrument.PIANO, (Measure(4, (Note(0, 1, 60),)),)),))
    output = compile_to_noteblock(score)
    root = write_datapack(output, tmp_path / "pack", pack_format=94)
    meta = json.loads((root / "pack.mcmeta").read_text())
    assert meta["pack"]["pack_format"] == 94
    assert (root / "data/mc_music/functions/music/start.mcfunction").exists()
