# MIDI Pack — Fabric 1.20.4

MIDI Pack is a client GUI and server-side MIDI-to-datapack importer for Minecraft Java 1.20.4, Fabric, and Java 17.

## Features

- Press **M** to open the in-game MIDI Pack screen.
- Enter a custom X/Y/Z placement origin, or use the current player block position.
- Import a .mid / .midi file through the operating system's native file dialog.
- Transfer the selected file and placement coordinates to the integrated or dedicated server using a Fabric custom payload.
- Parse Standard MIDI Format 0/1 with Java's built-in javax.sound.midi, including PPQ tempo changes and GM program changes.
- Retain source Track and MIDI Channel. Physical parts are split by **Track + Channel**:
  - MIDI 1: source tracks are separated; multiple channels inside one source track are split into separate lanes.
  - MIDI 0: its single track is split by MIDI Channel.
- Generate two datapacks:
  - **playsound** — maps MIDI pitches to note-block sound events; velocity controls volume.
  - **note blocks** — creates one straight X-axis lane per detected Track + Channel part. Lanes run parallel along Z and share one global MIDI tick timeline.
- Play, pause, resume, and stop both backends from the GUI.

## Physical lane layout

- The chosen origin is the support-block coordinate of the first station in the first part.
- Parts are ordered by ascending source Track index, then MIDI Channel.
- Each part gets its own Z lane, spaced 3 blocks apart.
- Within each lane, unique note-block instrument/pitch stations are laid out along +X, with 3 blocks between stations.
- Each station has a support block, a note block one block above it, and a neighboring redstone-block pulse trigger.
- Chords can pulse multiple stations in one lane on the same game tick. Different lanes use the same global tick counter, preserving cross-part synchronization.
- These are independent redstone-block pulse stations, not a continuous redstone-dust bus.

Reserve roughly 770 blocks in the +X direction and Z space for all parts (3 blocks per lane). The note-block backend supports up to 64 parts and 256 unique stations per part. The playsound backend does not use the placement coordinates.

## Build

Requirements: JDK 17 and Gradle 8.6.

    gradle build --no-daemon

The mod jar is written to build/libs/.

## Install and use

1. Install Fabric Loader for Minecraft 1.20.4 and Fabric API.
2. Copy the mod jar into the game's mods directory.
3. Start a world or join a server with this mod installed on the server. The importing player must have operator permission level 2.
4. Press M. Set X/Y/Z to the support-block coordinate of the first lane's first note block, or click **Use my current position**.
5. Click Import MIDI and select a file (maximum 512 KiB).
6. After the server confirms import, choose either playback backend.

## Safety and limitations

- Upload size is limited to 512 KiB. The server validates the MIDI header, extension, basename, world height and world-border range.
- Only PPQ-timed Standard MIDI Format 0/1 files are supported. SMPTE time division is rejected.
- The parser converts absolute MIDI time to 50 ms Minecraft ticks. Very long files and files with over 100,000 note-on events are rejected.
- Vanilla note blocks cannot represent MIDI velocity or note-off duration. Each note-on is a one-tick trigger; repeated identical pitches in the same part on the same game tick are coalesced by the physical backend.
- A part is defined as a Track + Channel pair.
- The playsound backend approximates GM instruments with a handful of vanilla timbres, maps velocity to volume, and folds MIDI pitch into the vanilla sound pitch range.
- Make a world backup before testing generated structures.
