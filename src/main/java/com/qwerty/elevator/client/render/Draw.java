package com.qwerty.elevator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Convenience wrapper around a pose stack + buffer source with double-based box helpers. */
public final class Draw {
    public final MultiBufferSource buf;
    public final PoseStack ps;
    public final Font font;

    public Draw(MultiBufferSource buf, PoseStack ps, Font font) {
        this.buf = buf;
        this.ps = ps;
        this.font = font;
    }

    public void box(TextureAtlasSprite sp, int argb, int light, double x0, double y0, double z0, double x1, double y1, double z1) {
        boxF(sp, argb, light, RenderUtil.ALL, 1f, false, x0, y0, z0, x1, y1, z1);
    }

    public void boxF(TextureAtlasSprite sp, int argb, int light, int faces, float tile, boolean translucent,
                     double x0, double y0, double z0, double x1, double y1, double z1) {
        RenderType type = translucent ? RenderUtil.glass() : RenderUtil.solid();
        RenderUtil.box(buf, type, ps, sp, argb, light,
                (float) Math.min(x0, x1), (float) Math.min(y0, y1), (float) Math.min(z0, z1),
                (float) Math.max(x0, x1), (float) Math.max(y0, y1), (float) Math.max(z0, z1), tile, faces);
    }

    public void color(int rgb, int light, double x0, double y0, double z0, double x1, double y1, double z1) {
        RenderUtil.colorBox(buf, ps, rgb, light, x0, y0, z0, x1, y1, z1);
    }

    public void glass(TextureAtlasSprite sp, int argb, int light, double x0, double y0, double z0, double x1, double y1, double z1) {
        boxF(sp, argb, light, RenderUtil.ALL, 1f, true, x0, y0, z0, x1, y1, z1);
    }

    public void beam(TextureAtlasSprite sp, int argb, int light, Vec3 a, Vec3 b, double size, float tile) {
        RenderUtil.beam(buf, RenderUtil.solid(), ps, sp, argb, light, a, b, size, tile);
    }

    public void cylinder(TextureAtlasSprite sp, int argb, int light, double cx, double cz, double r, double y0, double y1, int seg, boolean caps) {
        RenderUtil.cylinder(buf, RenderUtil.solid(), ps, sp, argb, light, cx, cz, r, y0, y1, seg, caps, false);
    }

    public void cylinderGlass(TextureAtlasSprite sp, int argb, int light, double cx, double cz, double r, double y0, double y1, int seg) {
        RenderUtil.cylinder(buf, RenderUtil.glass(), ps, sp, argb, light, cx, cz, r, y0, y1, seg, false, false);
        RenderUtil.cylinder(buf, RenderUtil.glass(), ps, sp, argb, light, cx, cz, r, y0, y1, seg, false, true);
    }

    /** Text in the XY plane facing +Z, centred on (cx, cy), with the given glyph height. */
    public void text(Component text, double cx, double cy, double z, double height, int rgb, int light) {
        float sc = (float) (height / 8.0);
        int w = font.width(text);
        ps.pushPose();
        ps.translate(cx, cy, z);
        ps.scale(sc, -sc, sc);
        font.drawInBatch(text, -w / 2f, -4f, 0xFF000000 | rgb, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, light);
        ps.popPose();
    }

    public void text(String text, double cx, double cy, double z, double height, int rgb, int light) {
        text(Component.literal(text), cx, cy, z, height, rgb, light);
    }

    /** Text fitted to a maximum width. */
    public void textFit(String text, double cx, double cy, double z, double height, double maxW, int rgb, int light) {
        int w = font.width(text);
        double h = height;
        if (w > 0) {
            double natural = w * height / 8.0;
            if (natural > maxW) h = height * maxW / natural;
        }
        text(text, cx, cy, z, h, rgb, light);
    }
}
