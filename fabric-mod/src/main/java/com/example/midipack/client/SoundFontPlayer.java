package com.example.midipack.client;

import javax.sound.midi.*;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-only local MIDI synthesizer using Java Sound. SoundFont loading is
 * delegated to the installed Java Sound Synthesizer provider (SF2/SF3 support varies by runtime).
 */
public final class SoundFontPlayer {
    private static Synthesizer synth;
    private static Sequencer sequencer;
    private static Soundbank loadedBank;
    private static int volume = 90;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private SoundFontPlayer() {}

    public static synchronized void loadSoundFont(File file) throws Exception {
        ensureInitialized();
        Soundbank bank = MidiSystem.getSoundbank(file);
        if (bank == null) throw new InvalidMidiDataException("Unsupported SoundFont file.");
        if (loadedBank != null) synth.unloadAllInstruments(loadedBank);
        if (!synth.loadAllInstruments(bank)) throw new IllegalStateException("Synthesizer rejected this SoundFont.");
        loadedBank = bank;
        applyVolume();
    }

    public static synchronized void loadMidi(File file) throws Exception {
        ensureInitialized();
        Sequence sequence = MidiSystem.getSequence(file);
        if (sequence.getDivisionType() != Sequence.PPQ)
            throw new InvalidMidiDataException("Only PPQ MIDI files are supported.");
        sequencer.setSequence(sequence);
    }

    public static synchronized void play() {
        ensureInitialized();
        if (sequencer.getSequence() == null) throw new IllegalStateException("Load a MIDI file first.");
        if (sequencer.isRunning()) sequencer.stop();
        sequencer.setTickPosition(0);
        sequencer.start();
    }

    public static synchronized void stop() {
        if (sequencer != null && sequencer.isRunning()) sequencer.stop();
        if (synth != null) for (MidiChannel ch : synth.getChannels()) if (ch != null) ch.allNotesOff();
    }

    public static synchronized void setVolume(int value) {
        volume = Math.max(0, Math.min(127, value));
        applyVolume();
    }

    public static synchronized boolean isReady() {
        return initialized.get() && sequencer != null && sequencer.getSequence() != null;
    }

    private static void ensureInitialized() {
        if (initialized.get()) return;
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
            sequencer = MidiSystem.getSequencer(false);
            sequencer.open();
            sequencer.getTransmitter().setReceiver(synth.getReceiver());
            initialized.set(true);
        } catch (MidiUnavailableException e) {
            throw new IllegalStateException("Java Sound MIDI synthesizer is unavailable in this runtime.", e);
        }
    }

    private static void applyVolume() {
        if (synth != null && synth.isOpen()) {
            for (MidiChannel channel : synth.getChannels()) if (channel != null) channel.controlChange(7, volume);
        }
    }
}
