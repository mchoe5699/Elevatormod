package com.qwerty.elevator.elevator;

/** One-click interior design combinations (카 디자인 프리셋). Everything stays editable afterwards. */
public enum DesignPreset implements Choice {
    LG_MGP("lg_mgp", "stainless_hairline", "marble_tile", "stainless_hairline", "stainless_hairline", "stainless_hairline", PanelStyle.LG_MGP, IndicatorStyle.LG_SEGMENT, ButtonLight.RED, 0xF4F5EF),
    VIDEO_CLASSIC("video_classic", "white_panel", "marble_tile", "white_panel", "stainless_hairline", "stainless_hairline", PanelStyle.VIDEO_CLASSIC, IndicatorStyle.VIDEO_AMBER, ButtonLight.RED, 0xFFF5D9),
    APARTMENT("apartment", "stainless_etching", "terrazzo", "stainless_mirror", "stainless_hairline", "stainless_mirror", PanelStyle.STAINLESS, IndicatorStyle.RED_DOT, ButtonLight.ORANGE, 0xFFF4E0),
    MODERN("modern", "stainless_hairline", "granite_tile", "stainless_mirror", "stainless_hairline", "stainless_mirror", PanelStyle.STAINLESS, IndicatorStyle.BLUE_LCD, ButtonLight.BLUE, 0xF4FAFF),
    LUXURY("luxury", "black_mirror", "marble_tile", "gold_mirror", "gold_mirror", "gold_mirror", PanelStyle.GOLD, IndicatorStyle.BLACK_LCD, ButtonLight.WHITE, 0xFFD9A0),
    HOTEL("hotel", "wood_panel_walnut", "marble_tile", "stainless_mirror", "stainless_mirror", "gold_mirror", PanelStyle.BLACK_GLASS, IndicatorStyle.ORANGE_DOT, ButtonLight.WHITE, 0xFFD9A0),
    WOOD("wood", "wood_panel_oak", "wood_panel_walnut", "white_panel", "stainless_hairline", "wood_panel_walnut", PanelStyle.WOOD, IndicatorStyle.WHITE_LCD, ButtonLight.WHITE, 0xFFF4E0),
    HOSPITAL("hospital", "white_panel", "rubber_floor", "white_panel", "stainless_hairline", "stainless_hairline", PanelStyle.WHITE, IndicatorStyle.GREEN_DOT, ButtonLight.GREEN, 0xF4FAFF),
    INDUSTRIAL("industrial", "painted_steel", "checker_plate", "painted_steel", "painted_steel", "hazard_stripe", PanelStyle.STAINLESS, IndicatorStyle.GREEN_DOT, ButtonLight.ORANGE, 0xFFFFFF),
    BLACK("black", "black_mirror", "terrazzo", "black_mirror", "black_mirror", "stainless_mirror", PanelStyle.BLACK_GLASS, IndicatorStyle.BLACK_LCD, ButtonLight.BLUE, 0xBFD8FF),
    GLASS("glass", "car_glass", "granite_tile", "stainless_mirror", "car_glass", "stainless_mirror", PanelStyle.STAINLESS, IndicatorStyle.WHITE_LCD, ButtonLight.WHITE, 0xFFFFFF);

    private final String id;
    private final String wall, floor, ceiling, door, trim;
    private final PanelStyle panel;
    private final IndicatorStyle indicator;
    private final ButtonLight light;
    private final int lightColor;

    DesignPreset(String id, String wall, String floor, String ceiling, String door, String trim,
                 PanelStyle panel, IndicatorStyle indicator, ButtonLight light, int lightColor) {
        this.id = id;
        this.wall = wall;
        this.floor = floor;
        this.ceiling = ceiling;
        this.door = door;
        this.trim = trim;
        this.panel = panel;
        this.indicator = indicator;
        this.light = light;
        this.lightColor = lightColor;
    }

    @Override public String id() { return id; }
    @Override public String category() { return "design"; }

    public void apply(ElevatorSettings s) {
        s.lgInterior = this == LG_MGP;
        s.videoInterior = this == VIDEO_CLASSIC;
        if (s.lgInterior) {
            s.shape = CarShape.BOX;
            s.wallBack = s.wallLeft = s.wallRight = WallType.SOLID;
            s.ceiling = s.handrail = true;
            s.mirror = s.cctv = false;
            s.copPosition = CopPosition.FRONT_RIGHT;
            s.doorStyle = DoorStyle.CENTER_2;
        }
        if (s.videoInterior) {
            s.shape = CarShape.BOX;
            s.wallBack = s.wallLeft = s.wallRight = WallType.SOLID;
            s.ceiling = s.handrail = s.mirror = true;
            s.cctv = false;
            s.copPosition = CopPosition.FRONT_RIGHT;
            s.doorStyle = DoorStyle.CENTER_2;
        }
        String p = "qwertys_elevator:";
        s.matWall = p + wall;
        s.matFloor = p + floor;
        s.matCeiling = p + ceiling;
        s.matDoor = p + door;
        s.matTrim = p + trim;
        s.panelStyle = panel;
        s.indicatorStyle = indicator;
        s.buttonLight = light;
        s.lightColor = lightColor;
        if (this == GLASS) {
            s.wallBack = WallType.GLASS;
            s.wallLeft = WallType.GLASS;
            s.wallRight = WallType.GLASS;
            s.mirror = false;
        }
    }
}
