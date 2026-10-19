package com.qwerty.elevator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.block.entity.LandingDoorBlockEntity;
import com.qwerty.elevator.elevator.DoorPanels;
import com.qwerty.elevator.elevator.DoorStyle;
import com.qwerty.elevator.elevator.Floor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Draws a whole landing door group (panels, three-sided frame, sill, floor mark) at its master block. */
public class LandingDoorRenderer implements BlockEntityRenderer<LandingDoorBlockEntity> {
    private static final int WHITE = 0xFFFFFFFF;

    public LandingDoorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(LandingDoorBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        if (!be.isMaster()) return;
        ControllerBlockEntity ctrl = null;
        BlockPos cp = be.getController();
        if (cp != null && be.getLevel() != null && be.getLevel().getBlockEntity(cp) instanceof ControllerBlockEntity c) ctrl = c;
        LandingDoorBlockEntity.GroupConfig cfg = be.getConfig();
        DoorStyle style = cfg.style != null ? cfg.style : ctrl != null ? ctrl.getSettings().doorStyle : DoorStyle.CENTER_2;
        String panelMat = !cfg.panelMat.isEmpty() ? cfg.panelMat : ctrl != null ? ctrl.getSettings().matDoor : "qwertys_elevator:stainless_hairline";
        String frameMat = !cfg.frameMat.isEmpty() ? cfg.frameMat : ctrl != null ? ctrl.getSettings().matTrim : "qwertys_elevator:stainless_mirror";
        TextureAtlasSprite panelSp = RenderUtil.material(panelMat);
        TextureAtlasSprite frameSp = RenderUtil.material(frameMat);
        boolean panelGlass = RenderUtil.isTranslucent(panelMat);
        int w = be.getGroupWidth(), h = be.getGroupHeight();
        float f = be.getOpen(pt);
        Direction facing = be.facing();
        Draw d = new Draw(buf, ps, Minecraft.getInstance().font);

        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        ps.translate(-0.5, 0, -0.5);
        // opening spans x ∈ [1-w, 1], y ∈ [0, h]; landing face z=0, hoistway face z=1
        double xc = 1 - w / 2.0;
        double xl = 1 - w, xr = 1;

        // ---- panels
        List<DoorPanels.Panel> panels = DoorPanels.panels(style, w, h, f, false);
        for (DoorPanels.Panel p : panels) {
            if (p.angle() != 0) {
                ps.pushPose();
                ps.translate(xc + p.hinge(), 0, 0.06);
                ps.mulPose(Axis.YP.rotationDegrees((float) p.angle()));
                double len = p.x1() - p.x0();
                d.boxF(panelSp, WHITE, light, RenderUtil.ALL, 1f, panelGlass, 0.01, 0, -0.05, len - 0.01, p.y1() - 0.01, 0);
                d.glass(RenderUtil.sprite("block/car_glass"), 0xA0FFFFFF, light, len * 0.3, h * 0.45, -0.052, len * 0.7, h * 0.85, 0.002);
                d.color(0x2A2A2A, light, len - 0.12, h * 0.42, -0.08, len - 0.08, h * 0.46, -0.05);
                ps.popPose();
                continue;
            }
            double z0 = 0.80 + p.layer() * 0.035, z1 = z0 + (p.bar() ? 0.025 : 0.03);
            double x0 = xc + p.x0(), x1 = xc + p.x1();
            if (p.bar()) {
                d.box(RenderUtil.sprite("block/painted_steel"), 0xFFC8CDD2, light, x0, p.y0(), z0, x1, p.y1(), z1);
            } else {
                d.boxF(panelSp, WHITE, light, RenderUtil.ALL, 1f, panelGlass, x0, p.y0(), z0, x1, p.y1(), z1);
                if (style == DoorStyle.VERTICAL) {
                    // rubber edges where the two halves meet
                    d.color(0x151515, light, x0, p.y0() > 0.01 ? p.y0() : p.y1() - 0.02, z0 - 0.001, x1, p.y0() > 0.01 ? p.y0() + 0.02 : p.y1(), z1 + 0.001);
                    d.color(0xF2C230, light, x0, p.y0() + (p.y1() - p.y0()) * 0.45, z0 - 0.002, x1, p.y0() + (p.y1() - p.y0()) * 0.5, z0);
                }
            }
        }
        if (style == DoorStyle.FOLDING_GATE) {
            for (int i = 0; i + 1 < panels.size(); i++) {
                DoorPanels.Panel a = panels.get(i), b = panels.get(i + 1);
                double xa = xc + (a.x0() + a.x1()) / 2, xb = xc + (b.x0() + b.x1()) / 2;
                for (int k = 0; k < 4; k++) {
                    double ya = 0.05 + (h - 0.1) * k / 4.0, yb = 0.05 + (h - 0.1) * (k + 1) / 4.0;
                    d.beam(RenderUtil.sprite("block/painted_steel"), 0xFFC8CDD2, light, new Vec3(xa, ya, 0.81), new Vec3(xb, yb, 0.81), 0.012, 1f);
                    d.beam(RenderUtil.sprite("block/painted_steel"), 0xFFC8CDD2, light, new Vec3(xa, yb, 0.81), new Vec3(xb, ya, 0.81), 0.012, 1f);
                }
            }
        }
        // hanger header on the hoistway side
        if (style != DoorStyle.SWING && style != DoorStyle.VERTICAL && style != DoorStyle.NONE) {
            double pocket = DoorPanels.pocket(style, w);
            double hl = style == DoorStyle.CENTER_2 || style == DoorStyle.CENTER_4 ? xl - pocket : xl - pocket;
            double hr = style == DoorStyle.CENTER_2 || style == DoorStyle.CENTER_4 ? xr + pocket : xr;
            d.color(0x3A4046, light, hl, h, 0.84, hr, h + 0.14, 0.98);
            d.color(0x8A9298, light, hl, h + 0.02, 0.83, hr, h + 0.05, 0.84);
        }

        // ---- three-sided frame (삼방틀) on the landing side
        if (style != DoorStyle.NONE) {
            double fw = cfg.wideFrame ? 0.16 : 0.06;
            d.box(frameSp, WHITE, light, xl - fw, 0, -0.035, xl, h + fw, 0.02);
            d.box(frameSp, WHITE, light, xr, 0, -0.035, xr + fw, h + fw, 0.02);
            d.box(frameSp, WHITE, light, xl - fw, h, -0.035, xr + fw, h + fw, 0.02);
            // inner returns
            d.box(frameSp, WHITE, light, xl, 0, 0, xl + 0.03, h, 0.78);
            d.box(frameSp, WHITE, light, xr - 0.03, 0, 0, xr, h, 0.78);
            d.box(frameSp, WHITE, light, xl, h - 0.03, 0, xr, h, 0.78);
        } else {
            d.color(0xF2C230, light, xl, 0, -0.01, xr, 0.04, 0.0);
        }
        // sill (승강장 문턱)
        d.box(RenderUtil.sprite("block/sill"), WHITE, light, xl - 0.03, -0.01, 0.0, xr + 0.03, 0.006, 0.99);

        // ---- floor number plate on the header
        if (cfg.showFloorLabel && style != DoorStyle.NONE) {
            String label = cfg.label;
            if (label.isEmpty() && ctrl != null) {
                for (Floor fl : ctrl.getFloors()) {
                    if (be.getBlockPos().equals(fl.front) || be.getBlockPos().equals(fl.rear)) { label = fl.name; break; }
                }
            }
            if (!label.isEmpty()) {
                double fw = cfg.wideFrame ? 0.16 : 0.06;
                ps.pushPose();
                ps.translate(xc, 0, 0);
                ps.mulPose(Axis.YP.rotationDegrees(180));
                d.textFit(label, 0, h + fw / 2 + (cfg.wideFrame ? 0 : 0.12), 0.037, Math.min(0.12, fw * 0.7 + 0.05), w * 0.5, 0x202020, light);
                ps.popPose();
            }
        }
        if (be.isManuallyOpen()) {
            ps.pushPose();
            ps.translate(xc, 0, 0);
            ps.mulPose(Axis.YP.rotationDegrees(180));
            d.text("⚠", 0, h * 0.6, 0.04, 0.3, 0xFFD02020, RenderUtil.FULL_BRIGHT);
            ps.popPose();
        }
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(LandingDoorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
