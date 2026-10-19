package com.qwerty.elevator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.qwerty.elevator.block.DeviceBlock;
import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.block.entity.DeviceBlockEntity;
import com.qwerty.elevator.elevator.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Lit buttons, lanterns, indicators and labels of the hall fixtures. */
public class DeviceRenderer implements BlockEntityRenderer<DeviceBlockEntity> {
    public DeviceRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(DeviceBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Draw d = new Draw(buf, ps, Minecraft.getInstance().font);
        DeviceBlock.Kind kind = be.kind();
        float time = be.blink + pt;
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180f - be.facing().toYRot()));
        ps.translate(-0.5, 0, -0.5);
        double zf = kind == DeviceBlock.Kind.PIT_STOP || kind == DeviceBlock.Kind.WIDE_INDICATOR ? 14 / 16.0 : 15 / 16.0;
        // switch to a frame looking at the fixture from the front: x right, y up, z towards the viewer
        ps.translate(1, 0, zf);
        ps.mulPose(Axis.YP.rotationDegrees(180));
        List<ControllerBlockEntity> ctrls = be.loadedControllers();
        ControllerBlockEntity c = ctrls.isEmpty() ? null : ctrls.get(0);
        int floor = be.clientFloor;
        if(be.effectiveStyle()!=PanelStyle.VIDEO_CLASSIC && be.effectiveStyle()!=PanelStyle.LG_MGP) {
            if(kind==DeviceBlock.Kind.HALL_BUTTON)
                d.box(RenderUtil.sprite("block/panel_stainless"),0xFFFFFFFF,light,
                        6/16.0,3/16.0,-1/16.0,10/16.0,13/16.0,0);
            else if(kind==DeviceBlock.Kind.SIGN)
                d.box(RenderUtil.sprite("block/white_panel"),0xFFFFFFFF,light,
                        3/16.0,5/16.0,-1/16.0,13/16.0,11/16.0,-.5/16.0);
        }
        switch (kind) {
            case HALL_BUTTON -> hallButton(d, be, ctrls, floor, light, time);
            case HALL_LANTERN -> lantern(d, be, ctrls, light, time);
            case INDICATOR -> indicator(d, be, c, 3 / 16.0, 4 / 16.0, 13 / 16.0, 12 / 16.0, light, time);
            case WIDE_INDICATOR -> wideIndicator(d, be, ctrls, light, time);
            case DESTINATION -> destination(d, be, c, floor, light);
            case FIRE_SWITCH -> fireSwitch(d, be, light);
            case PIT_STOP -> pitStop(d, be, light);
            case SIGN -> sign(d, be, c, light);
        }
        ps.popPose();
    }

    private void hallButton(Draw d, DeviceBlockEntity be, List<ControllerBlockEntity> ctrls, int floor, int light, float time) {
        ButtonLight bl = be.effectiveLight();
        PanelStyle style = be.effectiveStyle();
        boolean up = true, down = true;
        boolean upLit = false, downLit = false;
        if (!ctrls.isEmpty() && floor >= 0) {
            ControllerBlockEntity c = ctrls.get(0);
            if (c.isTopFloor(floor)) up = false;
            if (c.isBottomFloor(floor)) down = false;
            for (ControllerBlockEntity cc : ctrls) {
                int f = cc.findFloorNear(be.getBlockPos());
                upLit |= cc.hasHallCall(f, 1);
                downLit |= cc.hasHallCall(f, -1);
            }
        }
        if (style == PanelStyle.LG_MGP) {
            LgFixtures.hall(d,ctrls.isEmpty()?null:ctrls.get(0),up,down,upLit,downLit,light,time);
            return;
        }
        if (style == PanelStyle.VIDEO_CLASSIC) {
            VideoFixtures.hall(d, be, ctrls.isEmpty() ? null : ctrls.get(0), up, down, upLit, downLit, light, time);
            return;
        }
        double cx = 0.5;
        // faceplate trim
        d.color(style.highlight, light, 6 / 16.0 + 0.01, 3 / 16.0 + 0.01, 0.0005, 10 / 16.0 - 0.01, 13 / 16.0 - 0.01, 0.0015);
        d.color(style.plate, light, 6 / 16.0 + 0.02, 3 / 16.0 + 0.02, 0.0015, 10 / 16.0 - 0.02, 13 / 16.0 - 0.02, 0.0025);
        if (up) button(d, cx, down ? 0.62 : 0.5, upLit, bl, "▲", light);
        if (down) button(d, cx, up ? 0.38 : 0.5, downLit, bl, "▼", light);
        // braille plate
        d.color(style.highlight, light, cx - 0.03, 0.2 + 0.01, 0.0025, cx + 0.03, 0.23, 0.004);
    }

    private void button(Draw d, double cx, double cy, boolean lit, ButtonLight bl, String arrow, int light) {
        double r = 0.065;
        d.color(0xA8AFB5, light, cx - r * 1.2, cy - r * 1.2, 0.0025, cx + r * 1.2, cy + r * 1.2, 0.006);
        int face = lit ? bl.color : 0xE6E9EC;
        d.color(face, lit ? RenderUtil.FULL_BRIGHT : light, cx - r, cy - r, 0.006, cx + r, cy + r, 0.012);
        if (lit) d.color(bl.color, RenderUtil.FULL_BRIGHT, cx - r * 1.12, cy - r * 1.12, 0.006, cx + r * 1.12, cy + r * 1.12, 0.007);
        d.text(Component.literal(arrow), cx, cy, 0.0125, 0.07, lit ? 0xFFFFFF : 0x3A3A3A, lit ? RenderUtil.FULL_BRIGHT : light);
    }

    private void lantern(Draw d, DeviceBlockEntity be, List<ControllerBlockEntity> ctrls, int light, float time) {
        boolean upOn = false, downOn = false;
        for (ControllerBlockEntity c : ctrls) {
            int f = c.findFloorNear(be.getBlockPos());
            if (f < 0) continue;
            int stop = f < c.getGeometry().floorStop.length ? c.getGeometry().floorStop[f] : -1;
            if (stop >= 0 && stop == c.getLanternStop()) {
                if (c.getLanternDir() > 0) upOn = true;
                if (c.getLanternDir() < 0) downOn = true;
            }
        }
        PanelStyle style = be.effectiveStyle();
        double y0 = 10 / 16.0, y1 = 15 / 16.0;
        d.color(style.plate, light, 2 / 16.0 + 0.01, y0 + 0.01, 0.0005, 14 / 16.0 - 0.01, y1 - 0.01, 0.0015);
        // two lenses
        lens(d, 0.33, (y0 + y1) / 2, upOn, 0x30FF60, "▲", light);
        lens(d, 0.67, (y0 + y1) / 2, downOn, 0xFF3030, "▼", light);
    }

    private void lens(Draw d, double cx, double cy, boolean on, int color, String arrow, int light) {
        double w = 0.12, h = 0.09;
        int c = on ? color : RenderUtil.dim(color, 0.18f);
        d.color(c, on ? RenderUtil.FULL_BRIGHT : light, cx - w, cy - h, 0.0015, cx + w, cy + h, 0.008);
        d.text(Component.literal(arrow), cx, cy, 0.0085, 0.11, on ? 0xFFFFFF : RenderUtil.dim(color, 0.45f), on ? RenderUtil.FULL_BRIGHT : light);
    }

    private void indicator(Draw d, DeviceBlockEntity be, ControllerBlockEntity c, double x0, double y0, double x1, double y1, int light, float time) {
        IndicatorStyle style = be.effectiveIndicator();
        PanelStyle ps = be.effectiveStyle();
        d.color(ps.plate, light, x0 - 0.06, y0 - 0.06, 0.0005, x1 + 0.06, y1 + 0.06, 0.0015);
        String name = c == null ? "--" : c.displayName();
        int dir = c == null ? 0 : c.displayDir();
        boolean moving = c != null && c.getPhase() == ControllerBlockEntity.Phase.MOVING;
        boolean powered = c != null && c.isMainPower();
        IndicatorDraw.draw(d, style, name, dir, moving, time, x0, y0, x1, y1, 0.006, light, powered || c == null);
    }

    private void wideIndicator(Draw d, DeviceBlockEntity be, List<ControllerBlockEntity> ctrls, int light, float time) {
        IndicatorStyle style = be.effectiveIndicator();
        PanelStyle ps = be.effectiveStyle();
        double x0 = 0.03, x1 = 0.97, y0 = 4 / 16.0 + 0.03, y1 = 12 / 16.0 - 0.03;
        d.color(ps.plate, light, 0, 4 / 16.0, 0.0005, 1, 12 / 16.0, 0.0015);
        if (ctrls.size() <= 1) {
            ControllerBlockEntity c = ctrls.isEmpty() ? null : ctrls.get(0);
            String name = c == null ? "--" : c.displayName();
            IndicatorDraw.draw(d, style, name, c == null ? 0 : c.displayDir(),
                    c != null && c.getPhase() == ControllerBlockEntity.Phase.MOVING, time, x0, y0, x1, y1, 0.006, light, true);
            return;
        }
        int n = Math.min(4, ctrls.size());
        double cw = (x1 - x0) / n;
        for (int i = 0; i < n; i++) {
            ControllerBlockEntity c = ctrls.get(i);
            double cx0 = x0 + i * cw + 0.01, cx1 = x0 + (i + 1) * cw - 0.01;
            IndicatorDraw.draw(d, style, c.displayName(), c.displayDir(), c.getPhase() == ControllerBlockEntity.Phase.MOVING,
                    time, cx0, y0 + 0.08, cx1, y1, 0.006, light, c.isMainPower());
            d.textFit(c.getSettings().name, (cx0 + cx1) / 2, y0 + 0.035, 0.007, 0.05, cw * 0.9, 0xDDDDDD, RenderUtil.FULL_BRIGHT);
        }
    }

    private void destination(Draw d, DeviceBlockEntity be, ControllerBlockEntity c, int floor, int light) {
        double x0 = 3 / 16.0, x1 = 13 / 16.0, y0 = 2 / 16.0, y1 = 14 / 16.0;
        d.color(0x1C1F22, light, x0 + 0.01, y0 + 0.01, 0.0005, x1 - 0.01, y1 - 0.01, 0.002);
        d.color(0x0B2340, RenderUtil.FULL_BRIGHT, x0 + 0.04, y0 + 0.15, 0.002, x1 - 0.04, y1 - 0.04, 0.003);
        d.textFit(Component.translatable("device.qwertys_elevator.destination_title").getString(), 0.5, y1 - 0.1, 0.0035, 0.04, x1 - x0 - 0.1, 0xFFFFFF, RenderUtil.FULL_BRIGHT);
        if (c != null) {
            List<Floor> floors = c.getFloors();
            int n = floors.size();
            int cols = 3;
            int rows = Math.min(5, (n + cols - 1) / cols);
            double gx0 = x0 + 0.07, gx1 = x1 - 0.07, gy1 = y1 - 0.16, gy0 = y0 + 0.2;
            double cw = (gx1 - gx0) / cols, rh = (gy1 - gy0) / Math.max(1, rows);
            int shown = Math.min(n, cols * rows);
            for (int i = 0; i < shown; i++) {
                int col = i % cols, row = i / cols;
                double cx = gx0 + (col + 0.5) * cw, cy = gy1 - (row + 0.5) * rh;
                boolean here = i == floor;
                d.color(here ? 0x3B5F8F : 0x1E3E66, RenderUtil.FULL_BRIGHT, cx - cw * 0.4, cy - rh * 0.4, 0.003, cx + cw * 0.4, cy + rh * 0.4, 0.0036);
                d.textFit(floors.get(i).name, cx, cy, 0.0038, rh * 0.5, cw * 0.7, 0xFFFFFF, RenderUtil.FULL_BRIGHT);
            }
        }
        // card reader
        d.color(0x2A2E33, light, 0.42, y0 + 0.03, 0.002, 0.58, y0 + 0.1, 0.01);
        d.color(0x30FF60, RenderUtil.FULL_BRIGHT, 0.55, y0 + 0.08, 0.01, 0.57, y0 + 0.095, 0.011);
    }

    private void fireSwitch(Draw d, DeviceBlockEntity be, int light) {
        double x0 = 5 / 16.0, x1 = 11 / 16.0, y0 = 5 / 16.0, y1 = 11 / 16.0;
        d.color(0xC8201A, light, x0 + 0.005, y0 + 0.005, 0.0005, x1 - 0.005, y1 - 0.005, 0.002);
        d.textFit(Component.translatable("device.qwertys_elevator.fire").getString(), 0.5, y1 - 0.06, 0.0025, 0.045, x1 - x0 - 0.04, 0xFFFFFF, light);
        // key cylinder
        d.color(0xC9CED3, light, 0.46, 0.4, 0.002, 0.54, 0.48, 0.012);
        double kx = be.active ? 0.035 : 0;
        d.color(0x2A2A2A, light, 0.493 - kx * 0.3, 0.42, 0.012, 0.507 + kx, 0.46, 0.014);
        // lamp
        d.color(be.active ? 0xFF4020 : 0x501010, be.active ? RenderUtil.FULL_BRIGHT : light, 0.56, 0.36, 0.002, 0.62, 0.42, 0.012);
    }

    private void pitStop(Draw d, DeviceBlockEntity be, int light) {
        double push = be.active ? 0.02 : 0;
        d.color(0xF2C230, light, 5 / 16.0, 4 / 16.0, 0.0005, 11 / 16.0, 12 / 16.0, 0.002);
        d.color(0xD02020, light, 0.42, 0.45, 0.002, 0.58, 0.61, 0.05 - push);
        d.color(0xB01818, light, 0.4, 0.43, 0.05 - push, 0.6, 0.63, 0.07 - push);
        d.textFit(be.active ? "STOP" : "RUN", 0.5, 0.33, 0.0025, 0.04, 0.3, be.active ? 0xD02020 : 0x202020, light);
    }

    private void sign(Draw d, DeviceBlockEntity be, ControllerBlockEntity c, int light) {
        double x0 = 3 / 16.0, x1 = 13 / 16.0, y0 = 5 / 16.0, y1 = 11 / 16.0;
        if (c != null && be.effectiveStyle() == PanelStyle.VIDEO_CLASSIC) {
            VideoFixtures.capacityPlate(d,.5,.30,.55,c.getSettings(),c.getFloors().size(),light);
            return;
        }
        d.color(0xF4F4F0, light, x0 + 0.004, y0 + 0.004, 0.0005, x1 - 0.004, y1 - 0.004, 0.002);
        if (c == null) {
            d.textFit("?", 0.5, 0.5, 0.0025, 0.08, 0.5, 0x202020, light);
            return;
        }
        ElevatorSettings s = c.getSettings();
        double w = x1 - x0 - 0.05;
        d.textFit(s.name + " · " + Component.translatable(s.type.key()).getString(), 0.5, y1 - 0.06, 0.0025, 0.045, w, 0x111111, light);
        if (s.capacityPersons > 0) {
            d.textFit(Component.translatable("car.qwertys_elevator.capacity", s.capacityPersons, s.capacityKg).getString(), 0.5, 0.5, 0.0025, 0.055, w, 0x111111, light);
        } else {
            d.textFit(s.capacityKg + "kg", 0.5, 0.5, 0.0025, 0.055, w, 0x111111, light);
        }
        d.textFit(s.speedMpm + " m/min", 0.5, y0 + 0.06, 0.0025, 0.04, w, 0x333333, light);
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
