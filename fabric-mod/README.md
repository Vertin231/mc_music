# MIDI Pack — Fabric 1.20.4

A client GUI + server-side MIDI importer for Minecraft Java Edition 1.20.4 (Java 17).

## Features

- Press **M** to open the in-game MIDI Pack screen.
- **Import MIDI** opens the operating system file chooser (Swing).
- Uploads a MIDI file to the integrated/dedicated server through a Fabric custom payload.
- Parses Standard MIDI Format 0/1 with Java's built-in `javax.sound.midi`, including tempo changes.
- Generates two datapacks into the current world's `datapacks/` directory:
  - `playsound`: sound events with MIDI pitch and velocity approximations.
  - `note-block`: physical note blocks with adjacent one-tick redstone-block pulses.
- Reloads datapacks after generation. Use the GUI to start either backend or stop playback.

## Build

Requirements: JDK 17 and Gradle 8.6.

From this directory:

```bash
gradle build
```

The mod jar is written to `build/libs/`.

## Install

1. Install Fabric Loader for Minecraft 1.20.4.
2. Install Fabric API for 1.20.4.
3. Copy the built `midipack-0.1.0.jar` into `.minecraft/mods/`.
4. Start the game and enter a world where you have permission level 2 (operator).

Press **M**, import a `.mid` or `.midi` file (maximum 512 KiB), then choose a playback backend.

## Note-block backend safety

The note-block datapack lays out note blocks around the overworld spawn point. It uses `setblock ... keep` for setup so existing blocks are not overwritten. Choose a clear area around world spawn; if positions are occupied, the corresponding note block will not be placed. During playback, adjacent control positions are temporarily replaced by redstone blocks and reset to air one game tick later. Keep this area clear.

Vanilla note blocks cannot reproduce MIDI velocity or note-off duration. The note-block backend therefore approximates each note-on as a one-tick trigger. The playsound backend uses velocity as volume and MIDI pitch as the playsound pitch parameter.

## Current limitations

- Upload limit: 512 KiB per MIDI file.
- Only PPQ-timed MIDI is accepted; SMPTE time division is rejected.
- Percussion channel 10 is mapped to a small set of vanilla note-block percussion sounds.
- Playback control is designed for one active operator/player at a time; note-block structures are shared world objects.
- No resource pack or custom audio is required.
