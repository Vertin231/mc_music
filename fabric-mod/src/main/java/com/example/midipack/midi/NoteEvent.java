package com.example.midipack.midi;

/** A MIDI note-on event with its source track/channel and absolute Minecraft game tick. */
public record NoteEvent(int mcTick, int track, int channel, int program, int midiNote, int velocity) {
    public NoteEvent {
        if (mcTick < 0 || track < 0 || channel < 0 || channel > 15 || program < 0 || program > 127
                || midiNote < 0 || midiNote > 127 || velocity < 1 || velocity > 127)
            throw new IllegalArgumentException("Invalid MIDI note event");
    }
}
