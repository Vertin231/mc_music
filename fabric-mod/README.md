# MIDI Pack — Fabric 1.20.4 (0.1.3)

MIDI Pack is a client GUI and server-side MIDI-to-datapack importer for Minecraft Java 1.20.4, Fabric, and Java 17.

## Features

- Press **M** to open the in-game MIDI Pack screen.
- Enter a custom X/Y/Z placement origin, or use the current player block position.
- Browse and import .mid / .midi files using the in-game file browser. The default folder is `<game directory>/music`; you can navigate folders or paste an absolute file/folder path. No AWT desktop dialog is used.
- Transfer the selected file and placement coordinates to the integrated or dedicated server using a Fabric custom payload.
- Parse Standard MIDI Format 0/1 with Java's built-in `javax.sound.midi`, including PPQ tempo changes and GM program changes.
- Retain source Track and MIDI Channel. Physical parts are split by **Track + Channel**.
- Generate two datapacks:
  - **playsound** — maps MIDI pitches to note-block sound events; velocity controls volume.
  - **physical note blocks** — builds an actual redstone repeater chain with redstone dust branches and note blocks. No per-note `setblock redstone_block` commands are used.
- Every Track + Channel gets an independent physical lane. Simultaneous notes in a chord are powered in parallel by one dust branch.
- Play and Stop controls are available. There is no pause/resume system.

## Physical redstone layout

The selected origin `(x, y, z)` is the support block beneath the first repeater of the first lane.

- The first repeater is at `(x, y+1, z)`, facing +X.
- A single redstone-block pulse at `(x-1, y+1, z)` starts the physical chain. The datapack removes this input block after 2 game ticks; all subsequent note triggers are carried by repeaters and redstone dust.
- Repeaters are placed every 3 blocks along +X. Their delay settings encode the elapsed time between MIDI event groups; long gaps use additional repeater stages.
- A powered relay block and a continuous redstone-dust bus trigger the note blocks in parallel. A redstone lamp is placed beside each event bus and lights when that event pulse arrives, providing a visual beat indicator.
- Every instrument-base block has a solid stone support block underneath it. This prevents gravity-affected timbres such as sand (snare) from falling before the note block is placed. Transparent instrument bases such as glass are also supported.
- Parts are laid out in parallel along +Z. Lane spacing expands to accommodate the largest chord in the imported file.
- Chords of up to **28 simultaneous notes** are supported (14 dust branches on each side of the lane). Each event stage also has a redstone-lamp indicator.

### Timing limits

Vanilla repeaters have settings of 1–4 redstone ticks, equivalent to 2–8 Minecraft game ticks. To ensure a pulse can turn off before the next one arrives, the input pulse is held for 4 game ticks and the physical backend enforces at least **6 game ticks (300 ms)** between distinct event groups in the same part. Gaps are rounded upward to an even number of game ticks; longer gaps are represented by multiple repeaters. Fast MIDI passages can therefore be stretched. The playsound backend retains its separate game-tick scheduler and is not subject to this physical-circuit quantization.

Vanilla note blocks are one-shot/percussive. MIDI note-off duration and velocity cannot be represented by the physical note-block backend. GM programs are approximated by vanilla note-block timbres, and pitches are folded into the vanilla note-block range.

## Limits

- MIDI upload: 512 KiB.
- Maximum physical note events: 25,000.
- Maximum generated repeaters: 50,000 total.
- Maximum Track + Channel lanes: 64.
- Maximum simultaneous chord size: 28 notes.
- Only PPQ-timed Standard MIDI Format 0/1 files are supported; SMPTE time division is rejected.

## Build

Requirements: JDK 17 and Gradle 8.6.

```powershell
gradle build --no-daemon
```

The mod jars are written to `build/libs/`.

## Install and use

1. Install Fabric Loader for Minecraft 1.20.4 and Fabric API.
2. Copy `midipack-0.1.3.jar` into the instance's `mods` directory.
3. Start a world or join a server with this mod installed on the server. The importing player must have operator permission level 2.
4. Press **M** and set the origin coordinates, or use the current player position.
5. Click **Import MIDI**, select a .mid / .midi file, and import it.
6. Run the generated function shown in chat, or use the GUI's Play control.

**Important:** back up the world and choose a clear build area. The setup function uses `setblock ... keep`, so it will not overwrite existing blocks, but any occupied position can prevent part of the circuit from being placed. The redstone lamp is a visual event indicator: it lights while the corresponding dust bus is powered. The redstone input block at `(x-1, y+1, z)` is replaced when playback starts.

Stopping removes the input pulse source. A pulse that has already entered a repeater chain may continue through the remaining stages, so a few in-flight notes can still play after Stop is pressed.
