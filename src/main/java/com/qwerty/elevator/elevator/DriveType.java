package com.qwerty.elevator.elevator;

/** How the car is driven. Decides which shaft equipment is drawn and some limits. */
public enum DriveType implements Choice {
    TRACTION_MR("traction_mr", true, true),        // 로프식, 기계실 있음
    TRACTION_MRL("traction_mrl", true, true),      // 로프식, 기계실 없음
    HYDRAULIC("hydraulic", false, false),          // 유압식 (직접식)
    HYDRAULIC_ROPED("hydraulic_roped", true, false), // 유압식 (간접식, 로프 1:2)
    RACK_PINION("rack_pinion", false, false),      // 랙 앤 피니언 (공사용 호이스트)
    VACUUM("vacuum", false, false),                // 진공식 (공압식)
    CHAIN("chain", false, false),                  // 체인식 (패터노스터)
    SCREW("screw", false, false);                  // 스크류식 (주택용/휠체어 리프트)

    private final String id;
    public final boolean ropes;
    public final boolean counterweight;

    DriveType(String id, boolean ropes, boolean counterweight) {
        this.id = id;
        this.ropes = ropes;
        this.counterweight = counterweight;
    }

    @Override public String id() { return id; }
    @Override public String category() { return "drive"; }
}
