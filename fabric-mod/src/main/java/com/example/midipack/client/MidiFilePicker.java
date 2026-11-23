package com.example.midipack.client;

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opens the operating-system native file dialog on AWT's event thread. */
public final class MidiFilePicker {
    private MidiFilePicker() {}

    public static void open() {
        EventQueue.invokeLater(() -> {
            Frame owner = new Frame();
            FileDialog dialog = new FileDialog(owner, "Select a MIDI file", FileDialog.LOAD);
            dialog.setFilenameFilter((directory, name) -> {
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                return lower.endsWith(".mid") || lower.endsWith(".midi");
            });
            try {
                dialog.setVisible(true);
                String filename = dialog.getFile();
                String directory = dialog.getDirectory();
                if (filename == null || directory == null) return;
                Path selected = Path.of(directory, filename);
                long size = Files.size(selected);
                if (size <= 0 || size > MidiPackClient.MAX_MIDI_BYTES) {
                    MidiPackClient.notifyUser("MIDI 文件必须大于 0 且不超过 512 KiB。");
                    return;
                }
                MidiPackClient.upload(selected.getFileName().toString(), Files.readAllBytes(selected));
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                MidiPackClient.notifyUser("读取 MIDI 失败：" + message);
            } finally {
                dialog.dispose();
                owner.dispose();
            }
        });
    }
}
