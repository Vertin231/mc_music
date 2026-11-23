package com.example.midipack.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * In-game MIDI file browser. This deliberately avoids java.awt so it works
 * when the launcher starts Minecraft with java.awt.headless=true.
 */
public final class MidiFilePickerScreen extends Screen {
    private static final int MAX_VISIBLE_ENTRIES = 7;

    private final BlockPos origin;
    private final Screen parent;
    private Path directory;
    private TextFieldWidget pathField;
    private String status = "";
    private int listedEntries;

    public MidiFilePickerScreen(BlockPos origin, Screen parent) {
        super(Text.literal("Select MIDI"));
        this.origin = origin;
        this.parent = parent;
        Path gameDir = MinecraftClient.getInstance().runDirectory.toPath();
        this.directory = gameDir.resolve("music").toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            status = "Cannot create game/music: " + safeMessage(e);
            directory = gameDir.toAbsolutePath().normalize();
        }
    }

    @Override
    protected void init() {
        clearChildren();
        int left = Math.max(8, width / 2 - 155);
        int top = Math.max(8, height / 2 - 125);

        pathField = new TextFieldWidget(textRenderer, left, top, 310, 20, Text.literal("Folder or MIDI path"));
        pathField.setMaxLength(1024);
        pathField.setText(directory.toString());
        addDrawableChild(pathField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Open path"), b -> openTypedPath())
                .dimensions(left, top + 25, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Game music folder"), b -> {
            directory = MinecraftClient.getInstance().runDirectory.toPath().resolve("music").toAbsolutePath().normalize();
            refresh();
        }).dimensions(left + 105, top + 25, 125, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Up"), b -> {
            if (directory.getParent() != null) {
                directory = directory.getParent();
                refresh();
            }
        }).dimensions(left + 235, top + 25, 75, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Import selected path"), b -> importPath())
                .dimensions(left, top + 190, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(left + 160, top + 190, 150, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), b -> refresh())
                .dimensions(left + 235, top + 50, 75, 20).build());

        populateEntries(left, top + 77);
    }

    private void populateEntries(int left, int top) {
        listedEntries = 0;
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> entries = stream
                    .filter(p -> Files.isDirectory(p) || isMidi(p))
                    .sorted(Comparator.<Path, Boolean>comparing(p -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .limit(MAX_VISIBLE_ENTRIES)
                    .toList();

            for (Path entry : entries) {
                int row = listedEntries++;
                String label = (Files.isDirectory(entry) ? "[DIR] " : "[MIDI] ")
                        + entry.getFileName();
                if (label.length() > 43) label = label.substring(0, 40) + "...";
                addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                    if (Files.isDirectory(entry)) {
                        directory = entry.toAbsolutePath().normalize();
                        refresh();
                    } else {
                        pathField.setText(entry.toAbsolutePath().toString());
                        status = "Selected: " + entry.getFileName();
                    }
                }).dimensions(left, top + row * 22, 310, 20).build());
            }
            if (entries.isEmpty()) status = "No MIDI files or folders here.";
            else if (entries.size() == MAX_VISIBLE_ENTRIES) status = "Showing first 7 entries. Type a path for others.";
        } catch (Exception e) {
            status = "Cannot list folder: " + safeMessage(e);
        }
    }

    private void openTypedPath() {
        try {
            Path typed = Path.of(pathField.getText().trim()).toAbsolutePath().normalize();
            if (Files.isDirectory(typed)) {
                directory = typed;
                refresh();
            } else if (Files.isRegularFile(typed) && isMidi(typed)) {
                pathField.setText(typed.toString());
                status = "MIDI file selected.";
            } else {
                status = "Path is not a folder or .mid/.midi file.";
            }
        } catch (Exception e) {
            status = "Invalid path: " + safeMessage(e);
        }
    }

    private void importPath() {
        try {
            Path selected = Path.of(pathField.getText().trim()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(selected) || !isMidi(selected)) {
                status = "Select an existing .mid or .midi file.";
                return;
            }
            long size = Files.size(selected);
            if (size <= 0 || size > MidiPackClient.MAX_MIDI_BYTES) {
                status = "MIDI must be between 1 byte and 512 KiB.";
                return;
            }
            byte[] data = Files.readAllBytes(selected);
            MinecraftClient.getInstance().setScreen(parent);
            MidiPackClient.upload(selected.getFileName().toString(), data, origin);
        } catch (Exception e) {
            status = "Import failed: " + safeMessage(e);
        }
    }

    private static boolean isMidi(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mid") || name.endsWith(".midi");
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void refresh() {
        status = "";
        clearAndInit();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 140, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "Browse .mid / .midi files. Default: game directory/music",
                Math.max(8, width / 2 - 155), height / 2 - 58, 0xBFC8D2);
        context.drawTextWithShadow(textRenderer, status,
                Math.max(8, width / 2 - 155), height / 2 + 100, 0xFFFF55);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
