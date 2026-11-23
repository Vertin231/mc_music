package com.example.midipack.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/** Lightweight in-game control panel. */
public class MidiPackScreen extends Screen {
    private String lastNamespace = "midi_song";

    public MidiPackScreen() {
        super(Text.literal("MIDI Pack"));
    }

    @Override
    protected void init() {
        int left = width / 2 - 100;
        int top = height / 2 - 80;
        addDrawableChild(ButtonWidget.builder(Text.literal("Import MIDI (system file picker)"), b -> {
            MidiFilePicker.open();
        }).dimensions(left, top, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Play: playsound backend"), b -> {
            MidiPackClient.action(lastNamespace + "_sound", "play");
        }).dimensions(left, top + 28, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Play: note-block backend"), b -> {
            MidiPackClient.action(lastNamespace + "_blocks", "play");
        }).dimensions(left, top + 56, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Stop playback"), b -> {
            MidiPackClient.action(lastNamespace + "_sound", "stop");
            MidiPackClient.action(lastNamespace + "_blocks", "stop");
        }).dimensions(left, top + 84, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(left, top + 116, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 112, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Press M to open this screen. Import a MIDI, then choose a backend."),
                width / 2, height / 2 + 72, 0xBFC8D2);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }

    private static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }
}
