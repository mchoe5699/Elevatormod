package com.qwerty.elevator.elevator;

/**
 * Door operating styles. Panel layout and motion are computed in {@link DoorPanels}.
 */
public enum DoorStyle implements Choice {
    CENTER_2("center_2"),       // 2매 중앙개폐 (CO)
    CENTER_4("center_4"),       // 4매 중앙개폐 (2CO)
    SIDE_2("side_2"),           // 2매 측면개폐 (2S)
    SIDE_3("side_3"),           // 3매 측면개폐 (3S)
    SINGLE_SLIDE("single"),     // 1매 편개
    VERTICAL("vertical"),       // 상하개폐 (화물용)
    SWING("swing"),             // 여닫이 (주택용 승강장 문)
    FOLDING_GATE("gate"),       // 수동 접이식 게이트
    NONE("none");               // 문 없음 (패터노스터 등)

    private final String id;

    DoorStyle(String id) { this.id = id; }

    @Override public String id() { return id; }
    @Override public String category() { return "door"; }
}
