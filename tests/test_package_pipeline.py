from fractions import Fraction
import json

import mido
import numpy as np
import pytest

from mc_music import (
    Instrument, Measure, Note, NoteMatrix, NoteRef, Part, Score, Tie,
    compile_to_noteblock, load_midi, to_note_block_note, write_datapack,
)


def test_note_block_pitch_modes():
    assert to_note_block_note(54) == 0
    assert to_note_block_note(78) == 24
    with pytest.raises(ValueError):
        to_note_block_note(80)
    assert to_note_block_note(80, "octave_fold") == 14
    assert to_note_block_note(80, "clip") == 24


def test_compiler_creates_setup_and_one_tick_pulse():
    score = Score(120, (
        Part(Instrument.PIANO, (
            Measure(Fraction(4), (Note(Fraction(0), Fraction(1), 60),)),
        )),
    ))
    output = compile_to_noteblock(score)
    assert len(output.placements) == 1
    setup = output.files["data/mc_music/function/music/setup.mcfunction"]
    assert "minecraft:note_block[instrument=harp,note=6,powered=false]" in setup
    tick0 = output.files["data/mc_music/function/music/tick_00000000.mcfunction"]
    tick10 = output.files["data/mc_music/function/music/tick_00000010.mcfunction"]
    assert "minecraft:redstone_block" in tick0
    assert "minecraft:air" in tick10


def test_same_tick_same_physical_note_is_rejected():
    part = Part(Instrument.PIANO, (
        Measure(4, (
            Note(0, 1, 60),
            Note(0, 1, 60),
        )),
    ))
    with pytest.raises(ValueError, match="same-tick collision"):
        compile_to_noteblock(Score(120, (part,)))


def test_ppq_conversion_is_exact_or_fails():
    part = Part(Instrument.PIANO, (
        Measure(4, (Note(Fraction(1, 7), Fraction(1, 7), 60),)),
    ))
    with pytest.raises(ValueError, match="cannot be represented exactly"):
        compile_to_noteblock(Score(120, (part,)), ppq=960)
    compiled = compile_to_noteblock(Score(120, (part,)), ppq=7)
    assert compiled.schedule


def test_tie_chain_merges_into_one_event():
    part = Part(Instrument.PIANO, (
        Measure(4, (Note(3, 1, 60),)),
        Measure(4, (Note(0, 1, 60),)),
    ))
    matrix = part.to_matrix(ties=(Tie(NoteRef(0, 0), NoteRef(1, 0)),))
    assert len(matrix) == 1
    assert matrix.duration.tolist() == [2 * 960]


def test_matrix_batch_operations():
    matrix = NoteMatrix(
        960,
        np.array([10, 30]),
        np.array([10, 10]),
        np.array([60, 64]),
        np.array([80, 100]),
        np.array([0, 2]),
    )
    assert matrix.transpose(2).midi.tolist() == [62, 66]
    assert matrix.velocity_curve(0.5).velocity.tolist() == [40, 50]
    assert matrix.quantize(20, strength=0.5).start.tolist() == [15, 35]
    assert not matrix.start.flags.writeable


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
    root = write_datapack(output, tmp_path / "pack", pack_format=121)
    meta = json.loads((root / "pack.mcmeta").read_text())
    assert meta["pack"]["min_format"] == [121, 0]
    assert meta["pack"]["max_format"] == 121
    assert (root / "data/mc_music/function/music/start.mcfunction").exists()
