package com.example.midipack;

import com.example.midipack.generator.DatapackGenerator;
import com.example.midipack.midi.MidiParser;
import com.example.midipack.midi.NoteEvent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class MidiPackMod implements ModInitializer {
    public static final Identifier UPLOAD_ID = new Identifier("midipack", "upload_midi");
    public static final Identifier ACTION_ID = new Identifier("midipack", "player_action");
    public static final int MAX_MIDI_BYTES = 512 * 1024;

    @Override
    public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_ID,
                (server, player, handler, buf, responseSender) -> {
                    String filename = buf.readString(128);
                    byte[] bytes = buf.readByteArray(MAX_MIDI_BYTES);
                    server.execute(() -> importMidi(server, player, filename, bytes));
                });

        ServerPlayNetworking.registerGlobalReceiver(ACTION_ID,
                (server, player, handler, buf, responseSender) -> {
                    String namespace = buf.readString(64);
                    String action = buf.readString(16);
                    server.execute(() -> {
                        if (!player.hasPermissionLevel(2)) {
                            player.sendMessage(Text.literal("需要管理员权限才能控制 MIDI 数据包。"), false);
                            return;
                        }
                        if (!namespace.matches("[a-z0-9_]{1,64}") || !(action.equals("play") || action.equals("stop"))) {
                            player.sendMessage(Text.literal("无效的播放请求。"), false);
                            return;
                        }
                        String function = namespace + ":" + action;
                        server.getCommandManager().executeWithPrefix(player.getCommandSource(), "function " + function);
                        player.sendMessage(Text.literal("MIDI " + action + " 请求已执行：" + function), false);
                    });
                });
    }

    private static void importMidi(MinecraftServer server,
                                   net.minecraft.server.network.ServerPlayerEntity player,
                                   String filename, byte[] bytes) {
        if (!player.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("需要管理员权限才能导入 MIDI。"), false);
            return;
        }
        if (bytes.length == 0 || bytes.length > MAX_MIDI_BYTES) {
            player.sendMessage(Text.literal("MIDI 文件为空或超过 512 KiB 限制。"), false);
            return;
        }
        String safeName = Path.of(filename).getFileName().toString();
        if (!safeName.toLowerCase(Locale.ROOT).endsWith(".mid")
                && !safeName.toLowerCase(Locale.ROOT).endsWith(".midi")) {
            player.sendMessage(Text.literal("只支持 .mid / .midi 文件。"), false);
            return;
        }
        try {
            Path importDir = server.getSavePath(WorldSavePath.ROOT).resolve("midi_import");
            Files.createDirectories(importDir);
            Path midiFile = importDir.resolve(safeName).normalize();
            if (!midiFile.startsWith(importDir.normalize())) throw new IllegalArgumentException("非法文件名");
            Files.write(midiFile, bytes);

            List<NoteEvent> events = MidiParser.parse(midiFile.toFile());
            String base = safeName.replaceFirst("(?i)\\.(mid|midi)$", "");
            BlockPos spawn = server.getOverworld().getSpawnPos().up(1);
            Path datapacks = server.getSavePath(WorldSavePath.DATAPACKS);
            String soundNs = DatapackGenerator.generate(datapacks, base, events,
                    DatapackGenerator.Backend.PLAYSOUND, spawn);
            String blockNs = DatapackGenerator.generate(datapacks, base, events,
                    DatapackGenerator.Backend.NOTE_BLOCK, spawn);
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "reload");
            player.sendMessage(Text.literal("已导入 " + events.size() + " 个音符事件，并生成两种数据包："), false);
            player.sendMessage(Text.literal("playsound: /function " + soundNs + ":play"), false);
            player.sendMessage(Text.literal("音符盒: /function " + blockNs + ":play"), false);
            player.sendMessage(Text.literal("音符盒布局位于世界出生点附近；请先确认该区域为空。"), false);
        } catch (Exception e) {
            player.sendMessage(Text.literal("MIDI 导入失败：" + e.getMessage()), false);
        }
    }
}
