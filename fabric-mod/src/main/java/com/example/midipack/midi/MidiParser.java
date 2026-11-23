package com.example.midipack.midi;

import javax.sound.midi.*;
import java.io.File;
import java.util.*;

/** Parses SMF 0/1, retaining note duration and converting PPQ time to 50 ms game ticks. */
public final class MidiParser {
    private static final int MAX_NOTE_EVENTS = 25_000;
    private static final int MAX_GAME_TICK = 2_000_000;
    private MidiParser() {}
    private record Raw(long tick, int track, int index, MidiMessage message) {}
    private record Pending(long startTick, int mcTick, int track, int channel, int program, int pitch, int velocity) {}

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

        int[] programs = new int[16];
        double microsPerTick = 500_000.0 / resolution;
        long lastTick = 0;
        double elapsedMicros = 0;
        List<NoteEvent> notes = new ArrayList<>();
        Map<Integer, ArrayDeque<Pending>> pending = new HashMap<>();
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
                    int startTick = safeGameTick(elapsedMicros);
                    int key = (ch << 8) | sm.getData1();
                    pending.computeIfAbsent(key, ignored -> new ArrayDeque<>())
                            .addLast(new Pending(e.tick(), startTick, e.track(), ch, programs[ch], sm.getData1(), sm.getData2()));
                } else if (sm.getCommand() == ShortMessage.NOTE_OFF
                        || (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() == 0)) {
                    int key = (ch << 8) | sm.getData1();
                    ArrayDeque<Pending> queue = pending.get(key);
                    if (queue != null && !queue.isEmpty()) {
                        Pending start = queue.removeFirst();
                        int endTick = safeGameTick(elapsedMicros);
                        notes.add(new NoteEvent(start.mcTick(), Math.max(0, endTick - start.mcTick()),
                                start.track(), start.channel(), start.program(), start.pitch(), start.velocity()));
                    }
                }
            }
        }
        // MIDI notes without a Note Off are treated as short notes.
        for (ArrayDeque<Pending> queue : pending.values()) for (Pending start : queue) {
            notes.add(new NoteEvent(start.mcTick(), 1, start.track(), start.channel(), start.program(), start.pitch(), start.velocity()));
        }
        notes.sort(Comparator.comparingInt(NoteEvent::mcTick).thenComparingInt(NoteEvent::track)
                .thenComparingInt(NoteEvent::channel).thenComparingInt(NoteEvent::midiNote));
        if (notes.isEmpty()) throw new IllegalArgumentException("MIDI 文件中没有有效的 Note On 事件。");
        if (notes.size() > MAX_NOTE_EVENTS)
            throw new IllegalArgumentException("音符事件过多；最多支持 " + MAX_NOTE_EVENTS + " 个。");
        return List.copyOf(notes);
    }
    private static int safeGameTick(double elapsedMicros) {
        long tick = Math.round(elapsedMicros / 50_000.0);
        if (tick > MAX_GAME_TICK) throw new IllegalArgumentException("MIDI 时长超过限制（约 27.7 小时）。");
        return (int) tick;
    }
    private static boolean isTempo(MidiMessage m) {
        return m instanceof MetaMessage meta && meta.getType() == 0x51;
    }
}
