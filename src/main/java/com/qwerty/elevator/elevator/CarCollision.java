package com.qwerty.elevator.elevator;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Moves entities together with the car and keeps them inside/outside of its walls.
 * The same code runs on the server (mobs, items, vehicles) and on the client (the local player and
 * whatever the local player is steering), because players move client-side.
 */
public final class CarCollision {
    private CarCollision() {}

    public enum Result { NONE, INSIDE, ROOF, PUSHED }

    /** Door openings of one cabin, as fractions 0..1. */
    public record Doors(float front, float rear) {}

    public static Result apply(Entity e, CarGeometry g, ElevatorSettings s,
                               List<CarGeometry.Cabin> prev, List<CarGeometry.Cabin> now, Doors[] doors) {
        if (e.isPassenger() || e.noPhysics || e.isSpectator()) return Result.NONE;
        int n = Math.min(prev.size(), now.size());
        for (int i = 0; i < n; i++) {
            CarGeometry.Cabin cp = prev.get(i);
            CarGeometry.Cabin cn = now.get(i);
            Doors d = doors.length == 0 ? new Doors(1, 1) : doors[Math.min(cp.deck(), doors.length - 1)];
            Result r = applyCabin(e, g, s, cp.anchor(), cn.anchor(), d);
            if (r != Result.NONE) return r;
        }
        return Result.NONE;
    }

