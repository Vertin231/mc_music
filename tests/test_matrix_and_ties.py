from fractions import Fraction
import numpy as np
from core import Instrument, Note, Measure, Part, Score
from matrix import NoteMatrix
from timing import fraction_to_ppq
from ties import NoteRef, Tie

def test_ppq_exact():
    assert fraction_to_ppq(Fraction(1, 3), 960) == 320
    try:
        fraction_to_ppq(Fraction(1, 7), 960)
    except ValueError:
        pass
    else:
        assert False

def test_matrix_ops():
    m=NoteMatrix(960,np.array([240,0]),np.array([120,240]),np.array([64,60]),np.array([80,100]))
    assert m.sorted().midi.tolist()==[60,64]
    assert m.transpose(2).midi.tolist()==[66,62]
    assert m.quantize(120).start.tolist()==[240,0]
    assert m.chord_groups()=={0:(1,),240:(0,)}
    assert not m.start.flags.writeable

def test_cross_bar_and_ties():
    part=Part(Instrument.PIANO,(Measure(4,(Note(3,2,60,90),)),Measure(4,(Note(1,1,60,80),Note(2,1,62,70)))))
    mat=part.to_matrix()
    assert mat.start.tolist()==[2880,4800,5760]
    assert mat.duration.tolist()==[1920,960,960]
    ties=(Tie(NoteRef(0,0),NoteRef(1,0)),)
    tied=part.to_matrix(ties=ties)
    assert tied.start.tolist()==[2880,5760]
    assert tied.duration.tolist()==[2880,960]

def test_score_matrix():
    p1=Part(Instrument.PIANO,(Measure(4,(Note(0,1,60,100),)),))
    p2=Part(Instrument.FLUTE,(Measure(4,(Note(2,1,72,90),)),))
    s=Score(120,(p1,p2))
    mat=s.to_matrix()
    assert mat.instrument.tolist()==[0,2]
    starts, durs=mat.to_game_ticks(120)
    assert starts.tolist()==[0,10]
    assert durs.tolist()==[10,10]
