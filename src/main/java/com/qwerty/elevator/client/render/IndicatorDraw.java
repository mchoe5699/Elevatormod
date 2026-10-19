package com.qwerty.elevator.client.render;

import com.qwerty.elevator.elevator.IndicatorStyle;
import net.minecraft.network.chat.Component;

/** Draws a floor indicator face (dot-matrix LED or LCD) in the XY plane facing +Z. */
public final class IndicatorDraw {
    private IndicatorDraw() {}

    public static void draw(Draw d, IndicatorStyle style, String name, int dir, boolean moving, float time,
                            double x0, double y0, double x1, double y1, double z, int light, boolean powered) {
        double w = x1 - x0, h = y1 - y0;
        int bg = powered ? style.bg : 0x050505;
        if (style.dotMatrix) {
            d.color(0x050505, light, x0, y0, z - 0.004, x1, y1, z);
        } else {
            d.boxF(RenderUtil.white(), 0xFF000000 | bg, powered ? RenderUtil.FULL_BRIGHT : light, RenderUtil.ALL, 0, false,
                    x0, y0, z - 0.004, x1, y1, z);
        }
        if (!powered) return;
        String text = name == null ? "" : name;
        if (style.dotMatrix && DotMatrix.supports(text)) {
            String arrow = dir > 0 ? "^" : dir < 0 ? "v" : " ";
            int cols = 6 * (text.length() + 1) + 1;
            double dot = Math.min(h / 8.5, w / (cols + 1.0));
            double totalW = (cols - 2) * dot;
            double sx = x0 + (w - totalW) / 2;
            double sy = y0 + (h - 7 * dot) / 2;
            float scroll = 0;
            if (moving && dir != 0) scroll = dir > 0 ? (time * 0.35f) % 9f : -((time * 0.35f) % 9f);
            d.ps.pushPose();
            d.ps.translate(0, 0, z + 0.0015);
            int dimOff = RenderUtil.dim(style.fg, 0.10f);
            DotMatrix.draw(d.buf, d.ps, arrow, (float) sx, (float) sy, (float) dot, style.fg, dimOff, light, scroll);
            DotMatrix.draw(d.buf, d.ps, text, (float) (sx + 6 * dot), (float) sy, (float) dot, style.fg, dimOff, light, 0);
            d.ps.popPose();
        } else {
            String arrow = dir > 0 ? "▲" : dir < 0 ? "▼" : "";
            int color = style.fg;
            double th = h * 0.62;
            if (!arrow.isEmpty()) {
                boolean blink = moving && ((int) (time / 6) % 2 == 0);
                d.text(Component.literal(arrow), x0 + w * 0.22, y0 + h / 2, z + 0.002, th * 0.8, blink ? RenderUtil.dim(color, 0.4f) : color, RenderUtil.FULL_BRIGHT);
                d.textFit(text, x0 + w * 0.62, y0 + h / 2, z + 0.002, th, w * 0.62, color, RenderUtil.FULL_BRIGHT);
            } else {
                d.textFit(text, x0 + w / 2, y0 + h / 2, z + 0.002, th, w * 0.9, color, RenderUtil.FULL_BRIGHT);
            }
        }
    }
}
