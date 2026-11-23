# Fabric multi-version port

This branch is the integration branch for the multi-version Fabric port.

## Target versions

The three new release targets are:

| Minecraft | Track | Fabric API |
| --- | --- | --- |
| 26.1.2 | 26.1 release line | 0.155.3+26.1.2 |
| 26.2 | 26.2 release line | 0.161.0+26.2 |
| 26.3 | 26.3 release line | 0.166.2+26.3 |

The existing `fabric-midi-gui-1.20.4` branch remains the maintained 1.20.4 / Java 17 build.

## Toolchain

The 26.x line uses Java 25 and the current Fabric Loom no-remap plugin
(`net.fabricmc.fabric-loom`). Minecraft 26.x uses unobfuscated Mojang names;
the 1.20.4 source currently uses Yarn names and cannot be made compatible by
changing version properties alone.

For 26.3, the current Fabric example project recommends Loader 0.20.1,
Loom 1.19-SNAPSHOT, and Fabric API 0.166.2+26.3. Loader 0.19.5 should not be
assumed to be the recommended baseline for a new 26.3 build. The mod metadata
will declare the minimum Loader version only after the selected toolchain and
runtime compatibility have been verified.

## Porting requirements

- Keep MIDI parsing, event ordering, Track + Channel part grouping, and the
  datapack generation algorithms independent of Minecraft classes.
- Port the Minecraft-facing networking, GUI, command source, identifiers,
  text components, and block-position adapters to the official 26.x names.
- Produce one version-specific JAR per target. Do not publish one 26.3 JAR as
  compatible with 26.1/26.2 without runtime verification.
- Keep the 1.20.4 implementation and artifact independent.
- CI must build every target and upload artifacts separately. A green compile
  is not a substitute for in-game playback testing.
