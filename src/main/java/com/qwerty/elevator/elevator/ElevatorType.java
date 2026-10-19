package com.qwerty.elevator.elevator;

/**
 * Elevator kinds (용도/구조별 분류). Selecting one applies sensible defaults to the settings;
 * everything stays editable afterwards.
 */
public enum ElevatorType implements Choice {
    PASSENGER("passenger"),       // 승객용
    MRL("mrl"),                   // 기계실 없는 엘리베이터 (MRL)
    HYDRAULIC("hydraulic"),       // 유압식
    PANORAMIC("panoramic"),       // 전망용
    FREIGHT("freight"),           // 화물용
    CAR_LIFT("car_lift"),         // 자동차용
    BED("bed"),                   // 침대용 (병원용)
    EMERGENCY("emergency"),       // 비상용
    EVACUATION("evacuation"),     // 피난용
    HOME("home"),                 // 주택용 (홈 엘리베이터)
    DUMBWAITER("dumbwaiter"),     // 소형화물용 (덤웨이터)
    INCLINED("inclined"),         // 경사형
    DOUBLE_DECK("double_deck"),   // 더블데크
    HIGH_SPEED("high_speed"),     // 초고속
    PATERNOSTER("paternoster"),   // 패터노스터 (순환식)
    VACUUM("vacuum"),             // 진공식 (공압식)
    CONSTRUCTION("construction"), // 공사용 호이스트 (랙앤피니언)
    PLATFORM_LIFT("platform_lift"); // 수직형 휠체어 리프트

    private final String id;

    ElevatorType(String id) { this.id = id; }

    @Override public String id() { return id; }
    @Override public String category() { return "type"; }

