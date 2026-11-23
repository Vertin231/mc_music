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
    parser.add_argument("--pack-format", type=int, required=True,
                        help="data-pack format number for your exact Minecraft Java version")
    parser.add_argument("--namespace", default="mc_music")
    parser.add_argument("--origin", nargs=3, type=int, default=(0, 64, 0),
                        metavar=("X", "Y", "Z"))
    parser.add_argument("--pitch-mode", choices=("strict", "octave_fold", "clip"),
                        default="strict")
    return parser


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)
    imported, compiled, output_path = midi_to_datapack(
        args.midi,
        args.output,
        pack_format=args.pack_format,
        origin=tuple(args.origin),
        namespace=args.namespace,
        pitch_mode=args.pitch_mode,
    )
    print(f"Imported {len(imported.score.parts)} parts at {imported.score.bpm:.3f} BPM")
    print(f"Generated {len(compiled.placements)} note-block placements")
    print(f"Generated {len(compiled.files)} function files")
    print(f"Datapack: {output_path.resolve()}")
    for warning in (*imported.warnings, *compiled.warnings):
        print(f"WARNING: {warning}")
    print(f"In Minecraft run: /function {args.namespace}:music/start")
    return 0
