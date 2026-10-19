package com.qwerty.elevator.elevator;

/** Colour / display technology for floor indicators. */
public enum IndicatorStyle implements Choice {
    RED_DOT("red_dot", 0xFF2A1A, 0x2A0806, true),
    ORANGE_DOT("orange_dot", 0xFF9A1A, 0x2A1806, true),
    GREEN_DOT("green_dot", 0x3CFF4A, 0x062A0A, true),
    BLUE_LCD("blue_lcd", 0xFFFFFF, 0x1D4FA8, false),
    WHITE_LCD("white_lcd", 0x111111, 0xD8E4E8, false),
    BLACK_LCD("black_lcd", 0x8CE6FF, 0x050608, false);

    private final String id;
    public final int fg;
    public final int bg;
    public final boolean dotMatrix;

    IndicatorStyle(String id, int fg, int bg, boolean dotMatrix) {
        this.id = id; this.fg = fg; this.bg = bg; this.dotMatrix = dotMatrix;
    }

    @Override public String id() { return id; }
    @Override public String category() { return "indicator"; }
}
