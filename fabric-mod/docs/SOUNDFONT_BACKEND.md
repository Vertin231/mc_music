# SoundFont backend design notes (0.1.5+)

## Why external SoundFonts?

The current datapack backends use Minecraft note-block samples and /playsound; they cannot faithfully express arbitrary MIDI instruments, note-off envelopes, sustain pedal, or SF2 program/bank selection. Re-triggering attacks is only a percussive sustain approximation.


## Recommended architecture: optional local client synthesizer

Keep the existing backends:

- **Physical note blocks** — redstone structure in the world; approximate sustain via repeated trigger pulses.
- **Vanilla playsound** — datapack playback using Minecraft's built-in note-block sound palette.

Add a separate, opt-in **SoundFont (client playback)** mode. The first implementation uses Java Sound's provider and therefore relies on runtime support for the chosen SoundFont format:

- Load user-selected SoundFont from local disk (SF2 is the recommended format); do not bundle copyrighted soundbanks by default.
- MIDI program changes, bank selection, Note On/Off, velocity and controller events are sent to the synthesizer, preserving note durations.
- Playback is local to the player client and will not generate a redstone structure or be audible to other players automatically.
- The first UI exposes local path fields for the SoundFont and MIDI, plus play/stop and volume; files are loaded only on the client.

## Engine candidates

### Java Sound MIDI Synthesizer (first feasibility prototype)

Try the Java MIDI Synthesizer/Soundbank APIs and verify SF2 loading in the exact Java runtime shipped with Minecraft. This may avoid native dependencies, but availability and soundbank support can vary by runtime/provider; do not assume it works until tested on supported Windows/Linux clients.

### FluidSynth (quality/robustness option)

FluidSynth is a native, cross-platform SoundFont synthesizer with SF2/SF3 and real-time MIDI support. It is a strong technical choice, but adds native-library packaging and maintenance across OS/architecture combinations. Treat it as a later optional engine rather than a hard dependency.

### SpessaSynth (not a direct Fabric Java dependency)

SpessaSynth is a JavaScript/WebAudio synthesizer. Its rich SF2/SF3/DLS support makes it interesting for a browser-based preview tool, but it is not a drop-in library for this Java Fabric mod.


## Soundbank suggestions (user-imported)

- **GeneralUser GS** — a commonly recommended GM SoundFont; check the exact release's license and redistribution terms before bundling.
- **FluidR3 GM** — widely supported GM bank; check the particular copy/edition's license and provenance before redistribution.
- **Open minimalistic FM/GM SoundFont** — useful as a small experimental option, but FM-synth timbres are intentionally synthetic rather than sampled-realistic.


## Prototype limitations\n\n- This first implementation is client-local and does not sync to other players.\n- Java Sound support for SF2 depends on the synthesizer provider in the bundled Java runtime. Unsupported formats or providers report an error.\n- GUI path entry is manual; no native desktop file dialog is used (to avoid AWT headless failures).\n- Program-change behavior and playback fidelity still need runtime testing on Windows with Minecraft's Java runtime.\n\n## Proposed implementation order

1. Prototype loading a user-selected SF2 into Java Sound and report provider, bank name, preset count, and load failures.
2. Build a small client-side test that plays a few notes and verifies Note Off / velocity / program changes.
3. Benchmark memory, audio latency, and CPU on a typical Minecraft client.
4. If Java Sound is inadequate, evaluate a packaged FluidSynth native backend.
5. Keep external banks user-selected; include only soundbanks with confirmed redistribution licenses.

## References

- FluidSynth overview and SF2/SF3/DLS support: https://www.fluidsynth.org/
- FluidSynth SoundFont resources: https://www.fluidsynth.org/wiki/SoundFont/
- SpessaSynth library: https://github.com/spessasynth/SpessaSynth_lib
- Example open FM/GM SoundFont (GPL-3.0-only): https://github.com/zeittresor/opensoundfont
