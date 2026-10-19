package com.qwerty.elevator.elevator;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes door panel rectangles for an opening of width w (x from -w/2 to w/2) and height h (y from 0 to h)
 * at open fraction f (0 = closed, 1 = open). Shared by car doors and landing doors.
 */
public final class DoorPanels {
    private DoorPanels() {}

    /**
     * @param layer depth layer (0 = closest to the opening plane); renderers offset each layer slightly
     * @param angle swing angle in degrees around a vertical hinge at x = hinge (0 for sliding panels)
     * @param bar   true for thin gate bars (folding gate)
     */
    public record Panel(double x0, double x1, double y0, double y1, int layer, double angle, double hinge, boolean bar) {
        static Panel slide(double x0, double x1, double y0, double y1, int layer) {
            return new Panel(x0, x1, y0, y1, layer, 0, 0, false);
        }
    }

    public static List<Panel> panels(DoorStyle style, double w, double h, double f, boolean car) {
        List<Panel> out = new ArrayList<>();
        double hw = w / 2;
        f = Math.max(0, Math.min(1, f));
        switch (style) {
            case CENTER_2 -> {
                double s = f * hw;
                out.add(Panel.slide(-hw - s, -s, 0, h, 0));
                out.add(Panel.slide(s, hw + s, 0, h, 0));
            }
            case CENTER_4 -> {
                double q = w / 4;
                out.add(Panel.slide(-q - f * hw, -f * hw, 0, h, 1));
                out.add(Panel.slide(-hw - f * q, -q - f * q, 0, h, 0));
                out.add(Panel.slide(f * hw, q + f * hw, 0, h, 1));
                out.add(Panel.slide(q + f * q, hw + f * q, 0, h, 0));
            }
            case SIDE_2 -> {
                out.add(Panel.slide(-hw - f * hw, -f * hw, 0, h, 0));
                out.add(Panel.slide(-f * w, hw - f * w, 0, h, 1));
            }
            case SIDE_3 -> {
                double t = w / 3;
                out.add(Panel.slide(-hw - f * t, -hw + t - f * t, 0, h, 0));
                out.add(Panel.slide(-hw + t - f * 2 * t, -hw + 2 * t - f * 2 * t, 0, h, 1));
                out.add(Panel.slide(-hw + 2 * t - f * w, hw - f * w, 0, h, 2));
            }
            case SINGLE_SLIDE -> out.add(Panel.slide(-hw - f * w, hw - f * w, 0, h, 0));
            case VERTICAL -> {
                if (car) {
                    // car gate: one panel rising into the car top
                    double lift = f * (h - 0.05);
                    out.add(Panel.slide(-hw, hw, lift, h + lift, 0));
                } else {
                    double hh = h / 2;
                    out.add(Panel.slide(-hw, hw, -f * hh, hh - f * hh, 0));
                    out.add(Panel.slide(-hw, hw, hh + f * hh, h + f * hh, 1));
                }
            }
            case SWING -> out.add(new Panel(-hw, hw, 0, Math.min(h, car ? 1.1 : h), 0, f * 90.0, -hw, false));
            case FOLDING_GATE -> {
                int n = Math.max(4, (int) Math.round(w * 6)) + 1;
                double pitch = w / (n - 1) * (1 - 0.88 * f);
                double gateH = car ? Math.min(h, h - 0.1) : h;
                for (int i = 0; i < n; i++) {
                    double x = -hw + i * pitch;
                    out.add(new Panel(x - 0.02, x + 0.02, 0.02, gateH, 0, 0, 0, true));
                }
            }
            case NONE -> { }
        }
        return out;
    }

    /** How far beyond the opening the panels reach when fully open (for wall pockets). */
    public static double pocket(DoorStyle style, double w) {
        return switch (style) {
            case CENTER_2, CENTER_4 -> w / 2;
            case SIDE_2, SIDE_3, SINGLE_SLIDE -> w;
            default -> 0;
        };
    }
}
