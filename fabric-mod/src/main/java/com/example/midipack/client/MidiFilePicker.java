package com.example.midipack.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/** Opens the in-game file browser; no AWT or native desktop dialog is used. */
public final class MidiFilePicker {
    private MidiFilePicker() {}

    public static void open(BlockPos origin) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new MidiFilePickerScreen(origin, client.currentScreen));
    }
}
