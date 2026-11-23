# MIDI Pack — Fabric 1.20.4

MIDI Pack is a client GUI and server-side MIDI-to-datapack importer for Minecraft Java 1.20.4, Fabric, and Java 17.

## Features

- Press **M** to open the in-game MIDI Pack screen.
- Enter a custom X/Y/Z placement origin, or use the current player block position.
- Import a .mid / .midi file through the operating system's native file dialog.
- Transfer the selected file and placement coordinates to the integrated or dedicated server using a Fabric custom payload.
- Parse Standard MIDI Format 0/1 with Java's built-in javax.sound.midi, including PPQ tempo changes and GM program changes.
- Generate two independent datapacks in the current world's datapacks directory:
  - **playsound** — maps MIDI pitches to note-block sound events; velocity controls volume.
  - **note blocks** — lays out every required instrument/pitch station in one straight row along the positive X axis. Each station has its note block and a neighboring redstone-block trigger.
- Play, pause, resume, and stop both backends from the GUI.

## Build

Requirements: JDK 17 and Gradle 8.6.

    gradle build --no-daemon

The mod jar is written to build/libs/.

## Install and use

1. Install Fabric Loader for Minecraft 1.20.4 and Fabric API.
2. Copy the mod jar into the game's mods directory.
3. Start a world or join a server with this mod installed on the server. The importing player must have operator permission level 2.
4. Press M. Set X/Y/Z to the support-block coordinate of the first note block, or click **Use my current position**.
5. Click Import MIDI and select a file (maximum 512 KiB).
6. After the server confirms import, choose either playback backend.

The physical layout starts at the selected support-block coordinate. Note blocks are placed one block above their support blocks and extend along +X, with 3 blocks between stations. Reserve a clear strip of roughly 770 blocks in the +X direction and two blocks of vertical clearance. The playsound backend does not use the placement coordinates.

## Safety and limitations

- Upload size is limited to 512 KiB. The server validates the MIDI header, extension, basename, world height and world-border range.
- Only PPQ-timed Standard MIDI Format 0/1 files are supported. SMPTE time division is rejected.
- The parser converts absolute MIDI time to 50 ms Minecraft ticks. Very long files and files with over 100,000 note-on events are rejected.
- Vanilla note blocks cannot represent MIDI velocity or note-off duration. Each note-on is a one-tick trigger; repeated identical pitches on the same game tick are coalesced by the physical note-block backend.
- The physical layout uses one straight X-axis row rather than a 2D grid. It uses individual redstone-block pulses at each note station, not a continuous redstone-dust bus.
- The playsound backend is an approximation: GM instruments are grouped into a handful of vanilla note-block timbres, MIDI velocity maps to volume, and MIDI pitch is folded into the vanilla sound pitch range.
- Make a world backup before testing generated structures.
