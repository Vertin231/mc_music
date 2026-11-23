"""Command line interface for mc-music."""
from __future__ import annotations

import argparse

from .noteblock import midi_to_datapack


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="mc-music",
        description="Compile a MIDI file into a Minecraft Java note-block datapack.",
    )
    parser.add_argument("midi", help="input .mid/.midi file")
    parser.add_argument("-o", "--output", default="mc_music_datapack", help="output datapack directory")
    parser.add_argument("--pack-format", type=int, default=121,
                        help="data-pack format (default 121 for Java 26.3)")
    parser.add_argument("--ppq", type=int, default=960,
                        help="exact PPQ resolution (default 960; conversion fails if not exact)")
    parser.add_argument("--namespace", default="mc_music")
    parser.add_argument("--origin", nargs=3, type=int, default=(0, 64, 0),
                        metavar=("X", "Y", "Z"))
    parser.add_argument("--pitch-mode", choices=("strict", "octave_fold", "clip"),
                        default="strict")
    parser.add_argument("--include-drums", action="store_true",
                        help="include MIDI channel 10 as pitched note-block events")
    return parser


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)
    from .midi import load_midi
    from .noteblock import compile_to_noteblock, write_datapack

    imported = load_midi(args.midi, include_drums=args.include_drums)
    compiled = compile_to_noteblock(
        imported.score,
        ppq=args.ppq,
        origin=tuple(args.origin),
        namespace=args.namespace,
        pitch_mode=args.pitch_mode,
    )
    output_path = write_datapack(compiled, args.output, pack_format=args.pack_format)
    print(f"Imported {len(imported.score.parts)} parts at {float(imported.score.bpm):.3f} BPM")
    print(f"Generated {len(compiled.placements)} note-block placements")
    print(f"Generated {len(compiled.files)} function files")
    print(f"Datapack: {output_path.resolve()}")
    for warning in (*imported.warnings, *compiled.warnings):
        print(f"WARNING: {warning}")
    print(f"In Minecraft run: /function {args.namespace}:music/start")
    return 0
