package com.qwerty.elevator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.qwerty.elevator.block.entity.MachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/** Rotating sheaves, brake drums and the main switch lever of machine-room equipment. */
public class MachineRenderer implements BlockEntityRenderer<MachineBlockEntity> {
    private static final int WHITE = 0xFFFFFFFF;

    public MachineRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(MachineBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Draw d = new Draw(buf, ps, Minecraft.getInstance().font);
        float angle = Mth.lerp(pt, be.angleOld, be.angle);
        TextureAtlasSprite sheave = RenderUtil.sprite("block/sheave");
        TextureAtlasSprite steel = RenderUtil.sprite("block/painted_steel");
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180f - be.facing().toYRot()));
        ps.translate(-0.5, 0, -0.5);
        switch (be.kind()) {
            case GEARED -> {
                // traction sheave on the right, axis along X
                ps.pushPose();
                ps.translate(0.86, 0.62, 0.5);
                ps.mulPose(Axis.YP.rotationDegrees(90));
                ps.mulPose(Axis.ZP.rotationDegrees(angle));
                RenderUtil.discZ(buf, ps, sheave, WHITE, light, 0.3, -0.07, 0.07, 24);
                ps.popPose();
                // brake drum between motor and gearbox
                ps.pushPose();
                ps.translate(0.3, 0.55, 0.5);
                ps.mulPose(Axis.YP.rotationDegrees(90));
                ps.mulPose(Axis.ZP.rotationDegrees(angle * 6));
                RenderUtil.discZ(buf, ps, RenderUtil.sprite("block/stainless_mirror"), WHITE, light, 0.17, -0.06, 0.06, 20);
                ps.popPose();
                // brake arms
                d.color(0xC8201A, light, 0.24, 0.3, 0.3, 0.36, 0.86, 0.34);
                d.color(0xC8201A, light, 0.24, 0.3, 0.66, 0.36, 0.86, 0.7);
            }
            case GEARLESS -> {
                ps.pushPose();
                ps.translate(0.5, 0.55, 0.08);
                ps.mulPose(Axis.ZP.rotationDegrees(angle));
                RenderUtil.discZ(buf, ps, sheave, WHITE, light, 0.36, -0.05, 0.06, 28);
                ps.popPose();
                d.color(0x1E2328, light, 0.46, 0.51, 0.0, 0.54, 0.59, 0.03);
            }
            case GOVERNOR -> {
                ps.pushPose();
                ps.translate(0.5, 0.68, 0.5);
                ps.mulPose(Axis.ZP.rotationDegrees(angle));
                RenderUtil.discZ(buf, ps, sheave, WHITE, light, 0.2, -0.05, 0.05, 20);
                // flyweights
                d.color(0xD02020, light, -0.12, -0.03, -0.07, -0.05, 0.03, -0.05);
                d.color(0xD02020, light, 0.05, -0.03, -0.07, 0.12, 0.03, -0.05);
                ps.popPose();
                d.color(0xF2C230, light, 0.3, 0.4, 0.32, 0.36, 0.52, 0.38);
            }
            case SHEAVE -> {
                ps.pushPose();
                ps.translate(0.5, 0.5, 0.5);
                ps.mulPose(Axis.ZP.rotationDegrees(angle));
                RenderUtil.discZ(buf, ps, sheave, WHITE, light, 0.38, -0.12, 0.12, 28);
                ps.popPose();
            }
            case HYDRAULIC -> {
                // pressure gauge needle follows the speed
                double needle = Math.min(1, Math.abs(be.speed) * 20 / 1.5);
                ps.pushPose();
                ps.translate(0.72, 0.78, 0.115);
                d.color(0xF4F4F4, light, -0.06, -0.06, -0.012, 0.06, 0.06, 0);
                ps.mulPose(Axis.ZP.rotationDegrees((float) (60 - needle * 120)));
                d.color(0xD02020, RenderUtil.FULL_BRIGHT, -0.004, 0, -0.016, 0.004, 0.05, -0.012);
                ps.popPose();
                d.color(be.speed != 0 ? 0x30FF60 : 0x204020, be.speed != 0 ? RenderUtil.FULL_BRIGHT : light, 0.2, 0.8, 0.115, 0.26, 0.86, 0.125);
            }
            case MAIN_SWITCH -> {
                ps.pushPose();
                ps.translate(0.62, 0.5, 0.78);
                ps.mulPose(Axis.ZP.rotationDegrees(be.powerOn ? 0 : 90));
                d.color(0xC8201A, light, -0.03, -0.02, -0.04, 0.03, 0.2, 0.02);
                ps.popPose();
                d.color(be.powerOn ? 0x30FF60 : 0x501010, be.powerOn ? RenderUtil.FULL_BRIGHT : light, 0.36, 0.68, 0.80, 0.42, 0.74, 0.81);
            }
        }
        ps.popPose();
    }
}
