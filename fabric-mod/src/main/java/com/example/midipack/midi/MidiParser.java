package com.example.midipack.midi;

import javax.sound.midi.*;
import java.io.File;
import java.util.*;

/** Parses SMF 0/1, retaining source Track and converting PPQ time to 50 ms game ticks. */
public final class MidiParser {
    private static final int MAX_NOTE_EVENTS = 25_000;
    private static final int MAX_GAME_TICK = 2_000_000;
    private MidiParser() {}
    private record Raw(long tick, int track, int index, MidiMessage message) {}

    public static List<NoteEvent> parse(File file) throws Exception {
        int type = MidiSystem.getMidiFileFormat(file).getType();
        if (type != 0 && type != 1) throw new IllegalArgumentException("仅支持 Standard MIDI Format 0/1。");
        Sequence sequence = MidiSystem.getSequence(file);
        if (sequence.getDivisionType() != Sequence.PPQ || sequence.getResolution() <= 0)
            throw new IllegalArgumentException("仅支持 PPQ 时间基准；SMPTE MIDI 暂不支持。");

        int resolution = sequence.getResolution();
        List<Raw> raw = new ArrayList<>();
        Track[] tracks = sequence.getTracks();
        for (int ti = 0; ti < tracks.length; ti++)
            for (int ei = 0; ei < tracks[ti].size(); ei++) {
                MidiEvent e = tracks[ti].get(ei);
                raw.add(new Raw(e.getTick(), ti, ei, e.getMessage()));
            }
        raw.sort(Comparator.comparingLong(Raw::tick)
                .thenComparingInt(e -> isTempo(e.message()) ? 0 : 1)
                .thenComparingInt(Raw::track).thenComparingInt(Raw::index));

        // Program changes are MIDI-channel scoped across the sequence.
        int[] programs = new int[16];
        double microsPerTick = 500_000.0 / resolution;
        long lastTick = 0;
        double elapsedMicros = 0;
        List<NoteEvent> notes = new ArrayList<>();
        for (Raw e : raw) {
            elapsedMicros += (e.tick() - lastTick) * microsPerTick;
            lastTick = e.tick();
            MidiMessage message = e.message();
            if (message instanceof MetaMessage meta && meta.getType() == 0x51) {
                byte[] d = meta.getData();
                if (d.length == 3) {
                    int tempo = ((d[0] & 255) << 16) | ((d[1] & 255) << 8) | (d[2] & 255);
                    if (tempo > 0) microsPerTick = (double) tempo / resolution;
                }
            } else if (message instanceof ShortMessage sm) {
                int ch = sm.getChannel();
                if (sm.getCommand() == ShortMessage.PROGRAM_CHANGE) programs[ch] = sm.getData1();
                else if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                    long tick = Math.round(elapsedMicros / 50_000.0);
                    if (tick > MAX_GAME_TICK)
                        throw new IllegalArgumentException("MIDI 时长超过限制（约 27.7 小时）。");
                    notes.add(new NoteEvent((int) tick, e.track(), ch, programs[ch],
                            sm.getData1(), sm.getData2()));
                    if (notes.size() > MAX_NOTE_EVENTS)
                        throw new IllegalArgumentException("音符事件过多；线性音符盒轨道最多支持 " + MAX_NOTE_EVENTS + " 个事件。");
                }
            }
        }
        notes.sort(Comparator.comparingInt(NoteEvent::mcTick).thenComparingInt(NoteEvent::track)
                .thenComparingInt(NoteEvent::channel).thenComparingInt(NoteEvent::midiNote));
        if (notes.isEmpty()) throw new IllegalArgumentException("MIDI 文件中没有有效的 Note On 事件。");
        return List.copyOf(notes);
    }
    private static boolean isTempo(MidiMessage m) {
        return m instanceof MetaMessage meta && meta.getType() == 0x51;
    }
}
