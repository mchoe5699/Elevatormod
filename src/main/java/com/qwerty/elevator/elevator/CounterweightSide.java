package com.qwerty.elevator.elevator;

public enum CounterweightSide implements Choice {
    BACK("back"), LEFT("left"), RIGHT("right"), NONE("none");

    private final String id;
    CounterweightSide(String id) { this.id = id; }
    @Override public String id() { return id; }
    @Override public String category() { return "cw"; }
}
