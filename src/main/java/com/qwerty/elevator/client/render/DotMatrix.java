package com.qwerty.elevator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.HashMap;
import java.util.Map;

/**
 * 5×7 dot-matrix glyphs for LED floor indicators. Draws in the XY plane of the current pose, facing +Z,
 * with (0,0) at the bottom-left of the text.
 */
public final class DotMatrix {
    private DotMatrix() {}

    private static final Map<Character, String[]> GLYPHS = new HashMap<>();

    static {
        g('0', "01110", "10001", "10011", "10101", "11001", "10001", "01110");
        g('1', "00100", "01100", "00100", "00100", "00100", "00100", "01110");
        g('2', "01110", "10001", "00001", "00010", "00100", "01000", "11111");
        g('3', "11111", "00010", "00100", "00010", "00001", "10001", "01110");
        g('4', "00010", "00110", "01010", "10010", "11111", "00010", "00010");
        g('5', "11111", "10000", "11110", "00001", "00001", "10001", "01110");
        g('6', "00110", "01000", "10000", "11110", "10001", "10001", "01110");
        g('7', "11111", "00001", "00010", "00100", "01000", "01000", "01000");
        g('8', "01110", "10001", "10001", "01110", "10001", "10001", "01110");
        g('9', "01110", "10001", "10001", "01111", "00001", "00010", "01100");
        g('A', "01110", "10001", "10001", "11111", "10001", "10001", "10001");
        g('B', "11110", "10001", "10001", "11110", "10001", "10001", "11110");
        g('C', "01110", "10001", "10000", "10000", "10000", "10001", "01110");
        g('D', "11100", "10010", "10001", "10001", "10001", "10010", "11100");
        g('E', "11111", "10000", "10000", "11110", "10000", "10000", "11111");
        g('F', "11111", "10000", "10000", "11110", "10000", "10000", "10000");
        g('G', "01110", "10001", "10000", "10111", "10001", "10001", "01111");
        g('H', "10001", "10001", "10001", "11111", "10001", "10001", "10001");
        g('I', "01110", "00100", "00100", "00100", "00100", "00100", "01110");
        g('K', "10001", "10010", "10100", "11000", "10100", "10010", "10001");
        g('L', "10000", "10000", "10000", "10000", "10000", "10000", "11111");
        g('M', "10001", "11011", "10101", "10101", "10001", "10001", "10001");
        g('N', "10001", "10001", "11001", "10101", "10011", "10001", "10001");
        g('O', "01110", "10001", "10001", "10001", "10001", "10001", "01110");
        g('P', "11110", "10001", "10001", "11110", "10000", "10000", "10000");
        g('R', "11110", "10001", "10001", "11110", "10100", "10010", "10001");
        g('S', "01111", "10000", "10000", "01110", "00001", "00001", "11110");
        g('T', "11111", "00100", "00100", "00100", "00100", "00100", "00100");
        g('U', "10001", "10001", "10001", "10001", "10001", "10001", "01110");
        g('V', "10001", "10001", "10001", "10001", "10001", "01010", "00100");
        g('W', "10001", "10001", "10001", "10101", "10101", "10101", "01010");
        g('X', "10001", "10001", "01010", "00100", "01010", "10001", "10001");
        g('Y', "10001", "10001", "01010", "00100", "00100", "00100", "00100");
        g('Z', "11111", "00001", "00010", "00100", "01000", "10000", "11111");
        g('-', "00000", "00000", "00000", "11111", "00000", "00000", "00000");
        g(' ', "00000", "00000", "00000", "00000", "00000", "00000", "00000");
        g('.', "00000", "00000", "00000", "00000", "00000", "01100", "01100");
        g('!', "00100", "00100", "00100", "00100", "00100", "00000", "00100");
        g('^', "00100", "01110", "11111", "00100", "00100", "00100", "00100"); // up arrow
        g('v', "00100", "00100", "00100", "00100", "11111", "01110", "00100"); // down arrow
        g('<', "00100", "01110", "11111", "00000", "11111", "01110", "00100"); // both
    }

    private static void g(char c, String... rows) {
        GLYPHS.put(c, rows);
    }

    public static boolean supports(String text) {
        for (char c : text.toUpperCase().toCharArray()) if (!GLYPHS.containsKey(c)) return false;
        return true;
    }

    /** Width in dots of a string (5 per char + 1 gap). */
    public static int width(String text) {
        return text.isEmpty() ? 0 : text.length() * 6 - 1;
    }

    /**
     * @param dot     size of one dot cell
     * @param on      colour of lit dots
     * @param off     colour of unlit dots (0 = don't draw)
     * @param scrollY vertical scroll in dots (for moving arrows)
     */
    public static void draw(MultiBufferSource buf, PoseStack ps, String text, float x, float y, float dot,
                            int on, int off, int light, float scrollY) {
        TextureAtlasSprite sp = RenderUtil.white();
        VertexConsumer vc = buf.getBuffer(RenderUtil.solid());
        float s = dot * 0.78f;
        String up = text.toUpperCase();
        for (int ci = 0; ci < up.length(); ci++) {
            String[] rows = GLYPHS.getOrDefault(up.charAt(ci), GLYPHS.get(' '));
            float cx = x + ci * 6 * dot;
            for (int r = 0; r < 7; r++) {
                for (int c = 0; c < 5; c++) {
                    int srcRow = r;
                    boolean lit;
                    if (scrollY != 0) {
                        float sr = r + scrollY;
                        int ir = (int) Math.floor(sr);
                        ir = ((ir % 9) + 9) % 9;
                        lit = ir < 7 && rows[ir].charAt(c) == '1';
                    } else {
                        lit = rows[srcRow].charAt(c) == '1';
                    }
                    int color = lit ? on : off;
                    if (!lit && off == 0) continue;
                    float px = cx + c * dot;
                    float py = y + (6 - r) * dot;
                    RenderUtil.face(vc, ps, sp, 0xFF000000 | color, lit ? RenderUtil.FULL_BRIGHT : light,
                            px, py, 0, 1, 0, 0, s, 0, 1, 0, s, 0, true, true);
                }
            }
        }
    }
}
