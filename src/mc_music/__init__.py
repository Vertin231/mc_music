"""mc_music: exact score model and Minecraft note-block datapack compiler."""
from .core import Instrument, Measure, Note, Part, Score
from .matrix import NoteMatrix, score_to_matrix
from .midi import MidiImportResult, load_midi
from .noteblock import (
    NoteBlockOutput,
    NoteBlockPlacement,
    compile_to_noteblock,
    midi_to_datapack,
    to_note_block_note,
    write_datapack,
)
from .ties import NoteRef, ResolvedEvent, Tie, resolve_part, resolve_ties
from .timing import (
    PPQ_DEFAULT,
    beats_to_game_ticks,
    fraction_to_ppq,
    ppq_to_game_ticks,
)

__all__ = [
    "Instrument", "Measure", "Note", "Part", "Score",
    "NoteMatrix", "score_to_matrix",
    "MidiImportResult", "load_midi",
    "NoteBlockOutput", "NoteBlockPlacement", "compile_to_noteblock",
    "midi_to_datapack", "to_note_block_note", "write_datapack",
    "NoteRef", "ResolvedEvent", "Tie", "resolve_part", "resolve_ties",
    "PPQ_DEFAULT", "beats_to_game_ticks", "fraction_to_ppq", "ppq_to_game_ticks",
]
