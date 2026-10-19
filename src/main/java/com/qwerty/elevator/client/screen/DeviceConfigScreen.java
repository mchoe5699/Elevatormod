package com.qwerty.elevator.client.screen;

import com.qwerty.elevator.block.DeviceBlock;
import com.qwerty.elevator.block.entity.DeviceBlockEntity;
import com.qwerty.elevator.block.entity.LandingDoorBlockEntity;
import com.qwerty.elevator.elevator.*;
import com.qwerty.elevator.network.DeviceConfigPacket;
import com.qwerty.elevator.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Appearance settings for a hall fixture or a landing door. */
public class DeviceConfigScreen extends BaseScreen {
    private final BlockPos pos;
    private CompoundTag deviceCfg;
    private LandingDoorBlockEntity.GroupConfig doorCfg;
    private DeviceBlock.Kind kind;
    private String custom = "";
    private int slot;

    public DeviceConfigScreen(BlockPos pos) {
        super(Component.translatable("gui.qwertys_elevator.device"));
        this.pos = pos;
        this.panelW = 220;
        this.panelH = 200;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            var be = level.getBlockEntity(pos);
            if (be instanceof DeviceBlockEntity d) {
                deviceCfg = d.saveConfig();
                kind = d.kind();
            } else if (be instanceof LandingDoorBlockEntity door) {
                doorCfg = LandingDoorBlockEntity.GroupConfig.load(door.getConfig().save());
            }
        }
    }

    @Override
    protected void build() {
        int x = left + 10, y = top + 22;
        if (deviceCfg != null) buildDevice(x, y);
        else if (doorCfg != null) buildDoor(x, y);
        button(Component.translatable("gui.done"), left + panelW - 90, top + panelH - 22, 80, b -> {
            send();
            onClose();
        });
    }

    private void send() {
        if (deviceCfg != null) ModNetwork.sendToServer(new DeviceConfigPacket(pos, deviceCfg));
        else if (doorCfg != null) ModNetwork.sendToServer(new DeviceConfigPacket(pos, doorCfg.save()));
    }

    private void buildDevice(int x, int y) {
        boolean follow = !deviceCfg.contains("follow") || deviceCfg.getBoolean("follow");
        toggle(Component.translatable("gui.qwertys_elevator.follow"), x, y, 200, follow, v -> deviceCfg.putBoolean("follow", v));
        PanelStyle ps = Choice.byId(PanelStyle.class, deviceCfg.getString("style"), PanelStyle.STAINLESS);
        IndicatorStyle is = Choice.byId(IndicatorStyle.class, deviceCfg.getString("ind"), IndicatorStyle.RED_DOT);
        ButtonLight bl = Choice.byId(ButtonLight.class, deviceCfg.getString("light"), ButtonLight.ORANGE);
        cycle(Component.translatable("gui.qwertys_elevator.panel_style"), x, y + 24, 200, ps::label, d -> deviceCfg.putString("style", Choice.cycle(ps, d).id()));
        if (kind == DeviceBlock.Kind.INDICATOR || kind == DeviceBlock.Kind.WIDE_INDICATOR) {
            cycle(Component.translatable("gui.qwertys_elevator.indicator"), x, y + 46, 200, is::label, d -> deviceCfg.putString("ind", Choice.cycle(is, d).id()));
        }
        if (kind == DeviceBlock.Kind.HALL_BUTTON) {
            cycle(Component.translatable("gui.qwertys_elevator.button_light"), x, y + 46, 200, bl::label, d -> deviceCfg.putString("light", Choice.cycle(bl, d).id()));
        }
        label(Component.translatable("gui.qwertys_elevator.follow_hint"), x, y + 76, 0x9AA3AA);
    }

    private void buildDoor(int x, int y) {
        Component styleName = doorCfg.style == null ? Component.translatable("gui.qwertys_elevator.auto") : doorCfg.style.label();
        cycle(Component.translatable("gui.qwertys_elevator.door_style"), x, y, 200, () -> styleName, d -> {
            DoorStyle[] all = DoorStyle.values();
            int idx = doorCfg.style == null ? -1 : doorCfg.style.ordinal();
            idx += d;
            if (idx >= all.length) idx = -1;
            if (idx < -1) idx = all.length - 1;
            doorCfg.style = idx < 0 ? null : all[idx];
        });
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                Component.empty().append(slot == 0 ? "▶ " : "").append(Component.translatable("gui.qwertys_elevator.mat.door")).append(": ").append(Presets.materialName(doorCfg.panelMat)),
                b -> {
                    slot = 0;
                    doorCfg.panelMat = doorCfg.panelMat.isEmpty() ? Presets.MATERIALS.get(0) : Presets.cycleMaterial(doorCfg.panelMat, hasShiftDown() ? -1 : 1);
                    rebuild();
                }).bounds(x, y + 22, 200, 18).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                Component.empty().append(slot == 1 ? "▶ " : "").append(Component.translatable("gui.qwertys_elevator.mat.frame")).append(": ").append(Presets.materialName(doorCfg.frameMat)),
                b -> {
                    slot = 1;
                    doorCfg.frameMat = doorCfg.frameMat.isEmpty() ? Presets.MATERIALS.get(1) : Presets.cycleMaterial(doorCfg.frameMat, hasShiftDown() ? -1 : 1);
                    rebuild();
                }).bounds(x, y + 44, 200, 18).build());
        edit(x, y + 67, 120, custom, 64, v -> custom = v);
        button(Component.translatable("gui.qwertys_elevator.set_custom"), x + 122, y + 66, 40, b -> {
            if (Presets.validBlock(custom)) {
                if (slot == 0) doorCfg.panelMat = custom; else doorCfg.frameMat = custom;
                rebuild();
            }
        });
        button(Component.translatable("gui.qwertys_elevator.auto"), x + 164, y + 66, 36, b -> {
            if (slot == 0) doorCfg.panelMat = ""; else doorCfg.frameMat = "";
            rebuild();
        });
        toggle(Component.translatable("gui.qwertys_elevator.wide_frame"), x, y + 90, 98, doorCfg.wideFrame, v -> doorCfg.wideFrame = v);
        toggle(Component.translatable("gui.qwertys_elevator.floor_label"), x + 102, y + 90, 98, doorCfg.showFloorLabel, v -> doorCfg.showFloorLabel = v);
        label(Component.translatable("gui.qwertys_elevator.label_text"), x, y + 118);
        edit(x + 70, y + 114, 60, doorCfg.label, 6, v -> doorCfg.label = v);
    }
}
