package com.example.midipack.client;

import com.example.midipack.MidiPackMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import org.lwjgl.glfw.GLFW;

public class MidiPackClient implements ClientModInitializer {
    private static KeyBinding openScreen;

    @Override
    public void onInitializeClient() {
        openScreen = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.midipack.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M,
                "category.midipack"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openScreen.wasPressed()) client.setScreen(new MidiPackScreen());
        });
    }

    public static void upload(String filename, byte[] data) {
        if (data.length > MidiPackMod.MAX_MIDI_BYTES) {
            notifyUser("文件超过 512 KiB，暂不支持。");
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(filename);
        buf.writeByteArray(data);
        ClientPlayNetworking.send(MidiPackMod.UPLOAD_ID, buf);
        notifyUser("正在上传并生成 MIDI 数据包……");
    }

    public static void action(String namespace, String action) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(namespace);
        buf.writeString(action);
        ClientPlayNetworking.send(MidiPackMod.ACTION_ID, buf);
    }

    public static void notifyUser(String message) {
        if (net.minecraft.client.MinecraftClient.getInstance().player != null) {
            net.minecraft.client.MinecraftClient.getInstance().player.sendMessage(Text.literal(message), false);
        }
    }
}
