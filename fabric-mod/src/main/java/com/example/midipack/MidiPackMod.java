package com.example.midipack;

import com.example.midipack.generator.DatapackGenerator;
import com.example.midipack.midi.MidiParser;
import com.example.midipack.midi.NoteEvent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class MidiPackMod implements ModInitializer {
    public static final Identifier UPLOAD_ID = new Identifier("midipack", "upload_midi");
    public static final Identifier ACTION_ID = new Identifier("midipack", "player_action");
    public static final int MAX_MIDI_BYTES = 512 * 1024;

    @Override
    public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_ID,
                (server, player, handler, buf, responseSender) -> {
                    String filename = buf.readString(128);
                    int x = buf.readInt();
                    int y = buf.readInt();
                    int z = buf.readInt();
                    byte[] bytes = buf.readByteArray(MAX_MIDI_BYTES);
                    BlockPos origin = new BlockPos(x, y, z);
                    server.execute(() -> importMidi(server, player, filename, bytes, origin));
                });
        ServerPlayNetworking.registerGlobalReceiver(ACTION_ID,
                (server, player, handler, buf, responseSender) -> {
                    String namespace = buf.readString(64);
                    String action = buf.readString(16);
                    server.execute(() -> handleAction(server, player, namespace, action));
                });
    }

    private static void handleAction(MinecraftServer server, ServerPlayerEntity player,
                                     String namespace, String action) {
        if (!player.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("需要管理员权限才能控制 MIDI 数据包。"), false);
            return;
        }
        if (!namespace.matches("[a-z0-9_]{1,64}")
                || !(action.equals("play") || action.equals("pause")
                || action.equals("resume") || action.equals("stop"))) {
            player.sendMessage(Text.literal("无效的播放请求。"), false);
            return;
        }
        server.getCommandManager().executeWithPrefix(
                player.getCommandSource(), "function " + namespace + ":" + action);
    }

    private static void importMidi(MinecraftServer server, ServerPlayerEntity player,
                                   String filename, byte[] bytes, BlockPos origin) {
        if (!player.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("需要管理员权限才能导入 MIDI。"), false);
            return;
        }
        if (bytes.length < 14 || bytes.length > MAX_MIDI_BYTES
                || bytes[0] != 'M' || bytes[1] != 'T' || bytes[2] != 'h' || bytes[3] != 'd') {
            player.sendMessage(Text.literal("文件头无效、文件为空或超过 512 KiB。"), false);
            return;
        }

        String normalized = filename == null ? "" : filename.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String safeName = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        if (safeName.isBlank() || safeName.equals(".") || safeName.equals("..") || safeName.length() > 128) {
            player.sendMessage(Text.literal("非法文件名。"), false);
            return;
        }
        String lower = safeName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".mid") && !lower.endsWith(".midi")) {
            player.sendMessage(Text.literal("只支持 .mid / .midi 文件。"), false);
            return;
        }

        if (!validPlacement(server, origin, player)) return;

        final Path midiFile;
        try {
            Path worldRoot = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
            Path importDir = worldRoot.resolve("midi_import").normalize();
            Files.createDirectories(importDir);
            midiFile = importDir.resolve(safeName).normalize();
            if (!midiFile.startsWith(importDir)) throw new IllegalArgumentException("非法文件名");
            Files.write(midiFile, bytes);
        } catch (Exception e) {
            player.sendMessage(Text.literal("保存 MIDI 失败：" + safeMessage(e)), false);
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                return MidiParser.parse(midiFile.toFile());
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).whenComplete((events, error) -> server.execute(() -> {
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                player.sendMessage(Text.literal("MIDI 解析失败：" + safeMessage(cause)), false);
                return;
            }
            generateDatapacks(server, player, safeName, events, origin);
        }));
    }

    private static boolean validPlacement(MinecraftServer server, BlockPos origin, ServerPlayerEntity player) {
        int maxY = server.getOverworld().getTopY();
        if (origin.getY() < server.getOverworld().getBottomY()
                || origin.getY() + 2 >= maxY) {
            player.sendMessage(Text.literal("放置 Y 坐标超出世界高度范围。"), false);
            return false;
        }
        // Worst-case layout has 256 stations, spaced three blocks apart along +X.
        BlockPos end = origin.add(255 * 3 + 2, 1, 0);
        if (!server.getOverworld().getWorldBorder().contains(origin)
                || !server.getOverworld().getWorldBorder().contains(end)) {
            player.sendMessage(Text.literal("线性音符盒布局超出世界边界，请调整 X/Z 起点。"), false);
            return false;
        }
        return true;
    }

    private static void generateDatapacks(MinecraftServer server, ServerPlayerEntity player,
                                          String safeName, List<NoteEvent> events, BlockPos origin) {
        try {
            String base = safeName.replaceFirst("(?i)\\.(mid|midi)$", "");
            Path datapacks = server.getSavePath(WorldSavePath.DATAPACKS);
            String soundNs = DatapackGenerator.generate(datapacks, base, events,
                    DatapackGenerator.Backend.PLAYSOUND, origin);
            String blockNs = DatapackGenerator.generate(datapacks, base, events,
                    DatapackGenerator.Backend.NOTE_BLOCK, origin);
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), "reload");
            player.sendMessage(Text.literal("已导入 " + events.size() + " 个音符事件。"), false);
            player.sendMessage(Text.literal("音符盒线性布局起点（支撑方块）：" + origin.getX() + " "
                    + origin.getY() + " " + origin.getZ() + "，沿 +X 方向延伸。"), false);
            player.sendMessage(Text.literal("playsound：/function " + soundNs + ":play"), false);
            player.sendMessage(Text.literal("音符盒：/function " + blockNs + ":play"), false);
            player.sendMessage(Text.literal("生成结构前请清空起点沿 +X 方向约 770 格的区域，并备份存档。"), false);
        } catch (Exception e) {
            player.sendMessage(Text.literal("数据包生成失败：" + safeMessage(e)), false);
        }
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
