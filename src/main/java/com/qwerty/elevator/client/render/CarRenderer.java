package com.qwerty.elevator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.qwerty.elevator.elevator.*;
import com.qwerty.elevator.entity.CarStatus;
import com.qwerty.elevator.entity.ElevatorCarEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Renders the car (카) with all its parts, and the hoistway equipment that moves with it. */
public class CarRenderer extends EntityRenderer<ElevatorCarEntity> {
    private static final double T = CarGeometry.WALL;
    private static final double FT = CarGeometry.FLOOR_T;
    private static final double RT = CarGeometry.ROOF_T;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int STEEL = 0x4E5A63;
    private static final int DARK = 0x2A2E33;
    private static final int YELLOW = 0xF2C230;

    public CarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(ElevatorCarEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public boolean shouldRender(ElevatorCarEntity e, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(e.getBoundingBoxForCulling());
    }

    /** Per-frame state. */
    private static final class Ctx {
        Draw d;
        ElevatorCarEntity car;
        ElevatorSettings s;
        CarGeometry g;
        CarStatus st;
        Level level;
        float pt;
        float time;
        int li;          // interior light
        int le;          // exterior light
        boolean lightOn;
        TextureAtlasSprite wallSp, floorSp, ceilSp, doorSp, trimSp, steelSp, glassSp, meshSp, mirrorSp, hairSp, lightSp, white;
        boolean wallGlass, doorGlass;
        double w2, depth, height;
    }

    @Override
    public void render(ElevatorCarEntity car, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int packedLight) {
        CarGeometry g = car.getGeometry();
        ElevatorSettings s = car.getSettings();
        if (!g.valid()) return;
        Ctx c = new Ctx();
        c.d = new Draw(buf, ps, getFont());
        c.car = car;
        c.s = s;
        c.g = g;
        c.st = car.getStatus();
        c.level = car.level();
        c.pt = pt;
        c.time = car.tickCount + pt;
        c.lightOn = c.st.light;
        c.wallSp = RenderUtil.material(s.matWall);
        c.floorSp = RenderUtil.material(s.matFloor);
        c.ceilSp = RenderUtil.material(s.matCeiling);
        c.doorSp = RenderUtil.material(s.matDoor);
        c.trimSp = RenderUtil.material(s.matTrim);
        c.wallGlass = RenderUtil.isTranslucent(s.matWall);
        c.doorGlass = RenderUtil.isTranslucent(s.matDoor);
        c.steelSp = RenderUtil.sprite("block/painted_steel");
        c.glassSp = RenderUtil.sprite("block/car_glass");
        c.meshSp = RenderUtil.sprite("block/mesh_panel");
        c.mirrorSp = RenderUtil.sprite("block/stainless_mirror");
        c.hairSp = RenderUtil.sprite("block/stainless_hairline");
        c.lightSp = RenderUtil.sprite("block/ceiling_light_panel");
        c.white = RenderUtil.white();
        c.w2 = s.carWidth / 2.0;
        c.depth = s.carDepth;
        c.height = s.carHeight;

        Vec3 origin = car.getPosition(pt);
        double sNow = car.getS(pt);
        float rot = 180f - g.facing.toYRot();
        List<CarGeometry.Cabin> cabins = g.cabins(sNow, car.cabinCount());
        for (CarGeometry.Cabin cabin : cabins) {
            Vec3 a = cabin.anchor();
            c.le = light(c.level, g.toWorld(a, 0, 1.2, s.carDepth / 2));
            c.li = c.lightOn ? RenderUtil.FULL_BRIGHT : c.le;
            ps.pushPose();
            ps.translate(a.x - origin.x, a.y - origin.y, a.z - origin.z);
            ps.mulPose(Axis.YP.rotationDegrees(rot));
            boolean top = cabin.deck() == g.decks - 1 || g.paternoster;
            boolean bottom = cabin.deck() == 0 || g.paternoster;
            drawCabin(c, cabin.deck(), top, bottom);
            ps.popPose();
        }
        drawShaft(c, sNow, origin, rot);
        super.render(car, yaw, pt, ps, buf, packedLight);
    }

    private static int light(Level level, Vec3 p) {
        return LevelRenderer.getLightColor(level, BlockPos.containing(p));
    }

    // =================================================================== cabin

    private void drawCabin(Ctx c, int deck, boolean top, boolean bottom) {
        ElevatorSettings s = c.s;
        Draw d = c.d;
        double w2 = c.w2, D = c.depth, H = c.height;
        boolean round = s.shape == CarShape.ROUND;
        boolean roundBack = s.shape == CarShape.BOX_ROUND_BACK && D > w2 + 0.2;

        // ---------------- floor / platform
        if (round) {
            double r = Math.min(w2, D / 2);
            d.cylinder(c.floorSp, WHITE, c.li, 0, D / 2, r, -FT, 0, 32, true);
            d.cylinder(c.mirrorSp, WHITE, c.le, 0, D / 2, r + 0.02, -FT - 0.04, -FT, 32, true);
        } else {
            d.boxF(c.floorSp, WHITE, c.li, RenderUtil.UP, 1f, false, -w2, -FT, 0, w2, 0, roundBack ? D - w2 : D);
            d.boxF(c.steelSp, 0xFF8A949C, c.le, RenderUtil.ALL & ~RenderUtil.UP, 1f, false, -w2, -FT, 0, w2, 0, roundBack ? D - w2 : D);
            if (roundBack) {
                d.cylinder(c.floorSp, WHITE, c.li, 0, D - w2, w2, -FT, 0, 32, true);
            }
        }
        // door sill (문턱)
        if (s.doorStyle != DoorStyle.NONE && c.g.anyFront) {
            double l = c.g.doorU - c.g.doorWidth / 2 - 0.04, r = c.g.doorU + c.g.doorWidth / 2 + 0.04;
            d.box(RenderUtil.sprite("block/sill"), WHITE, c.li, l, -0.012, -0.015, r, 0.004, 0.09);
        }
        if (c.g.anyRear) {
            double l = c.g.rearDoorU - c.g.rearDoorWidth / 2 - 0.04, r = c.g.rearDoorU + c.g.rearDoorWidth / 2 + 0.04;
            d.box(RenderUtil.sprite("block/sill"), WHITE, c.li, l, -0.012, D - 0.09, r, 0.004, D + 0.015);
        }

        // ---------------- walls
        if (round) drawRoundWalls(c);
        else drawBoxWalls(c, roundBack);

        // ---------------- ceiling
        if (s.ceiling) drawCeiling(c, round, roundBack);

        // ---------------- doors
        drawCarDoors(c, deck);

        // ---------------- interior equipment
        if (s.riders && !c.g.paternoster) {
            drawCop(c, deck);
            drawInteriorIndicator(c, deck);
            drawHandrails(c, round, roundBack);
            if (s.cctv && s.ceiling && H > 1.9) drawCctv(c);
        }
        if (s.storage) drawShelf(c);

        // ---------------- exterior car frame
        if (!c.g.paternoster && s.drive != DriveType.VACUUM) drawFrame(c, top, bottom);
        if (c.g.paternoster) drawPaternosterFrame(c);
        if (s.drive == DriveType.VACUUM && top) {
            double r = Math.min(w2, D / 2);
            d.cylinder(c.white, 0xFFE8ECEF, c.le, 0, D / 2, r + 0.03, H + RT, H + RT + 0.18, 32, true);
            d.cylinder(c.steelSp, WHITE, c.le, 0, D / 2, r * 0.4, H + RT + 0.18, H + RT + 0.28, 16, true);
        }
    }

    private void drawBoxWalls(Ctx c, boolean roundBack) {
        ElevatorSettings s = c.s;
        double w2 = c.w2, D = c.depth, H = c.height;
        double sideEnd = roundBack ? D - w2 : D;
        // side walls
        wall(c, s.wallLeft, -w2, 0, 0, -w2 + T, H, sideEnd, 'x', true);
        wall(c, s.wallRight, w2 - T, 0, 0, w2, H, sideEnd, 'x', false);
        // back wall
        if (roundBack) {
            drawRoundBack(c);
        } else if (c.g.anyRear) {
            double l = c.g.rearDoorU - c.g.rearDoorWidth / 2, r = c.g.rearDoorU + c.g.rearDoorWidth / 2;
            double dh = Math.min(H, c.g.doorHeight);
            frontSegment(c, -w2 + T, l, 0, H, D - T, D);
            frontSegment(c, r, w2 - T, 0, H, D - T, D);
            frontSegment(c, l, r, dh, H, D - T, D);
            jamb(c, l, r, dh, D - T - 0.01, D - T);
        } else {
            wall(c, s.wallBack, -w2 + T, 0, D - T, w2 - T, H, D, 'z', false);
            if (s.mirror && s.wallBack == WallType.SOLID && H > 1.8) {
                double top = H - 0.3;
                d(c).boxF(c.mirrorSp, 0xFFE6EEF2, c.li, RenderUtil.NORTH, 0, false, -w2 + 0.25, 0.95, D - T - 0.004, w2 - 0.25, top, D - T);
                d(c).color(0xA9B2B8, c.li, -w2 + 0.22, 0.92, D - T - 0.003, w2 - 0.22, 0.95, D - T);
                d(c).color(0xA9B2B8, c.li, -w2 + 0.22, top, D - T - 0.003, w2 - 0.22, top + 0.03, D - T);
            }
        }
        // front wall with door opening
        if (s.doorStyle == DoorStyle.NONE) {
            if (s.ceiling) frontSegment(c, -w2, w2, H - 0.18, H, 0, T);
            return;
        }
        if (!c.g.anyFront) {
            wall(c, WallType.SOLID, -w2 + T, 0, 0, w2 - T, H, T, 'z', true);
            return;
        }
        double l = c.g.doorU - c.g.doorWidth / 2, r = c.g.doorU + c.g.doorWidth / 2;
        double dh = Math.min(H, c.g.doorHeight);
        frontSegment(c, -w2 + T, l, 0, H, 0, T);
        frontSegment(c, r, w2 - T, 0, H, 0, T);
        frontSegment(c, l, r, dh, H, 0, T);
        jamb(c, l, r, dh, T, T + 0.01);
    }

    private static Draw d(Ctx c) { return c.d; }

    private void frontSegment(Ctx c, double u0, double u1, double y0, double y1, double d0, double d1) {
        if (u1 - u0 < 1e-3 || y1 - y0 < 1e-3) return;
        c.d.boxF(c.wallSp, WHITE, c.li, RenderUtil.ALL, 1f, c.wallGlass, u0, y0, d0, u1, y1, d1);
    }

    /** Door frame (jamb + header) around an opening, slightly proud of the wall. */
    private void jamb(Ctx c, double l, double r, double dh, double d0, double d1) {
        double t = 0.035;
        c.d.box(c.trimSp, WHITE, c.li, l - t, 0, d0, l, dh + t, d1);
        c.d.box(c.trimSp, WHITE, c.li, r, 0, d0, r + t, dh + t, d1);
        c.d.box(c.trimSp, WHITE, c.li, l - t, dh, d0, r + t, dh + t, d1);
    }

    /**
     * Draws one wall panel of the given type. axis 'x' = wall in the YZ plane (side wall), 'z' = XY plane.
     * {@code minSideInterior}: whether the interior is towards the max side of the thin axis.
     */
    private void wall(Ctx c, WallType type, double x0, double y0, double z0, double x1, double y1, double z1, char axis, boolean minSideIsOutside) {
        Draw d = c.d;
        double H = y1 - y0;
        double frame = 0.05;
        switch (type) {
            case SOLID -> {
                d.boxF(c.wallSp, WHITE, c.li, RenderUtil.ALL, 1f, c.wallGlass, x0, y0, z0, x1, y1, z1);
                // kick plate (걸레받이)
                if (axis == 'x') {
                    double kx0 = minSideIsOutside ? x1 : x0 - 0.006, kx1 = minSideIsOutside ? x1 + 0.006 : x0;
                    d.box(c.hairSp, 0xFFB8C0C6, c.li, kx0, y0, z0 + 0.02, kx1, y0 + 0.12, z1 - 0.02);
                } else {
                    double kz0 = z0 - 0.006, kz1 = z0;
                    if (minSideIsOutside) { kz0 = z1; kz1 = z1 + 0.006; }
                    d.box(c.hairSp, 0xFFB8C0C6, c.li, x0 + 0.02, y0, kz0, x1 - 0.02, y0 + 0.12, kz1);
                }
            }
            case GLASS, HALF_GLASS -> {
                double gy0 = y0;
                if (type == WallType.HALF_GLASS) {
                    double split = Math.min(H * 0.45, 1.0);
                    d.boxF(c.wallSp, WHITE, c.li, RenderUtil.ALL, 1f, c.wallGlass, x0, y0, z0, x1, y0 + split, z1);
                    gy0 = y0 + split;
                }
                frameAround(c, x0, gy0, z0, x1, y1, z1, axis, frame);
                if (axis == 'x') d.glass(c.glassSp, 0xB0FFFFFF, c.li, x0 + 0.02, gy0 + frame, z0 + frame, x1 - 0.02, y1 - frame, z1 - frame);
                else d.glass(c.glassSp, 0xB0FFFFFF, c.li, x0 + frame, gy0 + frame, z0 + 0.02, x1 - frame, y1 - frame, z1 - 0.02);
                // mullions every ~1.2 m
                double len = axis == 'x' ? z1 - z0 : x1 - x0;
                int n = (int) Math.floor(len / 1.2);
                for (int i = 1; i <= n; i++) {
                    double p = (axis == 'x' ? z0 : x0) + len * i / (n + 1);
                    if (axis == 'x') d.box(c.trimSp, WHITE, c.li, x0, gy0, p - 0.02, x1, y1, p + 0.02);
                    else d.box(c.trimSp, WHITE, c.li, p - 0.02, gy0, z0, p + 0.02, y1, z1);
                }
            }
            case MESH -> {
                frameAround(c, x0, y0, z0, x1, y1, z1, axis, frame);
                if (axis == 'x') d.box(c.meshSp, WHITE, c.li, x0 + 0.02, y0 + frame, z0 + frame, x1 - 0.02, y1 - frame, z1 - frame);
                else d.box(c.meshSp, WHITE, c.li, x0 + frame, y0 + frame, z0 + 0.02, x1 - frame, y1 - frame, z1 - 0.02);
            }
            case RAILING -> {
                double rh = Math.min(1.05, H);
                double len = axis == 'x' ? z1 - z0 : x1 - x0;
                int posts = Math.max(2, (int) Math.ceil(len / 0.9) + 1);
                for (int i = 0; i < posts; i++) {
                    double p = (axis == 'x' ? z0 : x0) + len * i / (posts - 1);
                    p = Math.max((axis == 'x' ? z0 : x0) + 0.02, Math.min((axis == 'x' ? z1 : x1) - 0.02, p));
                    double cx = axis == 'x' ? (x0 + x1) / 2 : p, cz = axis == 'x' ? p : (z0 + z1) / 2;
                    d.box(c.mirrorSp, WHITE, c.li, cx - 0.02, y0, cz - 0.02, cx + 0.02, y0 + rh, cz + 0.02);
                }
                for (double ry : new double[]{rh, rh * 0.5, 0.1}) {
                    if (axis == 'x') d.box(c.mirrorSp, WHITE, c.li, (x0 + x1) / 2 - 0.02, y0 + ry - 0.04, z0, (x0 + x1) / 2 + 0.02, y0 + ry, z1);
                    else d.box(c.mirrorSp, WHITE, c.li, x0, y0 + ry - 0.04, (z0 + z1) / 2 - 0.02, x1, y0 + ry, (z0 + z1) / 2 + 0.02);
                }
            }
            case NONE -> { }
        }
    }

    private void frameAround(Ctx c, double x0, double y0, double z0, double x1, double y1, double z1, char axis, double f) {
        Draw d = c.d;
        if (axis == 'x') {
            d.box(c.trimSp, WHITE, c.li, x0, y0, z0, x1, y0 + f, z1);
            d.box(c.trimSp, WHITE, c.li, x0, y1 - f, z0, x1, y1, z1);
            d.box(c.trimSp, WHITE, c.li, x0, y0, z0, x1, y1, z0 + f);
            d.box(c.trimSp, WHITE, c.li, x0, y0, z1 - f, x1, y1, z1);
        } else {
            d.box(c.trimSp, WHITE, c.li, x0, y0, z0, x1, y0 + f, z1);
            d.box(c.trimSp, WHITE, c.li, x0, y1 - f, z0, x1, y1, z1);
            d.box(c.trimSp, WHITE, c.li, x0, y0, z0, x0 + f, y1, z1);
            d.box(c.trimSp, WHITE, c.li, x1 - f, y0, z0, x1, y1, z1);
        }
    }

    private void drawRoundBack(Ctx c) {
        ElevatorSettings s = c.s;
        Draw d = c.d;
        double w2 = c.w2, D = c.depth, H = c.height;
        double cz = D - w2;
        boolean glass = s.wallBack == WallType.GLASS || s.wallBack == WallType.HALF_GLASS;
        int seg = 16;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * i / seg, a1 = Math.PI * (i + 1) / seg;
            double x0 = Math.cos(a0) * w2, z0 = cz + Math.sin(a0) * w2;
            double x1 = Math.cos(a1) * w2, z1 = cz + Math.sin(a1) * w2;
            Vec3 p0 = new Vec3(x0, 0, z0), p1 = new Vec3(x1, 0, z1);
            if (glass) {
                panelBetween(c, p0, p1, 0.08, H - 0.08, true);
            } else if (s.wallBack != WallType.NONE) {
                panelBetween(c, p0, p1, 0, H, false);
            }
        }
        // top and bottom rails of the curved wall
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * i / seg, a1 = Math.PI * (i + 1) / seg;
            Vec3 p0 = new Vec3(Math.cos(a0) * w2, 0, cz + Math.sin(a0) * w2);
            Vec3 p1 = new Vec3(Math.cos(a1) * w2, 0, cz + Math.sin(a1) * w2);
            d.beam(c.trimSp, WHITE, c.li, p0.add(0, 0.04, 0), p1.add(0, 0.04, 0), 0.08, 1f);
            d.beam(c.trimSp, WHITE, c.li, p0.add(0, H - 0.04, 0), p1.add(0, H - 0.04, 0), 0.08, 1f);
            if (s.handrail) {
                Vec3 in0 = new Vec3(Math.cos(a0) * (w2 - 0.08), 0.9, cz + Math.sin(a0) * (w2 - 0.08));
                Vec3 in1 = new Vec3(Math.cos(a1) * (w2 - 0.08), 0.9, cz + Math.sin(a1) * (w2 - 0.08));
                d.beam(c.mirrorSp, WHITE, c.li, in0, in1, 0.04, 1f);
            }
        }
    }

    /** A vertical wall quad-panel between two floor points (for curved walls). */
    private void panelBetween(Ctx c, Vec3 p0, Vec3 p1, double y0, double y1, boolean glass) {
        Vec3 e = p1.subtract(p0);
        double len = e.length();
        Vec3 n = e.normalize();
        com.mojang.blaze3d.vertex.VertexConsumer vc = c.d.buf.getBuffer(glass ? RenderUtil.glass() : RenderUtil.solid());
        TextureAtlasSprite sp = glass ? c.glassSp : c.wallSp;
        int tint = glass ? 0xB0FFFFFF : WHITE;
        // outer face (normal = e × up points away from the centre for this winding)
        RenderUtil.face(vc, c.d.ps, sp, tint, c.li, (float) p0.x, (float) y0, (float) p0.z,
                (float) n.x, 0, (float) n.z, (float) len, 0, 1, 0, (float) (y1 - y0), 1f, true, true);
        RenderUtil.face(vc, c.d.ps, sp, tint, c.li, (float) p1.x, (float) y0, (float) p1.z,
                (float) -n.x, 0, (float) -n.z, (float) len, 0, 1, 0, (float) (y1 - y0), 1f, true, true);
    }

    private void drawRoundWalls(Ctx c) {
        ElevatorSettings s = c.s;
        Draw d = c.d;
        double D = c.depth, H = c.height;
        double r = Math.min(c.w2, D / 2);
        double cz = D / 2;
        int seg = 32;
        double open = c.g.anyFront && s.doorStyle != DoorStyle.NONE ? Math.min(Math.PI * 0.9, c.g.doorWidth / r) : 0;
        double frontAngle = -Math.PI / 2; // towards -z (front)
        boolean glass = s.wallBack == WallType.GLASS || s.wallBack == WallType.HALF_GLASS || s.wallBack == WallType.NONE;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            double mid = (a0 + a1) / 2;
            double diff = Math.atan2(Math.sin(mid - frontAngle), Math.cos(mid - frontAngle));
            if (Math.abs(diff) < open / 2) continue;
            Vec3 p0 = new Vec3(Math.cos(a1) * r, 0, cz + Math.sin(a1) * r);
            Vec3 p1 = new Vec3(Math.cos(a0) * r, 0, cz + Math.sin(a0) * r);
            panelBetween(c, p0, p1, 0.05, H - 0.05, glass);
        }
        // rings top/bottom and posts at the opening
        d.cylinder(c.trimSp, WHITE, c.li, 0, cz, r + 0.01, 0, 0.05, 32, false);
        d.cylinder(c.trimSp, WHITE, c.li, 0, cz, r + 0.01, H - 0.05, H, 32, false);
        if (open > 0) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double a = frontAngle + sgn * open / 2;
                double x = Math.cos(a) * r, z = cz + Math.sin(a) * r;
                d.box(c.trimSp, WHITE, c.li, x - 0.03, 0, z - 0.03, x + 0.03, H, z + 0.03);
            }
        }
    }

    private void drawCeiling(Ctx c, boolean round, boolean roundBack) {
        ElevatorSettings s = c.s;
        Draw d = c.d;
        double w2 = c.w2, D = c.depth, H = c.height;
        int lightColor = c.lightOn ? s.lightColor : 0x3A3A3A;
        int lightLight = c.lightOn ? RenderUtil.FULL_BRIGHT : c.le;
        if (round) {
            double r = Math.min(w2, D / 2);
            d.cylinder(c.ceilSp, WHITE, c.li, 0, D / 2, r, H, H + RT, 32, true);
            d.cylinder(c.lightSp, 0xFF000000 | lightColor, lightLight, 0, D / 2, r * 0.6, H - 0.012, H, 24, true);
            return;
        }
        double end = roundBack ? D - w2 : D;
        d.box(c.ceilSp, WHITE, c.li, -w2, H, 0, w2, H + RT, end);
        if (roundBack) d.cylinder(c.ceilSp, WHITE, c.li, 0, D - w2, w2, H, H + RT, 32, true);
        // light panels
        int nx = Math.max(1, (int) Math.round(s.carWidth / 1.1));
        int nz = Math.max(1, (int) Math.round(D / 1.1));
        double pw = Math.min(0.6, s.carWidth / nx * 0.62), pd = Math.min(0.6, D / nz * 0.62);
        for (int i = 0; i < nx; i++) {
            for (int k = 0; k < nz; k++) {
                double cx = -w2 + s.carWidth * (i + 0.5) / nx;
                double cz = D * (k + 0.5) / nz;
                if (roundBack && cz > D - w2) continue;
                d.color(0x9AA3AA, c.li, cx - pw / 2 - 0.02, H - 0.006, cz - pd / 2 - 0.02, cx + pw / 2 + 0.02, H, cz + pd / 2 + 0.02);
                d.boxF(c.lightSp, 0xFF000000 | lightColor, lightLight, RenderUtil.ALL, 0, false,
                        cx - pw / 2, H - 0.014, cz - pd / 2, cx + pw / 2, H - 0.006, cz + pd / 2);
            }
        }
        // ventilation grille and emergency light near the front
        double gx = -w2 + 0.25;
        for (int i = 0; i < 5; i++) {
            d.color(0x15181B, c.li, gx + i * 0.05, H - 0.004, 0.25, gx + i * 0.05 + 0.025, H, 0.55);
        }
        d.color(c.lightOn ? 0xFFFFFF : 0x77FF77, RenderUtil.FULL_BRIGHT, w2 - 0.35, H - 0.02, 0.2, w2 - 0.25, H, 0.26);
    }

    private void drawCarDoors(Ctx c, int deck) {
        ElevatorSettings s = c.s;
        if (s.doorStyle == DoorStyle.NONE) return;
        float f = c.car.doorFront(deck, c.pt);
        float r = c.car.doorRear(deck, c.pt);
        double dh = Math.min(c.height, c.g.doorHeight);
        if (c.g.anyFront) drawDoorSet(c, s.doorStyle, c.g.doorU, c.g.doorWidth, dh, f, false);
        if (c.g.anyRear) drawDoorSet(c, s.doorStyle, c.g.rearDoorU, c.g.rearDoorWidth, dh, r, true);
    }

    private void drawDoorSet(Ctx c, DoorStyle style, double centerU, double w, double h, double f, boolean rear) {
        Draw d = c.d;
        List<DoorPanels.Panel> panels = DoorPanels.panels(style, w, h, f, true);
        double w2 = c.w2;
        double baseD = rear ? c.depth + 0.006 : -0.006;
        double sign = rear ? 1 : -1;
        int lightP = c.li;
        if (c.s.shape == CarShape.ROUND && !rear) {
            drawRoundDoor(c, panels, w, h, f);
            return;
        }
        for (DoorPanels.Panel p : panels) {
            double x0 = centerU + (rear ? -p.x1() : p.x0());
            double x1 = centerU + (rear ? -p.x0() : p.x1());
            if (p.angle() != 0) {
                // swinging gate around its hinge
                double hinge = centerU + (rear ? -p.hinge() : p.hinge());
                d.ps.pushPose();
                d.ps.translate(hinge, 0, baseD);
                d.ps.mulPose(Axis.YP.rotationDegrees((float) (rear ? p.angle() : -p.angle())));
                double len = p.x1() - p.x0();
                double gx0 = rear ? -len : 0, gx1 = rear ? 0 : len;
                d.box(c.steelSp, 0xFFE0E4E8, lightP, gx0, p.y0(), -0.02, gx1, p.y1(), 0.0);
                d.glass(c.glassSp, 0xA0FFFFFF, lightP, gx0 + 0.05, p.y0() + 0.15, -0.015, gx1 - 0.05, p.y1() - 0.08, -0.005);
                d.ps.popPose();
                continue;
            }
            x0 = Math.max(-w2, x0);
            x1 = Math.min(w2, x1);
            if (x1 - x0 < 0.002) continue;
            double y0 = Math.max(p.bar() ? 0 : -0.0, p.y0()), y1 = Math.min(c.height, p.y1());
            if (y1 - y0 < 0.002) continue;
            double layer = p.layer() * 0.024;
            double z0 = baseD + sign * layer, z1 = z0 + sign * (p.bar() ? 0.02 : 0.018);
            if (p.bar()) {
                d.box(c.steelSp, 0xFFC8CDD2, lightP, x0, y0, Math.min(z0, z1), x1, y1, Math.max(z0, z1));
            } else {
                d.boxF(c.doorSp, WHITE, lightP, RenderUtil.ALL, 1f, c.doorGlass, x0, y0, Math.min(z0, z1), x1, y1, Math.max(z0, z1));
                // rubber edge at the leading edge
                d.color(0x1A1A1A, lightP, x1 - 0.008, y0, Math.min(z0, z1) - 0.001, x1, y1, Math.max(z0, z1) + 0.001);
            }
        }
        if (style == DoorStyle.FOLDING_GATE) {
            // lattice between bars
            for (int i = 0; i + 1 < panels.size(); i++) {
                DoorPanels.Panel a = panels.get(i), b = panels.get(i + 1);
                double xa = centerU + (rear ? -(a.x0() + a.x1()) / 2 : (a.x0() + a.x1()) / 2);
                double xb = centerU + (rear ? -(b.x0() + b.x1()) / 2 : (b.x0() + b.x1()) / 2);
                double z = baseD + sign * 0.01;
                double top = Math.min(c.height, a.y1());
                int levels = 3;
                for (int k = 0; k < levels; k++) {
                    double ya = 0.05 + (top - 0.1) * k / levels, yb = 0.05 + (top - 0.1) * (k + 1) / levels;
                    d.beam(c.steelSp, 0xFFC8CDD2, lightP, new Vec3(xa, ya, z), new Vec3(xb, yb, z), 0.012, 1f);
                    d.beam(c.steelSp, 0xFFC8CDD2, lightP, new Vec3(xa, yb, z), new Vec3(xb, ya, z), 0.012, 1f);
                }
            }
        }
        // door operator header and hanger rail on the outside of the car
        if (!rear) {
            double l = centerU - w / 2 - DoorPanels.pocket(style, w), rr = centerU + w / 2 + (style == DoorStyle.CENTER_2 || style == DoorStyle.CENTER_4 ? w / 2 : 0);
            l = Math.max(-w2, l);
            rr = Math.min(w2, rr);
            if (style != DoorStyle.SWING && style != DoorStyle.FOLDING_GATE && style != DoorStyle.VERTICAL) {
                d.color(DARK, c.le, l, h, -0.06, rr, h + 0.08, -0.002);
            }
        }
    }

    private void drawRoundDoor(Ctx c, List<DoorPanels.Panel> panels, double w, double h, double f) {
        // curved sliding door following the cabin wall
        double r = Math.min(c.w2, c.depth / 2) + 0.012;
        double cz = c.depth / 2;
        double open = Math.min(Math.PI * 0.9, w / (r - 0.012));
        double front = -Math.PI / 2;
        double shift = f * open;
        int seg = 8;
        for (int i = 0; i < seg; i++) {
            double a0 = front - open / 2 + open * i / seg + shift;
            double a1 = front - open / 2 + open * (i + 1) / seg + shift;
            Vec3 p0 = new Vec3(Math.cos(a1) * r, 0, cz + Math.sin(a1) * r);
            Vec3 p1 = new Vec3(Math.cos(a0) * r, 0, cz + Math.sin(a0) * r);
            panelBetween(c, p0, p1, 0.05, h - 0.02, true);
        }
    }

    // =================================================================== interior equipment

    private void drawCop(Ctx c, int deck) {
        ElevatorSettings s = c.s;
        Draw d = c.d;
        List<CopLayout.Mount> mounts = CopLayout.mounts(s, c.g, deck);
        PanelStyle ps = s.panelStyle;
        int plate = ps.plate, hi = ps.highlight;
        int lit = s.buttonLight.color;
        for (CopLayout.Mount m : mounts) {
            d.ps.pushPose();
            d.ps.translate(m.origin().x, m.origin().y, m.origin().z);
            float yaw = (float) Math.toDegrees(Math.atan2(-m.xAxis().z, m.xAxis().x));
            d.ps.mulPose(Axis.YP.rotationDegrees(yaw));
            double W = m.width(), Hh = m.height();
            // faceplate
            d.color(plate, c.li, 0, 0, -0.012, W, Hh, 0);
            d.color(hi, c.li, 0.006, 0.006, -0.0005, W - 0.006, Hh - 0.006, 0.0005);
            d.color(plate, c.li, 0.012, 0.012, 0.0, W - 0.012, Hh - 0.012, 0.0012);
            // display
            double dx0 = 0.03, dx1 = W - 0.03;
            String name = c.st.display;
            if (c.g.decks > 1 && c.st.stop >= 0 && c.st.stop < c.g.stopFloors.length) {
                int[] fl = c.g.stopFloors[c.st.stop];
                if (deck < fl.length && fl[deck] >= 0 && fl[deck] < c.g.floorNames.length) name = c.g.floorNames[fl[deck]];
            }
            IndicatorDraw.draw(d, s.indicatorStyle, name, c.st.dir, c.st.moving, c.time,
                    dx0, m.displayY0(), dx1, m.displayY1(), 0.004, c.li, c.lightOn || c.st.moving);
            // capacity text under the display
            double capY = m.displayY0() - 0.03;
            String cap = s.capacityPersons > 0
                    ? Component.translatable("car.qwertys_elevator.capacity", s.capacityPersons, s.capacityKg).getString()
                    : s.capacityKg + "kg";
            d.textFit(cap, W / 2, capY, 0.0025, 0.018, W - 0.04, 0x1A1A1A, c.li);
            if (!c.st.message.isEmpty()) {
                d.textFit(Component.translatable(c.st.message).getString(), W / 2, capY - 0.026, 0.0025, 0.018, W - 0.04,
                        c.st.overload ? 0xD01010 : 0x8A1010, RenderUtil.FULL_BRIGHT);
            }
            // buttons
            for (CopLayout.CopButton b : m.buttons()) {
                double half = b.size() / 2;
                boolean on = false;
                int color = 0xE9EDF0;
                String label = "";
                int labelColor = 0x202020;
                switch (b.type()) {
                    case FLOOR -> {
                        on = c.st.isCalled(b.floor());
                        label = b.floor() < c.g.floorNames.length ? c.g.floorNames[b.floor()] : "?";
                    }
                    case OPEN -> label = "◀▶";
                    case CLOSE -> label = "▶◀";
                    case ALARM -> { color = 0xF2C230; label = "!"; on = c.st.alarm; }
                    case PHONE -> { color = 0x6BBF59; label = "☎"; }
                    case HOLD -> { label = "H"; on = c.st.doorHold; }
                    case FIRE -> { color = 0xD8302A; label = "F"; on = c.st.mode == ElevatorMode.FIRE_SERVICE; labelColor = 0xFFFFFF; }
                }
                int face = on ? lit : color;
                int faceLight = on ? RenderUtil.FULL_BRIGHT : c.li;
                d.color(hi, c.li, b.cx() - half * 1.18, b.cy() - half * 1.18, 0.0012, b.cx() + half * 1.18, b.cy() + half * 1.18, 0.0035);
                d.color(face, faceLight, b.cx() - half, b.cy() - half, 0.0035, b.cx() + half, b.cy() + half, 0.0075);
                if (on) d.color(lit, RenderUtil.FULL_BRIGHT, b.cx() - half * 1.12, b.cy() - half * 1.12, 0.0036, b.cx() + half * 1.12, b.cy() + half * 1.12, 0.0042);
                if (!label.isEmpty()) {
                    d.textFit(label, b.cx(), b.cy(), 0.0078, b.size() * 0.5, b.size() * 0.9, on ? 0xFFFFFF : labelColor, on ? RenderUtil.FULL_BRIGHT : c.li);
                }
                // braille dots next to floor buttons
                if (b.type() == CopLayout.ButtonType.FLOOR) {
                    d.color(hi, c.li, b.cx() - half - 0.016, b.cy() - 0.004, 0.0012, b.cx() - half - 0.008, b.cy() + 0.004, 0.004);
                }
            }
            // key switch for independent / fire service
            d.color(0x9CA3A8, c.li, W - 0.07, 0.025, 0.0012, W - 0.03, 0.065, 0.006);
            d.color(0x2A2A2A, c.li, W - 0.055, 0.04, 0.006, W - 0.045, 0.05, 0.008);
            d.ps.popPose();
        }
    }

    private void drawInteriorIndicator(Ctx c, int deck) {
        ElevatorSettings s = c.s;
        if (!c.g.anyFront || s.doorStyle == DoorStyle.NONE) return;
        double dh = Math.min(c.height, c.g.doorHeight);
        double space = c.height - dh;
        if (space < 0.22) return;
        double h = Math.min(0.2, space - 0.08);
        double w = Math.min(c.g.doorWidth * 0.6, 0.55);
        double cx = c.g.doorU;
        double y0 = dh + (space - h) / 2;
        String name = c.st.display;
        if (c.g.decks > 1 && c.st.stop >= 0 && c.st.stop < c.g.stopFloors.length) {
            int[] fl = c.g.stopFloors[c.st.stop];
            if (deck < fl.length && fl[deck] >= 0 && fl[deck] < c.g.floorNames.length) name = c.g.floorNames[fl[deck]];
        }
        c.d.color(s.panelStyle.plate, c.li, cx - w / 2 - 0.02, y0 - 0.02, T, cx + w / 2 + 0.02, y0 + h + 0.02, T + 0.006);
        IndicatorDraw.draw(c.d, s.indicatorStyle, name, c.st.dir, c.st.moving, c.time,
                cx - w / 2, y0, cx + w / 2, y0 + h, T + 0.01, c.li, c.lightOn || c.st.moving);
    }

    private void drawHandrails(Ctx c, boolean round, boolean roundBack) {
        ElevatorSettings s = c.s;
        if (!s.handrail || round || roundBack) return;
        Draw d = c.d;
        double w2 = c.w2, D = c.depth;
        double y = Math.min(0.9, c.height * 0.4);
        if (s.wallBack != WallType.NONE && s.wallBack != WallType.RAILING && !c.g.anyRear) {
            double z = D - T - 0.07;
            d.box(c.mirrorSp, WHITE, c.li, -w2 + 0.2, y - 0.02, z - 0.02, w2 - 0.2, y + 0.02, z + 0.02);
            for (double x : new double[]{-w2 + 0.25, 0, w2 - 0.25}) {
                if (s.carWidth < 1.5 && x == 0) continue;
                d.box(c.mirrorSp, WHITE, c.li, x - 0.012, y - 0.012, z, x + 0.012, y + 0.012, D - T);
            }
        }
        boolean sides = s.type == ElevatorType.BED || s.type == ElevatorType.PLATFORM_LIFT || s.carDepth >= 3;
        if (sides) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                WallType wt = sgn < 0 ? s.wallLeft : s.wallRight;
                if (wt == WallType.NONE || wt == WallType.RAILING) continue;
                double x = sgn * (w2 - T - 0.07);
                d.box(c.mirrorSp, WHITE, c.li, x - 0.02, y - 0.02, 0.35, x + 0.02, y + 0.02, D - 0.35);
                for (double z : new double[]{0.4, D - 0.4}) {
                    double wx = sgn * (w2 - T);
                    d.box(c.mirrorSp, WHITE, c.li, Math.min(x, wx), y - 0.012, z - 0.012, Math.max(x, wx), y + 0.012, z + 0.012);
                }
            }
        }
    }

    private void drawCctv(Ctx c) {
        double x = c.w2 - 0.2, z = c.depth - 0.2, H = c.height;
        c.d.color(0xE8E8E8, c.li, x - 0.06, H - 0.02, z - 0.06, x + 0.06, H, z + 0.06);
        c.d.color(0x15181B, c.li, x - 0.04, H - 0.07, z - 0.04, x + 0.04, H - 0.02, z + 0.04);
        c.d.color(0xD02020, RenderUtil.FULL_BRIGHT, x - 0.008, H - 0.075, z - 0.04 - 0.002, x + 0.008, H - 0.065, z - 0.04);
    }

    private void drawShelf(Ctx c) {
        // dumbwaiter: middle shelf
        double y = c.height / 2;
        c.d.box(c.hairSp, WHITE, c.li, -c.w2 + T, y - 0.02, 0.05, c.w2 - T, y, c.depth - T);
    }

    // =================================================================== exterior frame

    private void drawFrame(Ctx c, boolean top, boolean bottom) {
        ElevatorSettings s = c.s;
        Draw d = c.d;
        double w2 = c.w2, D = c.depth, H = c.height;
        int le = c.le;
        boolean platform = !s.ceiling;
        double frameTop = platform ? Math.min(H, 1.1) : H + RT + 0.3;
        double mid = D / 2;
        // car sling uprights (stiles) with guide shoes
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            double x0 = sgn * (w2 + 0.01), x1 = sgn * (w2 + 0.1);
            d.box(c.steelSp, 0xFF6B7780, le, Math.min(x0, x1), -FT - 0.26, mid - 0.07, Math.max(x0, x1), frameTop, mid + 0.07);
            for (double y : new double[]{-FT - 0.2, frameTop - 0.12}) {
                double gx0 = sgn * (w2 + 0.1), gx1 = sgn * (w2 + 0.2);
                d.color(DARK, le, Math.min(gx0, gx1), y, mid - 0.06, Math.max(gx0, gx1), y + 0.1, mid + 0.06);
                d.color(0x111111, le, Math.min(gx0, gx1) + (sgn > 0 ? 0.06 : 0), y + 0.01, mid - 0.025, Math.max(gx0, gx1) - (sgn < 0 ? 0.06 : 0), y + 0.09, mid + 0.025);
            }
        }
        if (bottom) {
            // safety plank with safety gear, and toe guard (에이프런)
            d.box(c.steelSp, 0xFF6B7780, le, -w2 - 0.1, -FT - 0.26, mid - 0.09, w2 + 0.1, -FT - 0.08, mid + 0.09);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                d.color(0xB03030, le, sgn * (w2 + 0.1) - 0.05, -FT - 0.32, mid - 0.06, sgn * (w2 + 0.1) + 0.05, -FT - 0.2, mid + 0.06);
            }
            if (c.g.anyFront && s.doorStyle != DoorStyle.NONE) {
                double l = c.g.doorU - c.g.doorWidth / 2 - 0.05, r = c.g.doorU + c.g.doorWidth / 2 + 0.05;
                double apron = Math.min(0.75, Math.max(0.3, s.pitDepth * 0.5));
                d.box(c.steelSp, 0xFFAAB2B8, le, l, -FT - apron, 0.0, r, -FT, 0.02);
                d.boxF(RenderUtil.sprite("block/hazard_stripe"), WHITE, le, RenderUtil.ALL, 0.5f, false, l, -FT - apron, -0.004, r, -FT - apron + 0.08, 0.0);
            }
            // buffer striker plates
            d.color(0x30353A, le, -0.25, -FT - 0.3, mid - 0.15, 0.25, -FT - 0.26, mid + 0.15);
            // traveling cable hanger on the left side of the platform
            d.color(DARK, le, -w2 - 0.16, -FT - 0.2, D * 0.7 - 0.05, -w2, -FT - 0.12, D * 0.7 + 0.05);
        }
        if (top && !platform) {
            double y0 = H + RT;
            // crosshead
            d.box(c.steelSp, 0xFF6B7780, le, -w2 - 0.1, y0 + 0.12, mid - 0.09, w2 + 0.1, y0 + 0.3, mid + 0.09);
            // rope hitch / sheaves
            if (s.drive == DriveType.TRACTION_MRL || s.drive == DriveType.HYDRAULIC_ROPED) {
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    d.ps.pushPose();
                    d.ps.translate(sgn * w2 * 0.45, y0 + 0.28, mid);
                    d.ps.mulPose(Axis.YP.rotationDegrees(90));
                    d.ps.mulPose(Axis.ZP.rotationDegrees(c.car.spin * 200f));
                    RenderUtil.discZ(d.buf, d.ps, RenderUtil.sprite("block/sheave"), WHITE, le, 0.22, -0.04, 0.04, 20);
                    d.ps.popPose();
                }
            } else if (s.drive.ropes || s.drive == DriveType.TRACTION_MR) {
                d.color(0x8D969D, le, -0.12, y0 + 0.3, mid - 0.05, 0.12, y0 + 0.4, mid + 0.05);
            }
            // door operator (도어 머신)
            if (c.g.anyFront && s.doorStyle != DoorStyle.NONE && s.doorStyle != DoorStyle.SWING) {
                double l = c.g.doorU - c.g.doorWidth / 2 - 0.1, r = c.g.doorU + c.g.doorWidth / 2 + 0.1;
                d.box(c.steelSp, 0xFF8E989F, le, l, y0, 0.05, r, y0 + 0.16, 0.32);
                d.cylinder(c.steelSp, 0xFF3B4045, le, r - 0.15, 0.18, 0.06, y0 + 0.16, y0 + 0.24, 12, true);
            }
            // inspection station (점검 운전반) and stop switch
            d.color(YELLOW, le, w2 - 0.42, y0, 0.05, w2 - 0.15, y0 + 0.2, 0.2);
            d.color(0xD02020, le, w2 - 0.34, y0 + 0.2, 0.09, w2 - 0.26, y0 + 0.24, 0.16);
            d.color(0x202020, le, w2 - 0.22, y0 + 0.2, 0.1, w2 - 0.18, y0 + 0.23, 0.14);
            // fan
            d.cylinder(c.steelSp, 0xFF4A5056, le, -w2 / 2, D * 0.7, 0.16, y0, y0 + 0.08, 16, true);
            // car-top balustrade
            if (s.overhead >= 1.0 && s.carWidth >= 1.4) {
                double rh = 0.7;
                double[][] posts = {{-w2 + 0.05, D - 0.05}, {w2 - 0.05, D - 0.05}, {-w2 + 0.05, 0.4}, {w2 - 0.05, 0.4}};
                for (double[] p : posts) d.color(YELLOW, le, p[0] - 0.02, y0, p[1] - 0.02, p[0] + 0.02, y0 + rh, p[1] + 0.02);
                d.color(YELLOW, le, -w2 + 0.03, y0 + rh - 0.04, D - 0.07, w2 - 0.03, y0 + rh, D - 0.03);
                d.color(YELLOW, le, -w2 + 0.03, y0 + rh - 0.04, 0.38, -w2 + 0.07, y0 + rh, D - 0.03);
                d.color(YELLOW, le, w2 - 0.07, y0 + rh - 0.04, 0.38, w2 - 0.03, y0 + rh, D - 0.03);
                d.color(0x111111, le, -w2 + 0.03, y0 + 0.08, D - 0.07, w2 - 0.03, y0 + 0.12, D - 0.03);
            }
        }
        if (s.drive == DriveType.RACK_PINION && top) {
            // drive unit on the back of the cage: motors and pinions
            double y0 = platform ? H : H + RT;
            d.box(c.steelSp, 0xFFE0B02A, le, -0.35, y0 - 0.6, D + 0.02, 0.35, y0 + 0.35, D + 0.32);
            for (int i = 0; i < 2; i++) {
                d.cylinder(c.steelSp, 0xFF3B6EA8, le, -0.18 + i * 0.36, D + 0.17, 0.12, y0 + 0.35, y0 + 0.62, 16, true);
            }
        }
    }

    private void drawPaternosterFrame(Ctx c) {
        Draw d = c.d;
        double w2 = c.w2, D = c.depth, H = c.height;
        // chain lugs at the back corners
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            d.color(DARK, c.le, sgn * (w2 - 0.08) - 0.05, H * 0.45, D, sgn * (w2 - 0.08) + 0.05, H * 0.55, D + 0.12);
        }
        // floor edge safety flap
        d.color(0xD8B030, c.le, -w2, -FT - 0.02, -0.02, w2, 0.0, 0.03);
    }

    // =================================================================== hoistway equipment

    private void drawShaft(Ctx c, double sNow, Vec3 origin, float rot) {
        CarGeometry g = c.g;
        ElevatorSettings s = c.s;
        Draw d = c.d;
        if (g.paternoster) {
            drawPaternosterShaft(c, origin, rot);
            return;
        }
        Vec3 a0 = g.stopAnchors.get(0);
        if (!g.isVertical()) {
            drawInclinedRails(c, origin);
            return;
        }
        double carY = g.anchorAt(sNow).y - a0.y;
        double minY = -s.pitDepth;
        double topY = g.maxAnchorY() - a0.y + s.carHeight + s.overhead + RT;
        if (g.machinePos != null) topY = Math.max(carY + s.carHeight + 0.5, g.machinePos.getY() - a0.y);
        double range = g.maxAnchorY() - g.minAnchorY();
        double w2 = c.w2, D = c.depth, H = c.height;
        int le = light(c.level, g.toWorld(g.anchorAt(sNow), 0, H / 2, D / 2));
        int lp = light(c.level, a0.add(0, 0.5, 0));

        d.ps.pushPose();
        d.ps.translate(a0.x - origin.x, a0.y - origin.y, a0.z - origin.z);
        d.ps.mulPose(Axis.YP.rotationDegrees(rot));
        double carTop = carY + (g.decks - 1) * g.deckSpacing + H + RT;
        double mid = D / 2;

        // ---- guide rails (T형 레일) with brackets
        boolean rails = s.drive != DriveType.VACUUM;
        if (rails) {
            TextureAtlasSprite railSp = RenderUtil.sprite("block/rail_steel");
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double x = sgn * (w2 + 0.22);
                d.boxF(railSp, WHITE, le, RenderUtil.ALL, 1f, false, x - 0.012, minY, mid - 0.05, x + 0.012, topY, mid + 0.05);
                double fx0 = sgn * (w2 + 0.22 + 0.012), fx1 = sgn * (w2 + 0.22 + 0.04);
                d.boxF(railSp, WHITE, le, RenderUtil.ALL, 1f, false, Math.min(fx0, fx1), minY, mid - 0.08, Math.max(fx0, fx1), topY, mid + 0.08);
                for (double y = minY + 0.5; y < topY; y += 2.5) {
                    double bx0 = sgn * (w2 + 0.26), bx1 = sgn * (w2 + 0.45);
                    d.color(0x5A646C, lp, Math.min(bx0, bx1), y, mid - 0.12, Math.max(bx0, bx1), y + 0.1, mid + 0.12);
                }
            }
        }

        // ---- ropes
        TextureAtlasSprite ropeSp = RenderUtil.sprite("block/wire_rope");
        boolean cw = s.drive.counterweight && s.counterweight != CounterweightSide.NONE;
        double cwH = Math.min(2.0, Math.max(0.8, range * 0.08 + 0.8));
        double cwY = minY + 0.6 + (g.maxAnchorY() - a0.y) - carY;
        double cwX = 0, cwZ = D + 0.3, cwW = Math.min(s.carWidth * 0.6, 1.2), cwD = 0.22;
        boolean cwAlongZ = false;
        if (s.counterweight == CounterweightSide.LEFT) { cwX = -w2 - 0.45; cwZ = mid; cwAlongZ = true; }
        if (s.counterweight == CounterweightSide.RIGHT) { cwX = w2 + 0.45; cwZ = mid; cwAlongZ = true; }
        if (cwAlongZ) cwW = Math.min(D * 0.6, 1.2);
        if (s.drive.ropes) {
            double hitchY = carTop + 0.3;
            int ropes = s.speedMpm >= 240 ? 6 : 4;
            for (int i = 0; i < ropes; i++) {
                double off = (i - (ropes - 1) / 2.0) * 0.045;
                if (s.drive == DriveType.TRACTION_MRL || s.drive == DriveType.HYDRAULIC_ROPED) {
                    for (int sgn = -1; sgn <= 1; sgn += 2) {
                        double x = sgn * w2 * 0.45 + sgn * 0.22;
                        d.beam(ropeSp, WHITE, le, new Vec3(x, hitchY, mid + off), new Vec3(x, topY, mid + off), 0.022, 1f);
                    }
                } else {
                    d.beam(ropeSp, WHITE, le, new Vec3(off, hitchY + 0.1, mid), new Vec3(off, topY, mid), 0.022, 1f);
                }
            }
            if (cw && s.drive != DriveType.HYDRAULIC_ROPED) {
                for (int i = 0; i < ropes; i++) {
                    double off = (i - (ropes - 1) / 2.0) * 0.045;
                    Vec3 p = cwAlongZ ? new Vec3(cwX, 0, cwZ + off) : new Vec3(cwX + off, 0, cwZ);
                    d.beam(ropeSp, WHITE, le, p.add(0, cwY + cwH + 0.1, 0), new Vec3(p.x, topY, p.z), 0.022, 1f);
                }
            }
        }

        // ---- counterweight with its own rails
        if (cw) {
            double hx = cwAlongZ ? cwD / 2 : cwW / 2, hz = cwAlongZ ? cwW / 2 : cwD / 2;
            TextureAtlasSprite cwSp = RenderUtil.sprite("block/counterweight");
            d.box(c.steelSp, 0xFF59636B, le, cwX - hx - 0.03, cwY, cwZ - hz - 0.03, cwX + hx + 0.03, cwY + cwH, cwZ + hz + 0.03);
            d.boxF(cwSp, WHITE, le, RenderUtil.ALL, 0.5f, false, cwX - hx, cwY + 0.06, cwZ - hz - 0.035, cwX + hx, cwY + cwH - 0.06, cwZ + hz + 0.035);
            d.color(0x2A2E33, le, cwX - 0.06, cwY + cwH, cwZ - 0.06, cwX + 0.06, cwY + cwH + 0.12, cwZ + 0.06);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double rx = cwAlongZ ? cwX : cwX + sgn * (hx + 0.06), rz = cwAlongZ ? cwZ + sgn * (hz + 0.06) : cwZ;
                d.color(0x7A848C, lp, rx - 0.015, minY, rz - 0.015, rx + 0.015, topY, rz + 0.015);
            }
            // compensating chain for fast cars
            if (s.speedMpm >= 150) {
                double bottom = Math.min(cwY, carY) - 1.2 - range * 0.02;
                Vec3 cTop = new Vec3(cwX, cwY, cwZ), cB = new Vec3(cwX, bottom, cwZ);
                Vec3 kB = new Vec3(0, bottom, mid), kTop = new Vec3(0, carY - FT - 0.3, mid);
                TextureAtlasSprite chain = RenderUtil.sprite("block/chain_link");
                d.beam(chain, WHITE, le, cTop, cB, 0.04, 0.5f);
                d.beam(chain, WHITE, le, cB, kB, 0.04, 0.5f);
                d.beam(chain, WHITE, le, kB, kTop, 0.04, 0.5f);
            }
            // buffer below counterweight
            if (s.pitDepth >= 0.5) drawSpringBuffer(c, cwX, cwZ, minY, Math.min(0.6, s.pitDepth - 0.2), lp);
        }

        // ---- car buffers in the pit
        if (s.pitDepth >= 0.5) {
            boolean oil = s.speedMpm > 60;
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double bx = sgn * Math.min(w2 * 0.5, 0.6);
                if (oil) drawOilBuffer(c, bx, mid, minY, Math.min(1.0, s.pitDepth - 0.3), lp);
                else drawSpringBuffer(c, bx, mid, minY, Math.min(0.6, s.pitDepth - 0.2), lp);
            }
        }

        // ---- governor rope (조속기 로프) and tension weight
        if (s.drive.ropes || s.drive == DriveType.TRACTION_MR) {
            double gx = w2 + 0.32, gz = 0.25;
            d.beam(ropeSp, 0xFFCFCFCF, le, new Vec3(gx, minY + 0.35, gz), new Vec3(gx, topY, gz), 0.012, 1f);
            d.beam(ropeSp, 0xFFCFCFCF, le, new Vec3(gx + 0.25, minY + 0.35, gz), new Vec3(gx + 0.25, topY, gz), 0.012, 1f);
            d.color(0x3B4045, lp, gx - 0.05, minY + 0.05, gz - 0.12, gx + 0.3, minY + 0.35, gz + 0.12);
            d.color(DARK, le, w2 + 0.1, carY + 0.4, gz - 0.03, gx + 0.02, carY + 0.48, gz + 0.03);
        }

        // ---- traveling cable (이동 케이블): hangs in a loop beside the car, fixed half way up the hoistway
        {
            double midY = (g.maxAnchorY() - g.minAnchorY()) / 2.0 + 0.5;
            double cx = -w2 - 0.12;
            double z1 = D * 0.7, r = 0.22, z2 = z1 - 2 * r;
            double cableLen = range / 2.0 + 2.0;
            double attach = carY - FT - 0.18;
            double bottomLoop = (attach + midY - cableLen) / 2.0;
            bottomLoop = Math.max(minY + 0.3, Math.min(Math.min(attach, midY) - 0.3, bottomLoop));
            TextureAtlasSprite cab = RenderUtil.sprite("block/traveling_cable");
            d.beam(cab, WHITE, le, new Vec3(cx, attach, z1), new Vec3(cx, bottomLoop + r, z1), 0.05, 1f);
            Vec3 prev = new Vec3(cx, bottomLoop + r, z1);
            int seg = 6;
            for (int i = 1; i <= seg; i++) {
                double ang = Math.PI * i / seg;
                Vec3 p = new Vec3(cx, bottomLoop + r - Math.sin(ang) * r, z1 - r + Math.cos(ang) * r);
                d.beam(cab, WHITE, le, prev, p, 0.05, 1f);
                prev = p;
            }
            d.beam(cab, WHITE, le, new Vec3(cx, bottomLoop + r, z2), new Vec3(cx, midY, z2), 0.05, 1f);
            d.color(0x5A646C, le, cx - 0.06, midY, z2 - 0.1, cx + 0.06, midY + 0.22, z2 + 0.1);
        }

        // ---- drive specific
        switch (s.drive) {
            case HYDRAULIC -> {
                double bottom = minY;
                double ramTop = carY - FT - 0.26;
                d.cylinder(RenderUtil.sprite("block/stainless_mirror"), WHITE, le, 0, mid, 0.11, bottom, ramTop, 16, true);
                d.cylinder(c.steelSp, 0xFF3D5A80, lp, 0, mid, 0.17, bottom - 0.01, bottom + 0.35, 16, true);
                d.cylinder(c.steelSp, 0xFF1E2830, lp, 0, mid, 0.19, bottom + 0.3, bottom + 0.38, 16, true);
            }
            case HYDRAULIC_ROPED -> {
                double jx = w2 + 0.42;
                double cylTop = minY + 0.3 + range / 2.0;
                double plungerTop = minY + 0.3 + range / 2.0 + carY / 2.0 + 0.5;
                d.cylinder(c.steelSp, 0xFF3D5A80, lp, jx, mid, 0.15, minY, cylTop, 16, true);
                d.cylinder(RenderUtil.sprite("block/stainless_mirror"), WHITE, le, jx, mid, 0.09, cylTop, plungerTop, 16, true);
                d.ps.pushPose();
                d.ps.translate(jx, plungerTop + 0.2, mid);
                d.ps.mulPose(Axis.YP.rotationDegrees(90));
                RenderUtil.discZ(d.buf, d.ps, RenderUtil.sprite("block/sheave"), WHITE, le, 0.2, -0.04, 0.04, 20);
                d.ps.popPose();
                d.beam(ropeSp, WHITE, le, new Vec3(jx - 0.2, plungerTop + 0.2, mid), new Vec3(w2 * 0.45 + 0.22, carTop + 0.3, mid), 0.022, 1f);
            }
            case RACK_PINION -> {
                double mz = D + 0.55;
                TextureAtlasSprite mast = RenderUtil.sprite("block/mast");
                d.boxF(mast, WHITE, le, RenderUtil.ALL, 1f, false, -0.3, minY, mz - 0.3, 0.3, topY, mz + 0.3);
                TextureAtlasSprite rack = RenderUtil.sprite("block/rack");
                d.boxF(rack, WHITE, le, RenderUtil.ALL, 0.5f, false, -0.06, minY, mz - 0.34, 0.06, topY, mz - 0.3);
            }
            case VACUUM -> {
                double r = Math.min(w2, D / 2) + 0.07;
                d.cylinderGlass(c.glassSp, 0x70FFFFFF, le, 0, mid, r, minY, topY, 32);
                for (double y = Math.ceil(minY); y < topY; y += 1.0) {
                    d.cylinder(c.white, 0xFFE4E8EB, le, 0, mid, r + 0.012, y, y + 0.05, 32, false);
                }
                d.cylinder(c.white, 0xFFE4E8EB, le, 0, mid, r + 0.06, topY, topY + 0.35, 32, true);
                d.cylinder(c.white, 0xFFE4E8EB, le, 0, mid, r + 0.06, minY - 0.1, minY + 0.05, 32, true);
            }
            case SCREW -> {
                double sx = -w2 + 0.15, sz = D + 0.15;
                d.beam(RenderUtil.sprite("block/screw"), WHITE, le, new Vec3(sx, minY, sz), new Vec3(sx, topY, sz), 0.08, 0.25f);
                d.color(0xD0D4D8, le, sx - 0.12, carY + 0.1, sz - 0.12, sx + 0.12, carY + 0.4, sz + 0.12);
                d.color(0x6B7780, le, sx - 0.1, carY + 0.2, sz - 0.1, -w2 + 0.01, carY + 0.3, D);
            }
            default -> { }
        }
        d.ps.popPose();
    }

    private void drawSpringBuffer(Ctx c, double x, double z, double y0, double h, int light) {
        if (h < 0.15) return;
        Draw d = c.d;
        TextureAtlasSprite sp = RenderUtil.sprite("block/spring");
        d.cylinder(c.steelSp, 0xFF40474D, light, x, z, 0.16, y0, y0 + 0.05, 12, true);
        d.cylinder(sp, 0xFFD4A82A, light, x, z, 0.11, y0 + 0.05, y0 + h - 0.04, 12, false);
        d.cylinder(c.steelSp, 0xFF40474D, light, x, z, 0.13, y0 + h - 0.04, y0 + h, 12, true);
    }

    private void drawOilBuffer(Ctx c, double x, double z, double y0, double h, int light) {
        if (h < 0.2) return;
        Draw d = c.d;
        d.cylinder(c.steelSp, 0xFF2F6F4A, light, x, z, 0.12, y0, y0 + h * 0.65, 12, true);
        d.cylinder(RenderUtil.sprite("block/stainless_mirror"), WHITE, light, x, z, 0.06, y0 + h * 0.65, y0 + h - 0.05, 12, false);
        d.cylinder(c.steelSp, 0xFF20252A, light, x, z, 0.1, y0 + h - 0.05, y0 + h, 12, true);
        d.color(0xF2C230, light, x - 0.12, y0 + h * 0.3, z - 0.122, x + 0.12, y0 + h * 0.36, z - 0.12);
    }

    private void drawInclinedRails(Ctx c, Vec3 origin) {
        CarGeometry g = c.g;
        Draw d = c.d;
        TextureAtlasSprite rail = RenderUtil.sprite("block/rail_steel");
        int n = g.stopAnchors.size();
        for (int i = 0; i + 1 < n; i++) {
            Vec3 a = g.stopAnchors.get(i), b = g.stopAnchors.get(i + 1);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double u = sgn * c.w2 * 0.6;
                Vec3 pa = g.toWorld(a, u, -FT - 0.35, c.depth / 2).subtract(origin);
                Vec3 pb = g.toWorld(b, u, -FT - 0.35, c.depth / 2).subtract(origin);
                int light = light(c.level, g.toWorld(a, u, 0, c.depth / 2));
                d.beam(rail, WHITE, light, pa, pb, 0.1, 1f);
                // wire rope along the incline
                Vec3 ra = g.toWorld(a, 0, -FT - 0.2, c.depth / 2).subtract(origin);
                Vec3 rb = g.toWorld(b, 0, -FT - 0.2, c.depth / 2).subtract(origin);
                d.beam(RenderUtil.sprite("block/wire_rope"), WHITE, light, ra, rb, 0.025, 1f);
            }
        }
        // bogie under the current car
        Vec3 now = g.anchorAt(c.car.getS(c.pt));
        d.ps.pushPose();
        d.ps.translate(now.x - origin.x, now.y - origin.y, now.z - origin.z);
        d.ps.mulPose(Axis.YP.rotationDegrees(180f - g.facing.toYRot()));
        d.color(0x4A5056, c.le, -c.w2 * 0.7, -FT - 0.32, 0.2, c.w2 * 0.7, -FT, c.depth - 0.2);
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            for (double z : new double[]{0.35, c.depth - 0.35}) {
                d.cylinder(c.steelSp, 0xFF20252A, c.le, sgn * c.w2 * 0.6, z, 0.09, -FT - 0.45, -FT - 0.3, 12, true);
            }
        }
        d.ps.popPose();
    }

    private void drawPaternosterShaft(Ctx c, Vec3 origin, float rot) {
        CarGeometry g = c.g;
        Draw d = c.d;
        Vec3 base = g.pBase;
        int le = light(c.level, base.add(0, g.pHeight / 2, 0));
        d.ps.pushPose();
        d.ps.translate(base.x - origin.x, base.y - origin.y, base.z - origin.z);
        d.ps.mulPose(Axis.YP.rotationDegrees(rot));
        TextureAtlasSprite chain = RenderUtil.sprite("block/chain_link");
        double D = c.depth;
        double z = D + 0.06;
        double[] xs = {-c.w2 + 0.08, c.w2 - 0.08};
        for (double x0 : xs) {
            // left (up) and right (down) runs of the chain loop
            d.beam(chain, WHITE, le, new Vec3(x0, c.height * 0.5, z), new Vec3(x0, g.pHeight + c.height * 0.5, z), 0.05, 0.5f);
            d.beam(chain, WHITE, le, new Vec3(x0 + g.pCross, c.height * 0.5, z), new Vec3(x0 + g.pCross, g.pHeight + c.height * 0.5, z), 0.05, 0.5f);
        }
        // sprockets
        for (double y : new double[]{c.height * 0.5, g.pHeight + c.height * 0.5}) {
            for (double x0 : xs) {
                d.ps.pushPose();
                d.ps.translate(x0 + g.pCross / 2, y, z);
                d.ps.mulPose(Axis.ZP.rotationDegrees(c.car.spin * 120f));
                RenderUtil.discZ(d.buf, d.ps, RenderUtil.sprite("block/sheave"), WHITE, le, g.pCross / 2, -0.03, 0.03, 24);
                d.ps.popPose();
            }
        }
        d.ps.popPose();
    }
}
