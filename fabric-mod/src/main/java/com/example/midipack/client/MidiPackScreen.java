package com.example.midipack.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class MidiPackScreen extends Screen {
    private TextFieldWidget xField, yField, zField;

    public MidiPackScreen() { super(Text.literal("MIDI Pack")); }

    @Override
    protected void init() {
        int center = width / 2;
        int top = Math.max(12, height / 2 - 116);
        BlockPos current = MinecraftClient.getInstance().player == null
                ? BlockPos.ORIGIN : MinecraftClient.getInstance().player.getBlockPos().down();

        xField = new TextFieldWidget(textRenderer, center - 108, top + 28, 66, 20, Text.literal("X"));
        yField = new TextFieldWidget(textRenderer, center - 33, top + 28, 66, 20, Text.literal("Y"));
        zField = new TextFieldWidget(textRenderer, center + 42, top + 28, 66, 20, Text.literal("Z"));
        for (TextFieldWidget field : new TextFieldWidget[]{xField, yField, zField}) {
            field.setMaxLength(10);
            addDrawableChild(field);
        }
        setCoordinates(current);

        addDrawableChild(ButtonWidget.builder(Text.literal("Use my current position"), button -> {
            if (client != null && client.player != null) setCoordinates(client.player.getBlockPos().down());
        }).dimensions(center - 108, top + 53, 216, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Import MIDI (system picker)"), button -> {
            BlockPos origin = readCoordinates();
            if (origin != null) MidiFilePicker.open(origin);
        }).dimensions(center - 108, top + 79, 216, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Play · playsound"), button -> {
            MidiPackClient.action(MidiPackClient.lastNamespace + "_sound", "stop");
            MidiPackClient.action(MidiPackClient.lastNamespace + "_sound", "play");
        }).dimensions(center - 108, top + 106, 104, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Play · note blocks"), button -> {
            MidiPackClient.action(MidiPackClient.lastNamespace + "_blocks", "stop");
            MidiPackClient.action(MidiPackClient.lastNamespace + "_blocks", "play");
        }).dimensions(center + 4, top + 106, 104, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Stop both"), button -> {
            MidiPackClient.action(MidiPackClient.lastNamespace + "_sound", "stop");
            MidiPackClient.action(MidiPackClient.lastNamespace + "_blocks", "stop");
        }).dimensions(center - 108, top + 133, 216, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(center - 108, top + 160, 216, 20).build());
    }

    private void setCoordinates(BlockPos pos) {
        xField.setText(Integer.toString(pos.getX()));
        yField.setText(Integer.toString(pos.getY()));
        zField.setText(Integer.toString(pos.getZ()));
    }

    private BlockPos readCoordinates() {
        try {
            int x = Integer.parseInt(xField.getText().trim());
            int y = Integer.parseInt(yField.getText().trim());
            int z = Integer.parseInt(zField.getText().trim());
            if (client != null && client.player != null) {
                if (Math.abs((long) x - client.player.getX()) > 30_000_000L
                        || Math.abs((long) z - client.player.getZ()) > 30_000_000L) {
                    throw new NumberFormatException();
                }
            }
            return new BlockPos(x, y, z);
        } catch (NumberFormatException e) {
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("请输入有效的整数坐标 X、Y、Z。"), false);
            }
            return null;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 133, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Note-block layout origin (support block)"),
                width / 2, height / 2 - 117, 0xBFC8D2);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("X"),
                width / 2 - 75, height / 2 - 99, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Y"),
                width / 2, height / 2 - 99, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Z"),
                width / 2 + 75, height / 2 - 99, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("One chronological +X lane per Track + Channel"),
                width / 2, height / 2 + 99, 0xA0A0A0);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
