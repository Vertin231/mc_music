package com.example.midipack.client;

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opens the operating system native file dialog without blocking the render thread. */
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
            dialog.setVisible(true);
            String filename = dialog.getFile();
            String directory = dialog.getDirectory();
            dialog.dispose();
            owner.dispose();
            if (filename == null || directory == null) return;

            try {
                Path selected = Path.of(directory, filename);
                long size = Files.size(selected);
                if (size <= 0 || size > 512 * 1024) {
                    MidiPackClient.notifyUser("MIDI 文件必须大于 0 且不超过 512 KiB。");
                    return;
                }
                byte[] bytes = Files.readAllBytes(selected);
                MidiPackClient.upload(selected.getFileName().toString(), bytes);
            } catch (Exception e) {
                MidiPackClient.notifyUser("读取 MIDI 失败：" + e.getMessage());
            }
        });
    }
}