    private static Result applyCabin(Entity e, CarGeometry g, ElevatorSettings s, Vec3 aPrev, Vec3 aNow, Doors doors) {
        Vec3 pos = e.position();
        Vec3 l = g.toLocal(aPrev, pos);
        double u = l.x, y = l.y, d = l.z;
        double hw = e.getBbWidth() / 2.0;
        double h = e.getBbHeight();
        double w2 = s.carWidth / 2.0;
        double depth = s.carDepth;
        double height = s.carHeight;
        double wall = CarGeometry.WALL;

        // quick reject
        if (u < -w2 - hw - 0.1 || u > w2 + hw + 0.1 || d < -hw - 0.1 || d > depth + hw + 0.1) return Result.NONE;
        if (y > height + CarGeometry.ROOF_T + 1.0 || y + h < -CarGeometry.FLOOR_T - 0.8) return Result.NONE;

        boolean centerInside = u > -w2 && u < w2 && d > 0 && d < depth;
        if (s.shape == CarShape.ROUND && centerInside) {
            double r = Math.min(w2, depth / 2);
            double cu = u, cd = d - depth / 2;
            if (cu * cu + cd * cd > r * r) centerInside = false;
        }
        Vec3 vel = e.getDeltaMovement();

        // ----- inside the cabin
        if (centerInside && y > -0.8 && y < height - 0.3) {
            boolean grounded = false;
            if (y < 0.3 && vel.y <= 0.25) {
                y = 0;
                grounded = true;
            }
            if (s.ceiling && y + h > height && height - h >= 0) y = height - h;

            boolean clampU = false, clampD = false;
            boolean frontOpen = doorPassable(s.doorStyle, doors.front()) && g.anyFront;
            boolean inFrontOpening = Math.abs(u - g.doorU) <= g.doorWidth / 2.0 - hw + 0.05;
            boolean rearOpen = g.anyRear && doorPassable(s.doorStyle, doors.rear());
            boolean inRearOpening = Math.abs(u - g.rearDoorU) <= g.rearDoorWidth / 2.0 - hw + 0.05;
            boolean noFrontWall = s.doorStyle == DoorStyle.NONE;

            if (s.shape == CarShape.ROUND) {
                double r = Math.min(w2, depth / 2) - wall - hw;
                double cd = d - depth / 2;
                double dist = Math.sqrt(u * u + cd * cd);
                boolean exiting = (frontOpen || noFrontWall) && inFrontOpening && cd < 0;
                if (dist > r && r > 0 && !exiting) {
                    u = u / dist * r;
                    d = depth / 2 + cd / dist * r;
                    clampU = clampD = true;
                }
            } else {
                double minU = -w2 + wall + hw, maxU = w2 - wall - hw;
                if (s.wallLeft.blocks && u < minU) { u = minU; clampU = true; }
                if (s.wallRight.blocks && u > maxU) { u = maxU; clampU = true; }
                double minD = wall + hw, maxD = depth - wall - hw;
                boolean frontPass = (frontOpen || noFrontWall) && inFrontOpening;
                if (!frontPass && d < minD) { d = minD; clampD = true; }
                boolean backBlocks = s.wallBack.blocks || g.anyRear;
                boolean rearPass = rearOpen && inRearOpening;
                if (backBlocks && !rearPass && d > maxD) { d = maxD; clampD = true; }
            }
            moveTo(e, g, aNow, u, y, d, grounded, clampU, clampD);
            return Result.INSIDE;
        }

        // ----- standing on the car roof (점검 운전 등)
        double roof = height + CarGeometry.ROOF_T + 0.07;
        if (centerInside && s.ceiling && y >= roof - 0.6 && y < roof + 0.35 && vel.y <= 0.25) {
            moveTo(e, g, aNow, u, roof, d, true, false, false);
            return Result.ROOF;
        }

        // ----- outside: don't walk through the walls
        if (y + h > -CarGeometry.FLOOR_T + 0.05 && y < height + CarGeometry.ROOF_T && y > -0.5) {
            boolean overlapping = u > -w2 - hw && u < w2 + hw && d > -hw && d < depth + hw;
            if (!overlapping) return Result.NONE;
            boolean frontOpen = (doorPassable(s.doorStyle, doors.front()) && g.anyFront) || s.doorStyle == DoorStyle.NONE;
            if (frontOpen && d < 0.6 && Math.abs(u - g.doorU) <= g.doorWidth / 2.0 - hw + 0.05) return Result.NONE;
            boolean rearOpen = g.anyRear && doorPassable(s.doorStyle, doors.rear());
            if (rearOpen && d > depth - 0.6 && Math.abs(u - g.rearDoorU) <= g.rearDoorWidth / 2.0 - hw + 0.05) return Result.NONE;
            if (s.wallLeft == WallType.NONE && u < 0) return Result.NONE;
            if (s.wallRight == WallType.NONE && u > 0) return Result.NONE;
            double pl = (u + w2 + hw), pr = (w2 + hw - u), pf = (d + hw), pb = (depth + hw - d);
            double m = Math.min(Math.min(pl, pr), Math.min(pf, pb));
            boolean cu = false, cd = false;
            if (m == pl) { u = -w2 - hw - 0.001; cu = true; }
            else if (m == pr) { u = w2 + hw + 0.001; cu = true; }
            else if (m == pf) { d = -hw - 0.001; cd = true; }
            else { d = depth + hw + 0.001; cd = true; }
            moveTo(e, g, aNow, u, y, d, false, cu, cd);
            return Result.PUSHED;
        }
        return Result.NONE;
    }

    public static boolean doorPassable(DoorStyle style, float f) {
        if (style == DoorStyle.NONE) return true;
        return f > 0.9f;
    }

    private static void moveTo(Entity e, CarGeometry g, Vec3 anchor, double u, double y, double d,
                               boolean grounded, boolean clampU, boolean clampD) {
        Vec3 world = g.toWorld(anchor, u, y, d);
        Vec3 vel = e.getDeltaMovement();
        if (clampU || clampD) {
            Vec3 lv = g.dirToLocal(vel);
            double lu = clampU ? 0 : lv.x;
            double ld = clampD ? 0 : lv.z;
            Vec3 r = g.right(), b = g.back();
            vel = new Vec3(r.x * lu + b.x * ld, vel.y, r.z * lu + b.z * ld);
        }
        if (grounded) {
            vel = new Vec3(vel.x, Math.max(0, vel.y), vel.z);
            e.setOnGround(true);
            e.fallDistance = 0;
        }
        e.setDeltaMovement(vel);
        e.setPos(world.x, world.y, world.z);
    }
}
