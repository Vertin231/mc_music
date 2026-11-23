package com.example.midipack.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.File;

/** GUI for loading a local MIDI file and an optional SF2 SoundFont, then playing locally. */
public final class SoundFontScreen extends Screen {
    private TextFieldWidget bankPath, midiPath, volumeField;
    private String status = "Load an SF2 bank and MIDI file to play locally.";
    private final Screen parent;

    public SoundFontScreen(Screen parent) {
        super(Text.literal("Local SoundFont Player"));
        this.parent = parent;
    }

    @Override protected void init() {
        int center = width / 2;
        int left = center - 150;
        int top = Math.max(12, height / 2 - 100);
        bankPath = new TextFieldWidget(textRenderer, left, top, 300, 20, Text.literal("SoundFont path"));
        bankPath.setMaxLength(1024);
        bankPath.setText(" ");
        midiPath = new TextFieldWidget(textRenderer, left, top + 48, 300, 20, Text.literal("MIDI path"));
        midiPath.setMaxLength(1024);
        volumeField = new TextFieldWidget(textRenderer, left + 228, top + 101, 72, 20, Text.literal("Volume"));
        volumeField.setMaxLength(3);
        volumeField.setText("90");
        addDrawableChild(bankPath);
        addDrawableChild(midiPath);
        addDrawableChild(volumeField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Load SoundFont (.sf2)"), b -> {
            try {
                File file = new File(bankPath.getText().trim());
                if (!file.isFile()) throw new IllegalArgumentException("SoundFont file path not found.");
                SoundFontPlayer.loadSoundFont(file);
                status = "SoundFont loaded: " + file.getName();
            } catch (Exception e) { status = "SoundFont load failed: " + safe(e); }
        }).dimensions(left, top + 24, 300, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Load MIDI (.mid)"), b -> {
            try {
                File file = new File(midiPath.getText().trim());
                if (!file.isFile()) throw new IllegalArgumentException("MIDI file path not found.");
                SoundFontPlayer.loadMidi(file);
                status = "MIDI loaded: " + file.getName();
            } catch (Exception e) { status = "MIDI load failed: " + safe(e); }
        }).dimensions(left, top + 72, 210, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Set volume"), b -> {
            try {
                SoundFontPlayer.setVolume(Integer.parseInt(volumeField.getText().trim()));
                status = "Volume set (0–127).";
            } catch (Exception e) { status = "Volume must be an integer from 0 to 127."; }
        }).dimensions(left + 216, top + 124, 84, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Play locally"), b -> {
            try { SoundFontPlayer.play(); status = "Local MIDI playback started."; }
            catch (Exception e) { status = "Playback failed: " + safe(e); }
        }).dimensions(left, top + 151, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), b -> {
            SoundFontPlayer.stop(); status = "Playback stopped.";
        }).dimensions(left + 155, top + 151, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                .dimensions(left, top + 178, 300, 20).build());
    }

    private static String safe(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int center = width / 2, top = height / 2 - 112;
        context.drawCenteredTextWithShadow(textRenderer, title, center, top, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "SoundFont (.sf2 path recommended)", center - 150, top + 15, 0xBFC8D2);
        context.drawTextWithShadow(textRenderer, "MIDI file (.mid/.midi path)", center - 150, top + 60, 0xBFC8D2);
        context.drawTextWithShadow(textRenderer, "Volume", center + 78, top + 116, 0xBFC8D2);
        context.drawTextWithShadow(textRenderer, status, Math.max(8, center - 150), top + 205, 0xFFFF55);
        context.drawTextWithShadow(textRenderer, "Playback is client-local; other players won't hear it.", center - 150, top + 222, 0xA0A0A0);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { if (client != null) client.setScreen(parent); }
}
