package com.example.midipack.midi;

import javax.sound.midi.*;
import java.io.File;
import java.util.*;

/** Parses SMF 0/1 using Java's built-in MIDI API and converts time to 50 ms game ticks. */
public final class MidiParser {
    private static final int MAX_NOTE_EVENTS = 100_000;
    private static final int MAX_GAME_TICK = 2_000_000;
    private MidiParser() {}

    private record Raw(long tick, int track, int index, MidiMessage message) {}

    public static List<NoteEvent> parse(File file) throws Exception {
        int type = MidiSystem.getMidiFileFormat(file).getType();
        if (type != 0 && type != 1) {
            throw new IllegalArgumentException("仅支持 Standard MIDI Format 0/1。");
        }
        Sequence sequence = MidiSystem.getSequence(file);
        if (sequence.getDivisionType() != Sequence.PPQ || sequence.getResolution() <= 0) {
            throw new IllegalArgumentException("仅支持 PPQ 时间基准；SMPTE MIDI 暂不支持。");
        }

        int resolution = sequence.getResolution();
        List<Raw> raw = new ArrayList<>();
        Track[] tracks = sequence.getTracks();
        for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
            Track track = tracks[trackIndex];
            for (int eventIndex = 0; eventIndex < track.size(); eventIndex++) {
                MidiEvent event = track.get(eventIndex);
                raw.add(new Raw(event.getTick(), trackIndex, eventIndex, event.getMessage()));
            }
        }
        raw.sort(Comparator.comparingLong(Raw::tick)
                .thenComparingInt(event -> isTempo(event.message()) ? 0 : 1)
                .thenComparingInt(Raw::track).thenComparingInt(Raw::index));

        int[] programs = new int[16];
        double microsPerTick = 500_000.0 / resolution;
        long lastTick = 0;
        double elapsedMicros = 0.0;
        List<NoteEvent> notes = new ArrayList<>();

        for (Raw event : raw) {
            elapsedMicros += (event.tick() - lastTick) * microsPerTick;
            lastTick = event.tick();
            MidiMessage message = event.message();

            if (message instanceof MetaMessage meta && meta.getType() == 0x51) {
                byte[] data = meta.getData();
                if (data.length == 3) {
                    int microsPerQuarter = ((data[0] & 0xff) << 16)
                            | ((data[1] & 0xff) << 8) | (data[2] & 0xff);
                    if (microsPerQuarter > 0) microsPerTick = (double) microsPerQuarter / resolution;
                }
            } else if (message instanceof ShortMessage sm) {
                int channel = sm.getChannel();
                if (sm.getCommand() == ShortMessage.PROGRAM_CHANGE) {
                    programs[channel] = sm.getData1();
                } else if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                    long gameTick = Math.round(elapsedMicros / 50_000.0);
                    if (gameTick > MAX_GAME_TICK) {
                        throw new IllegalArgumentException("MIDI 时长超过限制（约 27.7 小时）。");
                    }
                    notes.add(new NoteEvent((int) gameTick, channel, programs[channel],
                            sm.getData1(), sm.getData2()));
                    if (notes.size() > MAX_NOTE_EVENTS) {
                        throw new IllegalArgumentException("音符事件过多，最多支持 " + MAX_NOTE_EVENTS + " 个。");
                    }
                }
            }
        }

        notes.sort(Comparator.comparingInt(NoteEvent::mcTick)
                .thenComparingInt(NoteEvent::channel).thenComparingInt(NoteEvent::midiNote));
        if (notes.isEmpty()) throw new IllegalArgumentException("MIDI 文件中没有有效的 Note On 事件。");
        return List.copyOf(notes);
    }

    private static boolean isTempo(MidiMessage message) {
        return message instanceof MetaMessage meta && meta.getType() == 0x51;
    }
}
