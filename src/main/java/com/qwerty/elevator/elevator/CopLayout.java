package com.qwerty.elevator.elevator;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout of the car operating panel(s) (카 조작반, COP). Used both for drawing and for click hit-testing.
 * All positions are in car-local coordinates (u, y, d) relative to a cabin anchor.
 */
public final class CopLayout {
    private CopLayout() {}

    public enum ButtonType { FLOOR, OPEN, CLOSE, ALARM, PHONE, HOLD, FIRE }

    public record CopButton(ButtonType type, int floor, double cx, double cy, double size) {
        public boolean contains(double px, double py, double margin) {
            double h = size / 2 + margin;
            return Math.abs(px - cx) <= h && Math.abs(py - cy) <= h;
        }
    }

    /**
     * @param origin bottom-left corner of the plate (u, y, d)
     * @param xAxis  plate "right" direction in local coords (as seen by a person facing the panel)
     * @param normal direction the plate faces (into the car)
     */
    public record Mount(Vec3 origin, Vec3 xAxis, Vec3 normal, double width, double height,
                        double displayY0, double displayY1, List<CopButton> buttons) {
        public Vec3 point(double px, double py, double off) {
            return origin.add(xAxis.scale(px)).add(0, py, 0).add(normal.scale(off));
        }
    }

    public record SpecificationPlate(double cx,double bottom,double width) {}

    /** Reference plaque is narrower than the COP and lines up with the door-header display. */
    public static SpecificationPlate specificationPlate(ElevatorSettings s,CarGeometry g,Mount mount) {
        double width=Math.min(mount.width()*.75,Math.max(.12,g.doorWidth*.11));
        double height=width*.66;
        double headerCenter=(Math.min(s.carHeight,g.doorHeight)+s.carHeight)/2;
        double centerY=Math.min(s.carHeight-.08-height/2,headerCenter+.03);
        return new SpecificationPlate(.02+width/2,centerY-mount.origin().y-height/2,width);
    }

    public static List<Mount> mounts(ElevatorSettings s, CarGeometry g, int deck) {
        List<Mount> out = new ArrayList<>();
        if (!s.riders || g.paternoster) return out;
        List<Integer> floors = g.floorsForDeck(deck);
        boolean wide = s.carWidth >= 2.5;
        CopPosition pos = s.copPosition;
        if (pos == CopPosition.BOTH) {
            add(out, s, g, floors, CopPosition.FRONT_RIGHT, wide);
            add(out, s, g, floors, CopPosition.FRONT_LEFT, wide);
        } else {
            add(out, s, g, floors, pos, wide);
        }
        return out;
    }

