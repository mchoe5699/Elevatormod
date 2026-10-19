package com.qwerty.elevator.client.screen;

import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.block.entity.DeviceBlockEntity;
import com.qwerty.elevator.elevator.Floor;
import com.qwerty.elevator.network.DestinationPacket;
import com.qwerty.elevator.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 행선층 예약 패널: choose where you want to go; the system assigns a car. */
public class DestinationScreen extends BaseScreen {
    private final BlockPos pos;
    private int page;

    public DestinationScreen(BlockPos pos) {
        super(Component.translatable("gui.qwertys_elevator.destination"));
        this.pos = pos;
        this.panelW = 220;
        this.panelH = 200;
    }

    @Override
    protected void build() {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getBlockEntity(pos) instanceof DeviceBlockEntity dev)) return;
        ControllerBlockEntity c = dev.primary();
        label(Component.translatable("gui.qwertys_elevator.destination"), left + 8, top + 6, 0xFFFFFF);
        if (c == null) {
            label(Component.translatable("message.qwertys_elevator.not_linked"), left + 8, top + 30, 0xFF8080);
            return;
        }
        int here = dev.floorFor(c);
        List<Floor> floors = c.getFloors();
        int cols = 4, rows = 6, per = cols * rows;
        int pages = Math.max(1, (floors.size() + per - 1) / per);
        page = Math.max(0, Math.min(pages - 1, page));
        for (int i = 0; i < per; i++) {
            int idx = page * per + i;
            if (idx >= floors.size()) break;
            int col = i % cols, row = rows - 1 - i / cols;
            final int f = idx;
            var b = button(Component.literal(floors.get(idx).name), left + 14 + col * 48, top + 30 + row * 22, 44, btn -> {
                ModNetwork.sendToServer(new DestinationPacket(pos, f));
                onClose();
            });
            if (idx == here) b.active = false;
        }
        if (pages > 1) {
            small(Component.literal("◀"), left + 14, top + panelH - 22, 20, b -> { page--; rebuild(); });
            small(Component.literal("▶"), left + 40, top + panelH - 22, 20, b -> { page++; rebuild(); });
        }
    }
}
