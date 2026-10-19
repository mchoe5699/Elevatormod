package com.qwerty.elevator.elevator;

/** Operating modes of a controller. */
public enum ElevatorMode implements Choice {
    NORMAL("normal"),               // 자동 운전
    INDEPENDENT("independent"),     // 전용 운전
    INSPECTION("inspection"),       // 점검 운전
    FIRE_RECALL("fire_recall"),     // 소방 1차 (귀환)
    FIRE_SERVICE("fire_service"),   // 소방 2차 (소방관 운전)
    OUT_OF_SERVICE("out_of_service"), // 휴지 운전
    STOPPED("stopped");             // 정지 스위치 / 비상정지

    private final String id;
    ElevatorMode(String id) { this.id = id; }
    @Override public String id() { return id; }
    @Override public String category() { return "mode"; }
}