    private static void add(List<Mount> out, ElevatorSettings s, CarGeometry g, List<Integer> floors, CopPosition pos, boolean wide) {
        boolean video = s.panelStyle == PanelStyle.VIDEO_CLASSIC;
        boolean lg = s.panelStyle == PanelStyle.LG_MGP;
        double k = video ? Math.min(1.0, Math.max(0.25, (s.carHeight - 0.1) / 2.4))
                : Math.min(1.0, Math.max(0.45, (s.carHeight - 0.1) / 2.2));
        int n = floors.size();
        int cols = lg ? 2 : video ? (n <= 8 ? 2 : 3) : n <= 8 ? 1 : n <= 20 ? 2 : n <= 45 ? 3 : 4;
        int rows = Math.max(1, (n + cols - 1) / cols);
        double gridTop = 1.62 * k, gridBottom = (lg ? .35 : 1.0) * k;
        double pitchY = Math.min(0.105, (gridTop - gridBottom) / rows);
        double pitchX = Math.min(0.11, pitchY * 1.1 + 0.01);
        double btn = Math.min(0.075, pitchY * 0.72);
        double margin = 0.045;
        double width = Math.max(cols * pitchX + 2 * margin, 2 * 0.1 + 2 * margin);
        double bottom = (lg ? .12 : video ? 0.30 : 0.74) * k;
        double top = (lg || video ? 2.35 : 1.86) * k;
        double height = top - bottom;

        double halfW = s.carWidth / 2.0;
        double wall = CarGeometry.WALL;
        double doorL = g.doorU - g.doorWidth / 2.0;
        double doorR = g.doorU + g.doorWidth / 2.0;
        boolean noFrontDoor = s.doorStyle == DoorStyle.NONE && !g.anyFront;
        if (noFrontDoor) { doorL = -0.01; doorR = 0.01; }

        Vec3 origin, xAxis, normal;
        CopPosition actual = pos;
        if (pos == CopPosition.FRONT_RIGHT && (halfW - wall) - (doorR + 0.06) < width) actual = CopPosition.SIDE_RIGHT;
        if (pos == CopPosition.FRONT_LEFT && (doorL - 0.06) - (-halfW + wall) < width) actual = CopPosition.SIDE_LEFT;
        if (s.shape == CarShape.ROUND && (actual == CopPosition.SIDE_LEFT || actual == CopPosition.SIDE_RIGHT)) {
            actual = pos == CopPosition.FRONT_LEFT || pos == CopPosition.SIDE_LEFT ? CopPosition.FRONT_LEFT : CopPosition.FRONT_RIGHT;
        }
        double front = wall + 0.012;
        switch (actual) {
            case FRONT_LEFT -> {
                double right = doorL - 0.07;
                double left = Math.max(-halfW + wall + 0.02, right - width);
                origin = new Vec3(left, bottom, front);
                xAxis = new Vec3(1, 0, 0);
                normal = new Vec3(0, 0, 1);
                width = right - left;
            }
            case SIDE_RIGHT -> {
                double d0 = Math.min(0.2, Math.max(0.05, s.carDepth - width - 0.1));
                origin = new Vec3(halfW - wall - 0.012, bottom, d0);
                xAxis = new Vec3(0, 0, 1);
                normal = new Vec3(-1, 0, 0);
            }
            case SIDE_LEFT -> {
                double d0 = Math.min(0.2, Math.max(0.05, s.carDepth - width - 0.1));
                origin = new Vec3(-halfW + wall + 0.012, bottom, d0 + width);
                xAxis = new Vec3(0, 0, -1);
                normal = new Vec3(1, 0, 0);
            }
            default -> { // FRONT_RIGHT
                double left = doorR + 0.07;
                double right = Math.min(halfW - wall - 0.02, left + width);
                origin = new Vec3(left, bottom, front);
                xAxis = new Vec3(1, 0, 0);
                normal = new Vec3(0, 0, 1);
                width = right - left;
            }
        }
        if (width < 0.12) width = 0.12;

        List<CopButton> buttons = new ArrayList<>();
        double gridW = cols * pitchX;
        double gx0 = (width - gridW) / 2 + pitchX / 2;
        for (int i = 0; i < n; i++) {
            int col = lg ? (i < n/2 ? 0 : 1) : video ? i % cols : i / rows;
            int row = lg ? (i < n/2 ? i : i-n/2) : video ? i / cols : i % rows;
            double cx = gx0 + col * pitchX;
            double cy = (gridBottom - bottom) + (row + 0.5) * pitchY;
            buttons.add(new CopButton(ButtonType.FLOOR, floors.get(i), cx, cy, btn));
        }
        double small = Math.min(0.07, btn);
        double c1 = width * 0.3, c2 = width * 0.7;
        double yOpen = (lg ? .10 : video ? 0.57 : 0.17) * k, yAlarm = (lg ? 1.65 : video ? 0.43 : 0.06) * k;
        buttons.add(new CopButton(ButtonType.OPEN, -1, c1, yOpen, small));
        buttons.add(new CopButton(ButtonType.CLOSE, -1, c2, yOpen, small));
        buttons.add(new CopButton(ButtonType.ALARM, -1, c1, yAlarm, small));
        if (s.fireService) buttons.add(new CopButton(ButtonType.FIRE, -1, c2, yAlarm, small));
        else if (s.type == ElevatorType.FREIGHT || s.type == ElevatorType.BED || s.type == ElevatorType.CAR_LIFT)
            buttons.add(new CopButton(ButtonType.HOLD, -1, c2, yAlarm, small));
        else buttons.add(new CopButton(ButtonType.PHONE, -1, c2, yAlarm, small));

        double dispY0 = lg ? height - .48*k : video ? height - 0.25 * k : (gridTop - bottom) + 0.03 * k;
        double dispY1 = height - 0.03 * k;
        out.add(new Mount(origin, xAxis, normal, width, height, dispY0, dispY1, buttons));
    }

    /** Returns the button hit by a ray given in car-local coordinates, or null. */
    @Nullable
    public static CopButton hit(List<Mount> mounts, Vec3 from, Vec3 dir, double maxDist) {
        CopButton best = null;
        double bestT = maxDist;
        for (Mount m : mounts) {
            double denom = dir.dot(m.normal());
            if (denom >= -1e-6) continue; // must look at the plate's face
            double t = m.origin().subtract(from).dot(m.normal()) / denom;
            if (t < 0 || t > bestT) continue;
            Vec3 p = from.add(dir.scale(t));
            Vec3 rel = p.subtract(m.origin());
            double px = rel.dot(m.xAxis());
            double py = rel.y;
            if (px < -0.02 || px > m.width() + 0.02 || py < -0.02 || py > m.height() + 0.02) continue;
            for (CopButton b : m.buttons()) {
                if (b.contains(px, py, 0.012)) {
                    best = b;
                    bestT = t;
                }
            }
        }
        return best;
    }

    /** True if the ray hits any panel plate at all. */
    public static boolean hitsPlate(List<Mount> mounts, Vec3 from, Vec3 dir, double maxDist) {
        for (Mount m : mounts) {
            double denom = dir.dot(m.normal());
            if (denom >= -1e-6) continue;
            double t = m.origin().subtract(from).dot(m.normal()) / denom;
            if (t < 0 || t > maxDist) continue;
            Vec3 rel = from.add(dir.scale(t)).subtract(m.origin());
            double px = rel.dot(m.xAxis());
            if (px >= 0 && px <= m.width() && rel.y >= 0 && rel.y <= m.height()) return true;
        }
        return false;
    }
}
