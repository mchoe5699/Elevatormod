package com.qwerty.elevator.client.screen;

import com.qwerty.elevator.elevator.CopLayout;
import com.qwerty.elevator.entity.ElevatorCarEntity;
import com.qwerty.elevator.network.CarButtonPacket;
import com.qwerty.elevator.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Enlarged car operating panel (sneak + click the panel in the car). */
public class CarPanelScreen extends BaseScreen {
    private final ElevatorCarEntity car;
    private final int deck;
    private int page;

    public CarPanelScreen(ElevatorCarEntity car, int deck) {
        super(Component.translatable("gui.qwertys_elevator.car_panel"));
        this.car = car;
        this.deck = deck;
        this.panelW = 220;
        this.panelH = 220;
    }

    private void press(CopLayout.ButtonType type, int floor) {
        ModNetwork.sendToServer(new CarButtonPacket(car.getId(), type.ordinal(), floor));
    }

    @Override
    protected void build() {
        List<Integer> floors = car.getGeometry().floorsForDeck(deck);
        int cols = 4, rows = 6, per = cols * rows;
        int pages = Math.max(1, (floors.size() + per - 1) / per);
        page = Math.max(0, Math.min(pages - 1, page));
        int gx = left + 14, gy = top + 40;
        for (int i = 0; i < per; i++) {
            int idx = page * per + i;
            if (idx >= floors.size()) break;
            int f = floors.get(idx);
            int col = i % cols, row = rows - 1 - i / cols;
            String name = f < car.getGeometry().floorNames.length ? car.getGeometry().floorNames[f] : "?";
            boolean lit = car.getStatus().isCalled(f);
            Button b = button(Component.literal(lit ? "● " + name : name), gx + col * 48, gy + row * 22, 44, btn -> press(CopLayout.ButtonType.FLOOR, f));
            if (lit) b.setMessage(Component.literal("● " + name).withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        int by = gy + rows * 22 + 4;
        if (pages > 1) {
            small(Component.literal("◀"), left + panelW - 60, top + 22, 20, b -> { page--; rebuild(); });
            small(Component.literal("▶"), left + panelW - 36, top + 22, 20, b -> { page++; rebuild(); });
        }
        button(Component.literal("◀ ▶"), gx, by, 44, b -> press(CopLayout.ButtonType.OPEN, -1));
        button(Component.literal("▶ ◀"), gx + 48, by, 44, b -> press(CopLayout.ButtonType.CLOSE, -1));
        button(Component.translatable("gui.qwertys_elevator.alarm"), gx + 96, by, 44, b -> press(CopLayout.ButtonType.ALARM, -1));
        if (car.getSettings().fireService) {
            button(Component.translatable("gui.qwertys_elevator.fire_btn"), gx + 144, by, 44, b -> press(CopLayout.ButtonType.FIRE, -1));
        } else {
            button(Component.literal("☎"), gx + 144, by, 44, b -> press(CopLayout.ButtonType.PHONE, -1));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (car.isRemoved()) onClose();
        if (car.tickCount % 5 == 0) rebuild();
    }

    @Override
    protected void drawExtra(GuiGraphics g, int mouseX, int mouseY, float pt) {
        var st = car.getStatus();
        String arrow = st.dir > 0 ? "▲" : st.dir < 0 ? "▼" : " ";
        g.fill(left + 14, top + 14, left + 110, top + 34, 0xFF050505);
        g.drawString(font, arrow + " " + st.display, left + 20, top + 20, car.getSettings().indicatorStyle.dotMatrix ? car.getSettings().indicatorStyle.fg : 0xFFFFFF, false);
        g.drawString(font, car.getSettings().name, left + 116, top + 6, 0xFFFFFF, false);
        if (!st.message.isEmpty()) g.drawString(font, Component.translatable(st.message), left + 116, top + 40 - 20, 0xFF6060, false);
    }
}
