package com.qwerty.elevator.elevator;

/** Per-fixture floor transition, independent of rendering and camera frame rate. Times are game ticks. */
public final class VideoDisplayState {
    public static final float FLOOR_ROLL_TICKS = 10;
    public static final float ARROW_CYCLE_TICKS = 16;
    private String current = "", previous = "";
    private float changedAt = -100;
    private int direction;

    public void update(String floor, int dir, float time) {
        if (floor == null) floor = "";
        if (!floor.equals(current)) {
            previous = current;
            current = floor;
            changedAt = time;
            direction = Integer.signum(dir);
        }
    }

    public String current() { return current; }
    public String previous() { return previous; }
    public float progress(float time) {
        return previous.isEmpty() || direction == 0 ? 1 : Math.max(0, Math.min(1, (time - changedAt) / FLOOR_ROLL_TICKS));
    }
    public float outgoingRows(float time) { return 8 * direction * progress(time); }
    public float incomingRows(float time) { return -8 * direction * (1 - progress(time)); }

    /** Tip enters from the travel direction, then the complete arrow holds before the next sweep. */
    public static float arrowRows(float time, int dir, boolean moving) {
        if (!moving || dir == 0) return 0;
        float phase = Math.floorMod((int) Math.floor(time), (int) ARROW_CYCLE_TICKS) + time - (float) Math.floor(time);
        return -Integer.signum(dir) * 8 * Math.max(0, 1 - phase / 6);
    }
}
