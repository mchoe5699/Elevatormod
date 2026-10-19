package com.qwerty.elevator.elevator;

/** Where the car operating panel is mounted. */
public enum CopPosition implements Choice {
    FRONT_RIGHT("front_right"),
    FRONT_LEFT("front_left"),
    SIDE_RIGHT("side_right"),
    SIDE_LEFT("side_left"),
    BOTH("both");

    private final String id;
    CopPosition(String id) { this.id = id; }
    @Override public String id() { return id; }
    @Override public String category() { return "cop"; }
}
