package com.qwerty.elevator.elevator;

/** Colour of a lit call button. */
public enum ButtonLight implements Choice {
    ORANGE("orange", 0xFF9A2A),
    BLUE("blue", 0x3FA8FF),
    WHITE("white", 0xF4F8FF),
    RED("red", 0xFF3030),
    GREEN("green", 0x47FF6A);

    private final String id;
    public final int color;

    ButtonLight(String id, int color) { this.id = id; this.color = color; }

    @Override public String id() { return id; }
    @Override public String category() { return "light"; }
}
