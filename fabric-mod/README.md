# MIDI Pack — Fabric 1.20.4

MIDI Pack is a client GUI and server-side MIDI-to-datapack importer for Minecraft Java 1.20.4, Fabric, and Java 17.

## Features

- Press **M** to open the in-game MIDI Pack screen.
- Import a .mid / .midi file through the operating system's native file dialog.
- Transfer the selected file to the integrated or dedicated server using a Fabric custom payload; client networking is marshalled back onto the Minecraft client thread.
- Parse uploaded MIDI on a background worker so large files do not block the server tick loop.
- Parse Standard MIDI Format 0/1 with Java's built-in javax.sound.midi, including PPQ tempo changes and GM program changes.
- Generate two independent datapacks in the current world's datapacks directory:
  - **playsound** — maps MIDI pitches to note-block sound events; velocity controls volume.
  - **note blocks** — lays out physical note blocks and triggers them with one-game-tick redstone-block pulses.
- Play, pause, resume, and stop both backends from the GUI.

## Build

Requirements: JDK 17 and Gradle 8.6.

From this directory:

    gradle build --no-daemon

The mod jar is written to build/libs/. GitHub Actions also builds the mod and publishes the jar as a workflow artifact.

## Install and use

1. Install Fabric Loader for Minecraft 1.20.4.
2. Install Fabric API for Minecraft 1.20.4.
3. Copy midipack-0.1.1.jar into the game's mods directory.
4. Start a world or join a server with this mod installed on the server. The importing player must have operator permission level 2.
5. Press M, choose Import MIDI, and select a MIDI file (maximum 512 KiB).
6. After the server confirms import, choose either playback backend.

The server stores the uploaded MIDI source under the world's midi_import directory and writes both datapacks under datapacks/. Datapacks are reloaded after generation.

## Playback controls

The GUI exposes Play for both backends, Pause both, Resume both, and Stop both. The scoreboard timer is per player. The physical note-block structure is shared world state, so the note-block backend is intended for one operator/player at a time.

## Safety and limitations

- Upload size is limited to 512 KiB. The server validates the MIDI header, extension, and basename before writing into its controlled import directory.
- Only PPQ-timed Standard MIDI Format 0/1 files are supported. SMPTE time division is rejected.
- The parser converts absolute MIDI time to 50 ms Minecraft ticks. Very long files (over 2,000,000 game ticks) and files with over 100,000 note-on events are rejected.
- Percussion channel 10 is mapped to a small set of vanilla note-block percussion sounds.
- Vanilla note blocks cannot represent MIDI velocity or note-off duration. Each note-on is a one-tick trigger; repeated identical pitches on the same game tick are coalesced by the physical note-block backend.
- The note-block layout is placed near the overworld spawn point. The generator uses setblock ... keep, but you should still choose a clear area and make a world backup before testing.
- The playsound backend is an approximation: GM instruments are grouped into a handful of vanilla note-block timbres, MIDI velocity maps to volume, and MIDI pitch is folded into the vanilla sound pitch range.
- The native file picker requires a desktop environment. Headless dedicated servers do not open a file picker; importing is initiated by a connected client.

## Project layout

    fabric-mod/
      build.gradle
      gradle.properties
      settings.gradle
      src/main/java/com/example/midipack/
        MidiPackMod.java
        client/MidiPackClient.java
        client/MidiPackScreen.java
        client/MidiFilePicker.java
        midi/MidiParser.java
        midi/NoteEvent.java
        generator/DatapackGenerator.java
      src/main/resources/fabric.mod.json
