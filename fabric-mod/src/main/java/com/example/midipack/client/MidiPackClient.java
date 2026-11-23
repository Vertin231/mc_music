package com.example.midipack.client;

import com.example.midipack.MidiPackMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class MidiPackClient implements ClientModInitializer {
    public static final int MAX_MIDI_BYTES = MidiPackMod.MAX_MIDI_BYTES;
    private static KeyBinding openScreen;
    public static volatile String lastNamespace = "midi_song";

    @Override
    public void onInitializeClient() {
        openScreen = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.midipack.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.midipack"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openScreen.wasPressed()) client.setScreen(new MidiPackScreen());
        });
    }

    public static void upload(String filename, byte[] data, BlockPos origin) {
        MinecraftClient.getInstance().execute(() -> uploadOnClientThread(filename, data, origin));
    }

    private static void uploadOnClientThread(String filename, byte[] data, BlockPos origin) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            notifyUser("请先进入一个已启用 MIDI Pack 的世界或服务器。");
            return;
        }
        if (data.length == 0 || data.length > MAX_MIDI_BYTES) {
            notifyUser("文件必须大于 0 且不超过 512 KiB。");
            return;
        }
        String base = filename.replaceFirst("(?i)\\.(mid|midi)$", "");
        String slug = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_");
        if (slug.isBlank()) slug = "song";
        if (!Character.isLetter(slug.charAt(0)) && slug.charAt(0) != '_') slug = "song_" + slug;
        if (slug.length() > 52) slug = slug.substring(0, 52);
        lastNamespace = "midi_" + slug;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(filename);
        buf.writeInt(origin.getX());
        buf.writeInt(origin.getY());
        buf.writeInt(origin.getZ());
        buf.writeByteArray(data);
        ClientPlayNetworking.send(MidiPackMod.UPLOAD_ID, buf);
        notifyUser("正在上传 MIDI；音符盒起点：" + origin.getX() + ", " + origin.getY() + ", " + origin.getZ());
    }

    public static void action(String namespace, String action) {
        if (MinecraftClient.getInstance().getNetworkHandler() == null) {
            notifyUser("当前没有连接到游戏服务器。");
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(namespace);
        buf.writeString(action);
        ClientPlayNetworking.send(MidiPackMod.ACTION_ID, buf);
    }

    public static void notifyUser(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player != null) client.player.sendMessage(Text.literal(message), false);
        });
    }
}
