package com.qwerty.elevator.elevator;

/** Car operating panel (COP) / hall fixture faceplate style. */
public enum PanelStyle implements Choice {
    LG_MGP("lg_mgp", 0xA9ACAB, 0xD7DAD8),
    VIDEO_CLASSIC("video_classic", 0xBCC1BF, 0xECEEEC),
    STAINLESS("stainless", 0xC9CED3, 0xE8ECEF),
    BLACK_GLASS("black_glass", 0x15171A, 0x2A2E33),
    GOLD("gold", 0xC9A64A, 0xE6CB7A),
    WHITE("white", 0xEDEDED, 0xFFFFFF),
    WOOD("wood", 0x7A5232, 0x9B6A42);

    private final String id;
    public final int plate;
    public final int highlight;

    PanelStyle(String id, int plate, int highlight) { this.id = id; this.plate = plate; this.highlight = highlight; }

    @Override public String id() { return id; }
    @Override public String category() { return "panel"; }
}
