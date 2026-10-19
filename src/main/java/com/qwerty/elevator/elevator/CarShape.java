package com.qwerty.elevator.elevator;

public enum CarShape implements Choice {
    BOX("box"),
    ROUND("round"),
    BOX_ROUND_BACK("round_back");   // 전망용 반원형 뒷면

    private final String id;
    CarShape(String id) { this.id = id; }
    @Override public String id() { return id; }
    @Override public String category() { return "shape"; }
}
