package com.qwerty.elevator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qwerty.elevator.ElevatorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/** Low-level drawing helpers: textured/tinted boxes and quads using block-atlas sprites. */
public final class RenderUtil {
    private RenderUtil() {}

    public static final int FULL_BRIGHT = 0xF000F0;

    private static final Map<String, BlockState> STATE_CACHE = new HashMap<>();

    public static RenderType solid() { return Sheets.cutoutBlockSheet(); }
    public static RenderType glass() { return Sheets.translucentCullBlockSheet(); }

    public static TextureAtlasSprite sprite(String path) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(new ResourceLocation(ElevatorMod.MODID, path));
    }

    public static TextureAtlasSprite vanilla(String path) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(new ResourceLocation("minecraft", path));
    }

    public static TextureAtlasSprite white() { return sprite("block/white"); }

    public static BlockState stateOf(String id) {
        return STATE_CACHE.computeIfAbsent(id, k -> {
            ResourceLocation rl = ResourceLocation.tryParse(k);
            Block b = rl == null ? null : ForgeRegistries.BLOCKS.getValue(rl);
            if (b == null || b == Blocks.AIR) b = Blocks.IRON_BLOCK;
            return b.defaultBlockState();
        });
    }

    /** Sprite for a material given as a block id (any block can be used as a car material). */
    public static TextureAtlasSprite material(String id) {
        BlockState st = stateOf(id);
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(st).getParticleIcon(ModelData.EMPTY);
    }

    public static boolean isTranslucent(String id) {
        return id.contains("glass") || id.contains("ice") || id.contains("slime") || id.contains("honey");
    }

    // ------------------------------------------------------------------ primitive emission

    public static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, int argb,
                              float u, float v, int light, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z)
                .color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(n, nx, ny, nz)
                .endVertex();
    }

    /**
     * Draws a face spanned by edge vectors a (length la) and b (length lb) from origin o, with a×b pointing out.
     * Texture repeats every {@code tile} units. If {@code uFromA} the texture u runs along a, else along b;
     * the other axis gives v (flipped when {@code flipV}, so that "up" in the world is up in the texture).
     */
    public static void face(VertexConsumer vc, PoseStack ps, TextureAtlasSprite sp, int argb, int light,
                            float ox, float oy, float oz,
                            float ax, float ay, float az, float la,
                            float bx, float by, float bz, float lb,
                            float tile, boolean uFromA, boolean flipV) {
        if (la <= 1e-5f || lb <= 1e-5f) return;
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0) { nx /= len; ny /= len; nz /= len; }
        float ta = tile <= 0 ? la : tile, tb = tile <= 0 ? lb : tile;
        int na = Math.max(1, (int) Math.ceil(la / ta - 1e-4));
        int nb = Math.max(1, (int) Math.ceil(lb / tb - 1e-4));
        if (na * nb > 400) { ta = la; tb = lb; na = nb = 1; }
        for (int i = 0; i < na; i++) {
            float a0 = i * ta, a1 = Math.min(la, a0 + ta);
            for (int j = 0; j < nb; j++) {
                float b0 = j * tb, b1 = Math.min(lb, b0 + tb);
                float fa0 = (a0 - i * ta) / ta, fa1 = (a1 - i * ta) / ta;
                float fb0 = (b0 - j * tb) / tb, fb1 = (b1 - j * tb) / tb;
                float[][] corners = {{a0, b0, fa0, fb0}, {a1, b0, fa1, fb0}, {a1, b1, fa1, fb1}, {a0, b1, fa0, fb1}};
                for (float[] c : corners) {
                    float px = ox + ax * c[0] + bx * c[1];
                    float py = oy + ay * c[0] + by * c[1];
                    float pz = oz + az * c[0] + bz * c[1];
                    float fu = uFromA ? c[2] : c[3];
                    float fv = uFromA ? c[3] : c[2];
                    if (flipV) fv = 1 - fv;
                    vertex(vc, m, nm, px, py, pz, argb, sp.getU(fu * 16), sp.getV(fv * 16), light, nx, ny, nz);
                }
            }
        }
    }

    public static final int DOWN = 1, UP = 2, NORTH = 4, SOUTH = 8, WEST = 16, EAST = 32, ALL = 63;

    /** Axis-aligned box in the current pose. */
    public static void box(MultiBufferSource buf, RenderType type, PoseStack ps, TextureAtlasSprite sp, int argb, int light,
                           float x0, float y0, float z0, float x1, float y1, float z1, float tile, int faces) {
        VertexConsumer vc = buf.getBuffer(type);
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        if ((faces & DOWN) != 0) face(vc, ps, sp, argb, light, x0, y0, z0, 1, 0, 0, dx, 0, 0, 1, dz, tile, true, false);
        if ((faces & UP) != 0) face(vc, ps, sp, argb, light, x0, y1, z0, 0, 0, 1, dz, 1, 0, 0, dx, tile, false, false);
        if ((faces & NORTH) != 0) face(vc, ps, sp, argb, light, x0, y0, z0, 0, 1, 0, dy, 1, 0, 0, dx, tile, false, true);
        if ((faces & SOUTH) != 0) face(vc, ps, sp, argb, light, x0, y0, z1, 1, 0, 0, dx, 0, 1, 0, dy, tile, true, true);
        if ((faces & WEST) != 0) face(vc, ps, sp, argb, light, x0, y0, z0, 0, 0, 1, dz, 0, 1, 0, dy, tile, true, true);
        if ((faces & EAST) != 0) face(vc, ps, sp, argb, light, x1, y0, z0, 0, 1, 0, dy, 0, 0, 1, dz, tile, false, true);
    }

    public static void box(MultiBufferSource buf, PoseStack ps, TextureAtlasSprite sp, int argb, int light,
                           double x0, double y0, double z0, double x1, double y1, double z1) {
        box(buf, solid(), ps, sp, argb, light, (float) Math.min(x0, x1), (float) Math.min(y0, y1), (float) Math.min(z0, z1),
                (float) Math.max(x0, x1), (float) Math.max(y0, y1), (float) Math.max(z0, z1), 1f, ALL);
    }

    /** Untextured (tinted white) box. */
    public static void colorBox(MultiBufferSource buf, PoseStack ps, int rgb, int light,
                                double x0, double y0, double z0, double x1, double y1, double z1) {
        box(buf, solid(), ps, white(), 0xFF000000 | rgb, light, (float) Math.min(x0, x1), (float) Math.min(y0, y1), (float) Math.min(z0, z1),
                (float) Math.max(x0, x1), (float) Math.max(y0, y1), (float) Math.max(z0, z1), 0, ALL);
    }

    /** Thin beam with a square cross-section between two points (ropes, cables, rails along a slope). */
    public static void beam(MultiBufferSource buf, RenderType type, PoseStack ps, TextureAtlasSprite sp, int argb, int light,
                            Vec3 p0, Vec3 p1, double size, float tile) {
        Vec3 d = p1.subtract(p0);
        double len = d.length();
        if (len < 1e-5) return;
        Vec3 dir = d.scale(1 / len);
        Vec3 ref = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 s1 = dir.cross(ref).normalize().scale(size / 2);
        Vec3 s2 = dir.cross(s1).normalize().scale(size / 2);
        VertexConsumer vc = buf.getBuffer(type);
        Vec3[] side = {s1.add(s2), s1.subtract(s2), s1.scale(-1).subtract(s2), s1.scale(-1).add(s2)};
        for (int k = 0; k < 4; k++) {
            Vec3 a = side[k], b = side[(k + 1) % 4];
            Vec3 o = p0.add(b);
            Vec3 e1 = a.subtract(b);
            double l1 = e1.length();
            Vec3 e1n = e1.scale(1 / l1);
            // face spanned by e1 (across) and dir (along); choose order so the normal points outwards
            Vec3 n = e1n.cross(dir);
            Vec3 mid = a.add(b).scale(0.5);
            if (n.dot(mid) >= 0) {
                face(vc, ps, sp, argb, light, (float) o.x, (float) o.y, (float) o.z,
                        (float) e1n.x, (float) e1n.y, (float) e1n.z, (float) l1,
                        (float) dir.x, (float) dir.y, (float) dir.z, (float) len, tile, true, false);
            } else {
                Vec3 o2 = p0.add(a);
                Vec3 e2n = e1n.scale(-1);
                face(vc, ps, sp, argb, light, (float) o2.x, (float) o2.y, (float) o2.z,
                        (float) e2n.x, (float) e2n.y, (float) e2n.z, (float) l1,
                        (float) dir.x, (float) dir.y, (float) dir.z, (float) len, tile, true, false);
            }
        }
    }

    /** Vertical cylinder around (cx, cz) from y0 to y1. */
    public static void cylinder(MultiBufferSource buf, RenderType type, PoseStack ps, TextureAtlasSprite sp, int argb, int light,
                                double cx, double cz, double r, double y0, double y1, int segments, boolean caps, boolean inside) {
        VertexConsumer vc = buf.getBuffer(type);
        float h = (float) (y1 - y0);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            float x0 = (float) (cx + Math.cos(a0) * r), z0 = (float) (cz + Math.sin(a0) * r);
            float x1 = (float) (cx + Math.cos(a1) * r), z1 = (float) (cz + Math.sin(a1) * r);
            float ex = x1 - x0, ez = z1 - z0;
            float el = (float) Math.sqrt(ex * ex + ez * ez);
            if (!inside) {
                // edge (x1→x0) × up points outward
                face(vc, ps, sp, argb, light, x1, (float) y0, z1, -ex / el, 0, -ez / el, el, 0, 1, 0, h, 1f, true, true);
            } else {
                face(vc, ps, sp, argb, light, x0, (float) y0, z0, ex / el, 0, ez / el, el, 0, 1, 0, h, 1f, true, true);
            }
            if (caps) {
                Matrix4f m = ps.last().pose();
                Matrix3f nm = ps.last().normal();
                float u0 = sp.getU(8), v0 = sp.getV(8);
                float ua = sp.getU(8 + 8 * Math.cos(a0)), va = sp.getV(8 + 8 * Math.sin(a0));
                float ub = sp.getU(8 + 8 * Math.cos(a1)), vb = sp.getV(8 + 8 * Math.sin(a1));
                // top cap (CCW seen from above)
                vertex(vc, m, nm, (float) cx, (float) y1, (float) cz, argb, u0, v0, light, 0, 1, 0);
                vertex(vc, m, nm, x1, (float) y1, z1, argb, ub, vb, light, 0, 1, 0);
                vertex(vc, m, nm, x0, (float) y1, z0, argb, ua, va, light, 0, 1, 0);
                vertex(vc, m, nm, (float) cx, (float) y1, (float) cz, argb, u0, v0, light, 0, 1, 0);
                // bottom cap
                vertex(vc, m, nm, (float) cx, (float) y0, (float) cz, argb, u0, v0, light, 0, -1, 0);
                vertex(vc, m, nm, x0, (float) y0, z0, argb, ua, va, light, 0, -1, 0);
                vertex(vc, m, nm, x1, (float) y0, z1, argb, ub, vb, light, 0, -1, 0);
                vertex(vc, m, nm, (float) cx, (float) y0, (float) cz, argb, u0, v0, light, 0, -1, 0);
            }
        }
    }

    /** Disc in the plane facing +z (for sheaves etc.), centred at origin of the current pose. */
    public static void discZ(MultiBufferSource buf, PoseStack ps, TextureAtlasSprite sp, int argb, int light, double r, double z0, double z1, int segments) {
        ps.pushPose();
        ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        // rotating 90° about X maps the cylinder's y axis onto +z of the original pose
        cylinder(buf, solid(), ps, sp, argb, light, 0, 0, r, z0, z1, segments, true, false);
        ps.popPose();
    }

    public static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static int dim(int rgb, float f) {
        return lerpColor(0, rgb, f);
    }
}
