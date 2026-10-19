package com.qwerty.elevator.elevator;

public enum WallType implements Choice {
    SOLID("solid", true),
    GLASS("glass", true),
    HALF_GLASS("half_glass", true),
    MESH("mesh", true),
    RAILING("railing", true),
    NONE("none", false);

    private final String id;
    public final boolean blocks;

    WallType(String id, boolean blocks) { this.id = id; this.blocks = blocks; }

    @Override public String id() { return id; }
    @Override public String category() { return "wall"; }
}
