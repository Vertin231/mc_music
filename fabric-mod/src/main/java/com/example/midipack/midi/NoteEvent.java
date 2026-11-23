package com.example.midipack.midi;

/** A MIDI note-on event converted to an absolute Minecraft game tick. */
public record NoteEvent(int mcTick, int channel, int program, int midiNote, int velocity) {
    public NoteEvent {
        if (mcTick < 0 || channel < 0 || channel > 15 || program < 0 || program > 127
                || midiNote < 0 || midiNote > 127 || velocity < 1 || velocity > 127) {
            throw new IllegalArgumentException("Invalid MIDI note event");
        }
    }
}