    public void applyDefaults(ElevatorSettings s) {
        ElevatorSettings d = new ElevatorSettings(); // fresh defaults
        String keepName = s.name;
        int keepMain = s.mainFloor, keepPark = s.parkingFloor, keepFire = s.fireFloor, keepLowest = s.lowestFloorNumber;
        s.load(d.save());
        s.name = keepName;
        s.mainFloor = keepMain;
        s.parkingFloor = keepPark;
        s.fireFloor = keepFire;
        s.lowestFloorNumber = keepLowest;
        s.type = this;
        switch (this) {
            case PASSENGER -> {
                s.drive = DriveType.TRACTION_MR;
                s.speedMpm = 105;
            }
            case MRL -> {
                s.drive = DriveType.TRACTION_MRL;
                s.speedMpm = 90;
                s.counterweight = CounterweightSide.LEFT;
                s.matWall = "qwertys_elevator:stainless_etching";
            }
            case HYDRAULIC -> {
                s.drive = DriveType.HYDRAULIC;
                s.speedMpm = 45;
                s.accel = 0.4f;
                s.counterweight = CounterweightSide.NONE;
                s.doorStyle = DoorStyle.SIDE_2;
            }
            case PANORAMIC -> {
                s.drive = DriveType.TRACTION_MR;
                s.speedMpm = 105;
                s.wallBack = WallType.GLASS;
                s.wallLeft = WallType.GLASS;
                s.wallRight = WallType.GLASS;
                s.shape = CarShape.BOX_ROUND_BACK;
                s.counterweight = CounterweightSide.LEFT;
                s.mirror = false;
                s.matTrim = "qwertys_elevator:gold_mirror";
                s.panelStyle = PanelStyle.GOLD;
            }
            case FREIGHT -> {
                s.drive = DriveType.TRACTION_MR;
                s.carWidth = 4f; s.carDepth = 4f; s.carHeight = 4f;
                s.speedMpm = 45;
                s.doorStyle = DoorStyle.VERTICAL;
                s.matWall = "qwertys_elevator:painted_steel";
                s.matFloor = "qwertys_elevator:checker_plate";
                s.matCeiling = "qwertys_elevator:painted_steel";
                s.matDoor = "qwertys_elevator:painted_steel";
                s.matTrim = "qwertys_elevator:checker_plate";
                s.handrail = false; s.mirror = false; s.cctv = false;
                s.panelStyle = PanelStyle.STAINLESS;
                s.indicatorStyle = IndicatorStyle.GREEN_DOT;
                s.doorOpenSec = 10f;
                s.doorMoveSec = 4f;
            }
            case CAR_LIFT -> {
                s.drive = DriveType.HYDRAULIC_ROPED;
                s.carWidth = 4f; s.carDepth = 7f; s.carHeight = 3f;
                s.speedMpm = 30;
                s.doorStyle = DoorStyle.VERTICAL;
                s.counterweight = CounterweightSide.NONE;
                s.matWall = "qwertys_elevator:painted_steel";
                s.matFloor = "qwertys_elevator:checker_plate";
                s.matCeiling = "qwertys_elevator:painted_steel";
                s.matDoor = "qwertys_elevator:painted_steel";
                s.matTrim = "qwertys_elevator:checker_plate";
                s.handrail = false; s.mirror = false;
                s.carriesVehicles = true;
                s.doorOpenSec = 15f;
                s.doorMoveSec = 5f;
            }
            case BED -> {
                s.drive = DriveType.TRACTION_MR;
                s.carWidth = 2.5f; s.carDepth = 4f; s.carHeight = 3f;
                s.speedMpm = 60;
                s.doorStyle = DoorStyle.SIDE_3;
                s.doorOpenSec = 8f;
                s.matWall = "qwertys_elevator:white_panel";
                s.matFloor = "qwertys_elevator:rubber_floor";
                s.lightColor = 0xF4FAFF;
                s.copPosition = CopPosition.BOTH;
            }
            case EMERGENCY -> {
                s.drive = DriveType.TRACTION_MR;
                s.speedMpm = 120;
                s.fireService = true;
                s.panelStyle = PanelStyle.STAINLESS;
                s.buttonLight = ButtonLight.RED;
                s.carDepth = 3f;
            }
            case EVACUATION -> {
                s.drive = DriveType.TRACTION_MR;
                s.speedMpm = 120;
                s.fireService = true;
                s.carWidth = 3.5f; s.carDepth = 3f;
                s.buttonLight = ButtonLight.GREEN;
                s.indicatorStyle = IndicatorStyle.GREEN_DOT;
            }
            case HOME -> {
                s.drive = DriveType.SCREW;
                s.carWidth = 1.75f; s.carDepth = 1.75f; s.carHeight = 2.5f;
                s.speedMpm = 15;
                s.accel = 0.3f;
                s.doorStyle = DoorStyle.FOLDING_GATE;
                s.counterweight = CounterweightSide.NONE;
                s.matWall = "qwertys_elevator:wood_panel_oak";
                s.matFloor = "qwertys_elevator:wood_panel_walnut";
                s.matCeiling = "qwertys_elevator:white_panel";
                s.panelStyle = PanelStyle.WOOD;
                s.indicatorStyle = IndicatorStyle.WHITE_LCD;
                s.buttonLight = ButtonLight.WHITE;
                s.cctv = false;
                s.pitDepth = 0.25f;
            }
            case DUMBWAITER -> {
                s.drive = DriveType.TRACTION_MR;
                s.carWidth = 1f; s.carDepth = 1f; s.carHeight = 1f;
                s.speedMpm = 30;
                s.doorStyle = DoorStyle.VERTICAL;
                s.matWall = "qwertys_elevator:stainless_hairline";
                s.matFloor = "qwertys_elevator:stainless_hairline";
                s.capacityKg = 100;
                s.capacityPersons = 0;
                s.riders = false;
                s.storage = true;
                s.handrail = false; s.mirror = false; s.cctv = false;
                s.counterweight = CounterweightSide.NONE;
                s.pitDepth = 0.25f;
                s.overhead = 0.5f;
            }
            case INCLINED -> {
                s.drive = DriveType.TRACTION_MR;
                s.speedMpm = 45;
                s.wallBack = WallType.GLASS;
                s.wallLeft = WallType.GLASS;
                s.wallRight = WallType.GLASS;
                s.counterweight = CounterweightSide.NONE;
                s.mirror = false;
            }
            case DOUBLE_DECK -> {
                s.drive = DriveType.TRACTION_MR;
                s.speedMpm = 360;
                s.accel = 1.0f;
                s.decks = 2;
                s.counterweight = CounterweightSide.BACK;
            }
            case HIGH_SPEED -> {
                s.drive = DriveType.TRACTION_MR;
                s.speedMpm = 600;
                s.accel = 1.0f;
                s.matWall = "qwertys_elevator:black_mirror";
                s.matTrim = "qwertys_elevator:gold_mirror";
                s.panelStyle = PanelStyle.BLACK_GLASS;
                s.indicatorStyle = IndicatorStyle.BLACK_LCD;
                s.buttonLight = ButtonLight.BLUE;
                s.overhead = 3f;
                s.pitDepth = 3f;
            }
            case PATERNOSTER -> {
                s.drive = DriveType.CHAIN;
                s.carWidth = 1.75f; s.carDepth = 1.5f; s.carHeight = 2.5f;
                s.speedMpm = 15;
                s.doorStyle = DoorStyle.NONE;
                s.matWall = "qwertys_elevator:wood_panel_walnut";
                s.matFloor = "qwertys_elevator:wood_panel_oak";
                s.matCeiling = "qwertys_elevator:wood_panel_walnut";
                s.counterweight = CounterweightSide.NONE;
                s.mirror = false; s.cctv = false; s.handrail = false;
                s.paternosterCabins = 8;
            }
            case VACUUM -> {
                s.drive = DriveType.VACUUM;
                s.carWidth = 1.5f; s.carDepth = 1.5f; s.carHeight = 2.5f;
                s.shape = CarShape.ROUND;
                s.speedMpm = 9;
                s.accel = 0.3f;
                s.doorStyle = DoorStyle.SINGLE_SLIDE;
                s.wallBack = WallType.GLASS; s.wallLeft = WallType.GLASS; s.wallRight = WallType.GLASS;
                s.counterweight = CounterweightSide.NONE;
                s.matFloor = "qwertys_elevator:stainless_mirror";
                s.mirror = false; s.cctv = false;
                s.pitDepth = 0f;
                s.panelStyle = PanelStyle.WHITE;
                s.indicatorStyle = IndicatorStyle.WHITE_LCD;
            }
            case CONSTRUCTION -> {
                s.drive = DriveType.RACK_PINION;
                s.carWidth = 2f; s.carDepth = 3f; s.carHeight = 2.5f;
                s.speedMpm = 40;
                s.doorStyle = DoorStyle.VERTICAL;
                s.wallBack = WallType.MESH; s.wallLeft = WallType.MESH; s.wallRight = WallType.MESH;
                s.matWall = "qwertys_elevator:painted_steel";
                s.matFloor = "qwertys_elevator:checker_plate";
                s.matCeiling = "qwertys_elevator:painted_steel";
                s.matDoor = "qwertys_elevator:mesh_panel";
                s.matTrim = "qwertys_elevator:hazard_stripe";
                s.counterweight = CounterweightSide.NONE;
                s.handrail = false; s.mirror = false; s.cctv = false;
                s.panelStyle = PanelStyle.STAINLESS;
                s.indicatorStyle = IndicatorStyle.GREEN_DOT;
            }
            case PLATFORM_LIFT -> {
                s.drive = DriveType.SCREW;
                s.carWidth = 1.5f; s.carDepth = 1.75f; s.carHeight = 1.25f;
                s.speedMpm = 9;
                s.accel = 0.3f;
                s.doorStyle = DoorStyle.SWING;
                s.wallBack = WallType.RAILING; s.wallLeft = WallType.HALF_GLASS; s.wallRight = WallType.HALF_GLASS;
                s.ceiling = false;
                s.matWall = "qwertys_elevator:white_panel";
                s.matFloor = "qwertys_elevator:rubber_floor";
                s.counterweight = CounterweightSide.NONE;
                s.mirror = false; s.cctv = false; s.handrail = true;
                s.capacityKg = 300;
                s.capacityPersons = 2;
                s.pitDepth = 0.1f;
                s.panelStyle = PanelStyle.WHITE;
            }
        }
        if (this != DUMBWAITER && this != PLATFORM_LIFT) s.autoCapacity();
        s.clamp();
    }
}
