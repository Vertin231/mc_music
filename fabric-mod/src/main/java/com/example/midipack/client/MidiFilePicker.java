package com.example.midipack.client;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opens the operating system's native file chooser without blocking the render thread. */
public final class MidiFilePicker {
    private MidiFilePicker() {}

    public static void open() {
        Thread thread = new Thread(() -> SwingUtilities.invokeLater(() -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select a MIDI file");
            chooser.setFileFilter(new FileNameExtensionFilter("MIDI files (*.mid, *.midi)", "mid", "midi"));
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
            Path selected = chooser.getSelectedFile().toPath();
            try {
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
        }), "midipack-file-picker");
        thread.setDaemon(true);
        thread.start();
    }
}
