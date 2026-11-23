package com.example.midipack.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Lightweight in-game control panel. */
public class MidiPackScreen extends Screen {
    public MidiPackScreen() {
        super(Text.literal("MIDI Pack"));
    }

    @Override
    protected void init() {
        int left = width / 2 - 105;
        int top = height / 2 - 82;
        addDrawableChild(ButtonWidget.builder(Text.literal("Import MIDI (system picker)"), b -> {
            MidiFilePicker.open();
        }).dimensions(left, top, 210, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Play: playsound backend"), b -> {
            MidiPackClient.action(MidiPackClient.lastNamespace + "_sound", "play");
        }).dimensions(left, top + 28, 210, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Play: note-block backend"), b -> {
            MidiPackClient.action(MidiPackClient.lastNamespace + "_blocks", "play");
        }).dimensions(left, top + 56, 210, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Stop playback"), b -> {
            MidiPackClient.action(MidiPackClient.lastNamespace + "_sound", "stop");
            MidiPackClient.action(MidiPackClient.lastNamespace + "_blocks", "stop");
        }).dimensions(left, top + 84, 210, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(left, top + 116, 210, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 116, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Current datapack namespace: " + MidiPackClient.lastNamespace),
                width / 2, height / 2 + 74, 0xBFC8D2);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
