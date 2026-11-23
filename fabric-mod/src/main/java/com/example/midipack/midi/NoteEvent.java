package com.example.midipack.midi;

/** MIDI note-on with duration in 50 ms Minecraft game ticks. */
public record NoteEvent(int mcTick, int durationTicks, int track, int channel, int program, int midiNote, int velocity) {
    /** Compatibility constructor for callers/tests that don't provide duration. */
    public NoteEvent(int mcTick, int track, int channel, int program, int midiNote, int velocity) {
        this(mcTick, 1, track, channel, program, midiNote, velocity);
    }
    public NoteEvent {
        if (mcTick < 0 || durationTicks < 1 || track < 0 || channel < 0 || channel > 15 || program < 0 || program > 127
                || midiNote < 0 || midiNote > 127 || velocity < 1 || velocity > 127)
            throw new IllegalArgumentException("Invalid MIDI note event");
    }
}
