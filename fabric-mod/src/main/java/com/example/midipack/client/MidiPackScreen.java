package com.example.midipack.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class MidiPackScreen extends Screen {
    public MidiPackScreen() { super(Text.literal("MIDI Pack")); }

    @Override
    protected void init() {
        int left = width / 2 - 112;
        int top = height / 2 - 91;
        addDrawableChild(ButtonWidget.builder(Text.literal("Import MIDI (system file picker)"),
                button -> MidiFilePicker.open()).dimensions(left, top, 224, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Play · playsound"),
                button -> MidiPackClient.action(MidiPackClient.lastNamespace + "_sound", "play"))
                .dimensions(left, top + 29, 224, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Play · note blocks"),
                button -> MidiPackClient.action(MidiPackClient.lastNamespace + "_blocks", "play"))
                .dimensions(left, top + 56, 224, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Pause both backends"), button -> {
            MidiPackClient.action(MidiPackClient.lastNamespace + "_sound", "pause");
            MidiPackClient.action(MidiPackClient.lastNamespace + "_blocks", "pause");
        }).dimensions(left, top + 83, 224, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Resume both backends"), button -> {
            MidiPackClient.action(MidiPackClient.lastNamespace + "_sound", "resume");
            MidiPackClient.action(MidiPackClient.lastNamespace + "_blocks", "resume");
        }).dimensions(left, top + 110, 224, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop both backends"), button -> {
            MidiPackClient.action(MidiPackClient.lastNamespace + "_sound", "stop");
            MidiPackClient.action(MidiPackClient.lastNamespace + "_blocks", "stop");
        }).dimensions(left, top + 137, 224, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(left, top + 168, 224, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 116, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Selected MIDI: " + MidiPackClient.lastNamespace),
                width / 2, height / 2 + 103, 0xBFC8D2);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Requires operator permission (level 2) on server"),
                width / 2, height / 2 + 116, 0xA0A0A0);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
