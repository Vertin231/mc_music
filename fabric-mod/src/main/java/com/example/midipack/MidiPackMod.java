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

    @Override public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_ID, (server, player, handler, buf, responseSender) -> {
            String filename = buf.readString(128);
            BlockPos origin = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
            byte[] bytes = buf.readByteArray(MAX_MIDI_BYTES);
            server.execute(() -> importMidi(server, player, filename, bytes, origin));
        });
        ServerPlayNetworking.registerGlobalReceiver(ACTION_ID, (server, player, handler, buf, responseSender) -> {
            String namespace = buf.readString(64), action = buf.readString(16);
            server.execute(() -> handleAction(server, player, namespace, action));
        });
    }

    private static void handleAction(MinecraftServer server, ServerPlayerEntity player, String ns, String action) {
        if (!player.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("需要管理员权限才能控制 MIDI 数据包。"), false); return;
        }
        if (!ns.matches("[a-z0-9_]{1,64}") || !(action.equals("play") || action.equals("stop"))) {
            player.sendMessage(Text.literal("无效的播放请求。"), false); return;
        }
        // Physical note-block structures are placed and ticked in the Overworld.
        // Use the server command source so changing the player's dimension cannot move playback.
        var source = ns.endsWith("_blocks") ? server.getCommandSource() : player.getCommandSource();
        server.getCommandManager().executeWithPrefix(source, "function " + ns + ":" + action);
    }

    private static void importMidi(MinecraftServer server, ServerPlayerEntity player,
                                   String filename, byte[] bytes, BlockPos origin) {
        if (!player.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("需要管理员权限才能导入 MIDI。"), false); return;
        }
        if (bytes.length < 14 || bytes.length > MAX_MIDI_BYTES || bytes[0]!='M'
                || bytes[1]!='T' || bytes[2]!='h' || bytes[3]!='d') {
            player.sendMessage(Text.literal("文件头无效、文件为空或超过 512 KiB。"), false); return;
        }
        String normalized = filename == null ? "" : filename.replace('\\','/');
        int separator = normalized.lastIndexOf('/');
        String safeName = separator >= 0 ? normalized.substring(separator+1) : normalized;
        if (safeName.isBlank() || safeName.equals(".") || safeName.equals("..") || safeName.length()>128) {
            player.sendMessage(Text.literal("非法文件名。"), false); return;
        }
        String lower=safeName.toLowerCase(Locale.ROOT);
        if(!lower.endsWith(".mid")&&!lower.endsWith(".midi")) {
            player.sendMessage(Text.literal("只支持 .mid / .midi 文件。"), false); return;
        }

        final Path midiFile;
        try {
            Path worldRoot=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
            Path dir=worldRoot.resolve("midi_import").normalize();
            Files.createDirectories(dir);
            midiFile=dir.resolve(safeName).normalize();
            if(!midiFile.startsWith(dir))throw new IllegalArgumentException("非法文件名");
            Files.write(midiFile,bytes);
        } catch(Exception e) {
            player.sendMessage(Text.literal("保存 MIDI 失败："+safeMessage(e)),false); return;
        }
        CompletableFuture.supplyAsync(() -> {
            try { return MidiParser.parse(midiFile.toFile()); }
            catch(Exception e) { throw new CompletionException(e); }
        }).whenComplete((events,error)->server.execute(()->{
            if(error!=null) {
                Throwable cause=error.getCause()==null?error:error.getCause();
                player.sendMessage(Text.literal("MIDI 解析失败："+safeMessage(cause)),false); return;
            }
            if(!validPlacement(server,origin,events,player))return;
            generateDatapacks(server,player,safeName,events,origin);
        }));
    }

    private static boolean validPlacement(MinecraftServer server,BlockPos origin,
                                          List<NoteEvent> events,ServerPlayerEntity player) {
        var world=server.getOverworld();
        if(origin.getY()<world.getBottomY()||origin.getY()+2>=world.getTopY()) {
            player.sendMessage(Text.literal("放置 Y 坐标超出世界高度范围。"),false); return false;
        }
        if(events.size()>DatapackGenerator.MAX_PHYSICAL_NOTES) {
            player.sendMessage(Text.literal("音符事件超过线性音符盒轨道上限："+DatapackGenerator.MAX_PHYSICAL_NOTES+" 个。"),false);
            return false;
        }
        int parts=DatapackGenerator.countParts(events);
        if(parts>DatapackGenerator.MAX_PARTS) {
            player.sendMessage(Text.literal("声部数量超过限制：最多支持 "+DatapackGenerator.MAX_PARTS+" 条并行轨道。"),false);
            return false;
        }
        int repeaters=DatapackGenerator.maxPhysicalRepeaterCount(events);
        if(repeaters>DatapackGenerator.MAX_PHYSICAL_REPEATERS) {
            player.sendMessage(Text.literal("红石中继器数量超过安全上限："+DatapackGenerator.MAX_PHYSICAL_REPEATERS+" 个。"),false);
            return false;
        }
        int chordSize=DatapackGenerator.maxChordSize(events);
        int laneSpacing=chordSize+2;
        BlockPos start=origin.add(-1,0,0);
        BlockPos end=origin.add((repeaters-1)*3+2,2,Math.max(0,parts-1)*laneSpacing+chordSize-1);
        if(!world.getWorldBorder().contains(start)||!world.getWorldBorder().contains(end)) {
            player.sendMessage(Text.literal("实体红石轨道超出世界边界，请调整 X/Z 起点。"),false); return false;
        }
        return true;
    }

    private static void generateDatapacks(MinecraftServer server,ServerPlayerEntity player,
                                          String safeName,List<NoteEvent> events,BlockPos origin) {
        try {
            String base=safeName.replaceFirst("(?i)\\.(mid|midi)$","");
            Path datapacks=server.getSavePath(WorldSavePath.DATAPACKS);
            String soundNs=DatapackGenerator.generate(datapacks,base,events,DatapackGenerator.Backend.PLAYSOUND,origin);
            String blockNs=DatapackGenerator.generate(datapacks,base,events,DatapackGenerator.Backend.NOTE_BLOCK,origin);
            server.getCommandManager().executeWithPrefix(server.getCommandSource(),"reload");
            int parts=DatapackGenerator.countParts(events);
            player.sendMessage(Text.literal("已导入 "+events.size()+" 个音符事件，识别出 "+parts
                    +" 条声部轨道（Track + Channel）。"),false);
            player.sendMessage(Text.literal("实体红石轨道起点（首个中继器支撑方块）："+origin.getX()+" "+origin.getY()+" "+origin.getZ()
                    +"；声部沿 +Z 分行，红石链沿 +X 延伸。"),false);
            player.sendMessage(Text.literal("playsound：/function "+soundNs+":play"),false);
            player.sendMessage(Text.literal("实体红石音符盒轨道：/function "+blockNs+":play"),false);
            player.sendMessage(Text.literal("已生成中继器、红石粉和音符盒；不同声部独立，和弦并行。实体红石最短间隔按 4 游戏刻量化。"),false);
            player.sendMessage(Text.literal("请先确保放置区域为空并备份存档；停止时已进入线路的脉冲可能播放完当前剩余音符。"),false);
        } catch(Exception e) {
            player.sendMessage(Text.literal("数据包生成失败："+safeMessage(e)),false);
        }
    }
    private static String safeMessage(Throwable e) {
        return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
    }
}
