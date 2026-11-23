# MIDI Pack — Fabric 1.20.4

MIDI Pack is a client GUI and server-side MIDI-to-datapack importer for Minecraft Java 1.20.4, Fabric, and Java 17.

## Features

- Press **M** to open the in-game MIDI Pack screen.
- Enter a custom X/Y/Z placement origin, or use the current player block position.
- Browse and import .mid / .midi files using the in-game file browser (no AWT desktop dialog required). The default folder is `<game directory>/music`; you can navigate folders or paste an absolute file/folder path.
- Transfer the selected file and placement coordinates to the integrated or dedicated server using a Fabric custom payload.
- Parse Standard MIDI Format 0/1 with Java's built-in javax.sound.midi, including PPQ tempo changes and GM program changes.
- Retain source Track and MIDI Channel. Physical parts are split by **Track + Channel**:
  - MIDI 1: source tracks are separated; multiple channels inside one source track are split into separate lanes.
  - MIDI 0: its single track is split by MIDI Channel.
- Generate two datapacks:
  - **playsound** — maps MIDI pitches to note-block sound events; velocity controls volume.
  - **note blocks** — builds one chronological straight +X lane per Track + Channel. Each MIDI note event gets its own note-block station; lanes run parallel along +Z.
- All note-block lanes share one global 50 ms Minecraft game-tick clock. Chords and notes from different parts at the same MIDI time are triggered in the same game tick.
- Playback controls are deliberately simple: **Play** and **Stop**. There is no pause/resume system.

## Physical note-block lane layout

The chosen origin is the support-block coordinate of the first event station in the first part.

For part lane index j and note-event index i within that part:

| Block | Coordinate |
|---|---|
| Support block | (x + 3i, y, z + 3j) |
| Note block | (x + 3i, y + 1, z + 3j) |
| Redstone pulse block | (x + 3i + 1, y + 1, z + 3j) |

- Parts are ordered by ascending source Track index, then MIDI Channel.
- Events within each part are ordered by absolute MIDI time, then pitch.
- Each lane runs along +X; parallel lanes are separated by 3 blocks along +Z.
- Simultaneous notes occupy adjacent event stations and are triggered together, so chords are preserved.
- A datapack scoreboard clock dispatches events at 1 Minecraft game tick (50 ms) resolution. Each event station receives a one-tick redstone-block pulse.
- This is a chronological linear row of note-block stations, not a continuous redstone-dust/repeater bus. Repeaters have a minimum delay of 2 game ticks, which would lose the 1-game-tick timing resolution used by the MIDI clock.

The note-block backend supports up to **25,000 note events total** and **64 Track + Channel lanes**. The +X length is based on the longest part; the +Z width is 3 blocks per part. The playsound backend does not use the placement coordinates.

## Build

Requirements: JDK 17 and Gradle 8.6.

    gradle build --no-daemon

The mod jar is written to build/libs/.

## Install and use

1. Install Fabric Loader for Minecraft 1.20.4 and Fabric API.
2. Copy the mod jar into the game's mods directory.
3. Start a world or join a server with this mod installed on the server. The importing player must have operator permission level 2.
4. Press M. Set X/Y/Z to the support-block coordinate of the first lane's first station, or click **Use my current position**.
5. Click Import MIDI and browse to a file and import it (maximum 512 KiB). The default folder is `<game directory>/music`; the path field also accepts an absolute path.
6. After the server confirms import, choose **Play · playsound** or **Play · note blocks**. Use **Stop both** to stop playback and clear redstone pulse blocks.

## Safety and limitations

- Upload size is limited to 512 KiB. The server validates the MIDI header, extension, basename, world height and world-border range.
- Only PPQ-timed Standard MIDI Format 0/1 files are supported. SMPTE time division is rejected.
- The parser converts absolute MIDI time to 50 ms Minecraft game ticks. Files longer than the configured timeline or containing over 25,000 note-on events are rejected.
- Vanilla note blocks cannot represent MIDI velocity or note-off duration. Each note-on is a one-game-tick trigger.
- A physical part is defined as a Track + Channel pair.
- The note-block backend maps GM instruments approximately to vanilla note-block timbres and folds MIDI pitch into the vanilla note-block pitch range.
- The playsound backend approximates GM instruments with a handful of vanilla timbres, maps velocity to volume, and folds MIDI pitch into the vanilla sound pitch range.
- Make a world backup before testing generated structures. Choose a clear area: setup uses `setblock ... keep` so it does not overwrite existing blocks.
