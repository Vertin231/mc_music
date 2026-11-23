package com.example.midipack.midi;

import javax.sound.midi.*;
import java.io.File;
import java.util.*;

/** Standard MIDI parser using only Java 17 javax.sound.midi. */
public final class MidiParser {
    private MidiParser() {}

    private record Raw(long tick, int track, int index, MidiMessage message) {}

    public static List<NoteEvent> parse(File file) throws Exception {
        Sequence sequence = MidiSystem.getSequence(file);
        if (sequence.getDivisionType() != Sequence.PPQ || sequence.getResolution() <= 0) {
            throw new IllegalArgumentException("Only PPQ-timed Standard MIDI files are supported.");
        }
        int resolution = sequence.getResolution();
        List<Raw> raw = new ArrayList<>();
        Track[] tracks = sequence.getTracks();
        for (int t = 0; t < tracks.length; t++) {
            for (int i = 0; i < tracks[t].size(); i++) {
                MidiEvent event = tracks[t].get(i);
                raw.add(new Raw(event.getTick(), t, i, event.getMessage()));
            }
        }
        // Tempo meta-events at a shared tick are applied before notes at that tick.
        raw.sort(Comparator.comparingLong(Raw::tick)
                .thenComparingInt(r -> isTempo(r.message()) ? 0 : 1)
                .thenComparingInt(Raw::track).thenComparingInt(Raw::index));

        int[] programs = new int[16];
        double microsPerTick = 500_000.0 / resolution;
        long lastTick = 0;
        double elapsedMicros = 0.0;
        List<NoteEvent> result = new ArrayList<>();

        for (Raw event : raw) {
            long delta = event.tick() - lastTick;
            elapsedMicros += delta * microsPerTick;
            lastTick = event.tick();
            MidiMessage message = event.message();

            if (message instanceof MetaMessage meta && meta.getType() == 0x51) {
                byte[] data = meta.getData();
                if (data.length == 3) {
                    int microsPerQuarter = ((data[0] & 255) << 16)
                            | ((data[1] & 255) << 8) | (data[2] & 255);
                    if (microsPerQuarter > 0) microsPerTick = (double) microsPerQuarter / resolution;
                }
            } else if (message instanceof ShortMessage sm) {
                int channel = sm.getChannel();
                if (sm.getCommand() == ShortMessage.PROGRAM_CHANGE) {
                    programs[channel] = sm.getData1();
                } else if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                    int tick = (int) Math.round(elapsedMicros / 50_000.0);
                    result.add(new NoteEvent(tick, channel, programs[channel],
                            sm.getData1(), sm.getData2()));
                }
            }
        }
        result.sort(Comparator.comparingInt(NoteEvent::mcTick)
                .thenComparingInt(NoteEvent::channel).thenComparingInt(NoteEvent::midiNote));
        if (result.isEmpty()) throw new IllegalArgumentException("No note-on events found in MIDI file.");
        return List.copyOf(result);
    }

    private static boolean isTempo(MidiMessage message) {
        return message instanceof MetaMessage meta && meta.getType() == 0x51;
    }
}
