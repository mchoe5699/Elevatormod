package com.qwerty.elevator.elevator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * Everything the player can customise about one elevator (호기).
 * Stored in the controller and copied to the car entity for rendering.
 */
public class ElevatorSettings {
    public static final float MIN_SIZE = 0.75f;
    public static final float MAX_WIDTH = 10f;
    public static final float MAX_DEPTH = 12f;
    public static final float MAX_HEIGHT = 6f;
    public static final int[] SPEED_STEPS = {6, 9, 12, 15, 18, 24, 30, 45, 60, 90, 105, 120, 150, 180, 210, 240, 300, 360, 420, 480, 540, 600, 750, 900, 1000, 1080, 1260};

    public String name = "1호기";
    public ElevatorType type = ElevatorType.PASSENGER;
    public DriveType drive = DriveType.TRACTION_MR;

    // 카 크기 (블록 = m)
    public float carWidth = 3.0f;
    public float carDepth = 2.5f;
    public float carHeight = 3.0f;
    /** Lateral shift of the car centre relative to the landing door centre (+ = to the right when facing the car). */
    public float carOffset = 0f;

    // 출입문
    public DoorStyle doorStyle = DoorStyle.CENTER_2;
    /** 0 = take from the linked landing door. */
    public float doorWidth = 0f;
    public float doorHeight = 0f;

    // 카 디자인
    /** Video reference: cream panels, narrow mirror, timber rail and tiled floor. */
    public boolean videoInterior = false;
    public boolean lgInterior = false;
    public CarShape shape = CarShape.BOX;
    public WallType wallBack = WallType.SOLID;
    public WallType wallLeft = WallType.SOLID;
    public WallType wallRight = WallType.SOLID;
    public boolean ceiling = true;
    public String matWall = "qwertys_elevator:stainless_hairline";
    public String matFloor = "qwertys_elevator:granite_tile";
    public String matCeiling = "qwertys_elevator:stainless_mirror";
    public String matDoor = "qwertys_elevator:stainless_hairline";
    public String matTrim = "qwertys_elevator:stainless_mirror";
    public int lightColor = 0xFFF4E0;
    public PanelStyle panelStyle = PanelStyle.STAINLESS;
    public CopPosition copPosition = CopPosition.FRONT_RIGHT;
    public IndicatorStyle indicatorStyle = IndicatorStyle.RED_DOT;
    public ButtonLight buttonLight = ButtonLight.ORANGE;
    public boolean handrail = true;
    public boolean mirror = true;
    public boolean cctv = true;

    // 성능
    public int capacityKg = 1000;
    public int capacityPersons = 15;
    public int speedMpm = 105;
    public float accel = 0.8f;
    public float doorOpenSec = 4.0f;
    public float doorMoveSec = 2.0f;

    // 특수 형식
    public int decks = 1;
    /** 0 = automatic (distance between the first two floors). */
    public float deckSpacing = 0f;
    public int paternosterCabins = 6;
    public CounterweightSide counterweight = CounterweightSide.BACK;
    public float pitDepth = 1.5f;
    public float overhead = 1.5f;
    public boolean carriesVehicles = false;
    public boolean storage = false;     // 덤웨이터: 카 내부 보관함
    public boolean riders = true;       // 사람이 탈 수 있음
    public boolean fireService = false; // 비상용(소방 2차 운전 가능)

    // 운전
    public int mainFloor = 0;
    public int parkingFloor = 0;
    public int fireFloor = 0;
    public int autoReturnSec = 0;
    public int lightOffSec = 60;
    public int lowestFloorNumber = 1;
    public boolean useF4 = false;   // 4층을 F로 표기
    public boolean autoNames = true;
    public boolean chime = true;
    public boolean voice = true;
    public boolean groupControl = true;

    public ElevatorSettings copy() {
        ElevatorSettings s = new ElevatorSettings();
        s.load(save());
        return s;
    }

    /** Blocks per tick. */
    public double speedPerTick() {
        return speedMpm / 60.0 / 20.0;
    }

    /** Blocks per tick squared. */
    public double accelPerTick() {
        return accel / 400.0;
    }

    public void clamp() {
        carWidth = Mth.clamp(round(carWidth), MIN_SIZE, MAX_WIDTH);
        carDepth = Mth.clamp(round(carDepth), MIN_SIZE, MAX_DEPTH);
        carHeight = Mth.clamp(round(carHeight), 0.75f, MAX_HEIGHT);
        carOffset = Mth.clamp(round(carOffset), -MAX_WIDTH / 2, MAX_WIDTH / 2);
        doorWidth = Mth.clamp(round(doorWidth), 0, MAX_WIDTH);
        doorHeight = Mth.clamp(round(doorHeight), 0, MAX_HEIGHT);
        capacityKg = Mth.clamp(capacityKg, 50, 20000);
        capacityPersons = Mth.clamp(capacityPersons, 0, 300);
        speedMpm = Mth.clamp(speedMpm, 6, 1260);
        accel = Mth.clamp(accel, 0.1f, 2.0f);
        doorOpenSec = Mth.clamp(doorOpenSec, 1f, 60f);
        doorMoveSec = Mth.clamp(doorMoveSec, 0.5f, 10f);
        decks = Mth.clamp(decks, 1, 2);
        deckSpacing = Mth.clamp(deckSpacing, 0, 20);
        paternosterCabins = Mth.clamp(paternosterCabins, 2, 40);
        pitDepth = Mth.clamp(pitDepth, 0, 8);
        overhead = Mth.clamp(overhead, 0, 8);
        autoReturnSec = Mth.clamp(autoReturnSec, 0, 3600);
        lightOffSec = Mth.clamp(lightOffSec, 0, 3600);
        lowestFloorNumber = Mth.clamp(lowestFloorNumber, -20, 200);
        if (name == null || name.isBlank()) name = "1호기";
        if (name.length() > 24) name = name.substring(0, 24);
        if (type == ElevatorType.PATERNOSTER) decks = 1;
    }

    private static float round(float v) {
        return Math.round(v * 16f) / 16f;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putString("type", type.id());
        t.putString("drive", drive.id());
        t.putFloat("w", carWidth);
        t.putFloat("d", carDepth);
        t.putFloat("h", carHeight);
        t.putFloat("off", carOffset);
        t.putString("door", doorStyle.id());
        t.putFloat("dw", doorWidth);
        t.putFloat("dh", doorHeight);
        t.putBoolean("videoInterior", videoInterior);
        t.putBoolean("lgInterior", lgInterior);
        t.putString("shape", shape.id());
        t.putString("wb", wallBack.id());
        t.putString("wl", wallLeft.id());
        t.putString("wr", wallRight.id());
        t.putBoolean("ceil", ceiling);
        t.putString("mWall", matWall);
        t.putString("mFloor", matFloor);
        t.putString("mCeil", matCeiling);
        t.putString("mDoor", matDoor);
        t.putString("mTrim", matTrim);
        t.putInt("light", lightColor);
        t.putString("panel", panelStyle.id());
        t.putString("cop", copPosition.id());
        t.putString("ind", indicatorStyle.id());
        t.putString("btn", buttonLight.id());
        t.putBoolean("rail", handrail);
        t.putBoolean("mirror", mirror);
        t.putBoolean("cctv", cctv);
        t.putInt("kg", capacityKg);
        t.putInt("persons", capacityPersons);
        t.putInt("speed", speedMpm);
        t.putFloat("accel", accel);
        t.putFloat("dOpen", doorOpenSec);
        t.putFloat("dMove", doorMoveSec);
        t.putInt("decks", decks);
        t.putFloat("deckSp", deckSpacing);
        t.putInt("pCabins", paternosterCabins);
        t.putString("cw", counterweight.id());
        t.putFloat("pit", pitDepth);
        t.putFloat("oh", overhead);
        t.putBoolean("vehicles", carriesVehicles);
        t.putBoolean("storage", storage);
        t.putBoolean("riders", riders);
        t.putBoolean("fireSvc", fireService);
        t.putInt("main", mainFloor);
        t.putInt("park", parkingFloor);
        t.putInt("fire", fireFloor);
        t.putInt("ret", autoReturnSec);
        t.putInt("lOff", lightOffSec);
        t.putInt("lowest", lowestFloorNumber);
        t.putBoolean("f4", useF4);
        t.putBoolean("autoNames", autoNames);
        t.putBoolean("chime", chime);
        t.putBoolean("voice", voice);
        t.putBoolean("group", groupControl);
        return t;
    }

    public void load(CompoundTag t) {
        if (t == null || t.isEmpty()) return;
        name = t.getString("name");
        type = Choice.byId(ElevatorType.class, t.getString("type"), ElevatorType.PASSENGER);
        drive = Choice.byId(DriveType.class, t.getString("drive"), DriveType.TRACTION_MR);
        carWidth = t.getFloat("w");
        carDepth = t.getFloat("d");
        carHeight = t.getFloat("h");
        carOffset = t.getFloat("off");
        doorStyle = Choice.byId(DoorStyle.class, t.getString("door"), DoorStyle.CENTER_2);
        doorWidth = t.getFloat("dw");
        doorHeight = t.getFloat("dh");
        videoInterior = t.getBoolean("videoInterior");
        lgInterior = t.getBoolean("lgInterior");
        shape = Choice.byId(CarShape.class, t.getString("shape"), CarShape.BOX);
        wallBack = Choice.byId(WallType.class, t.getString("wb"), WallType.SOLID);
        wallLeft = Choice.byId(WallType.class, t.getString("wl"), WallType.SOLID);
        wallRight = Choice.byId(WallType.class, t.getString("wr"), WallType.SOLID);
        ceiling = t.getBoolean("ceil");
        matWall = t.getString("mWall");
        matFloor = t.getString("mFloor");
        matCeiling = t.getString("mCeil");
        matDoor = t.getString("mDoor");
        matTrim = t.getString("mTrim");
        lightColor = t.getInt("light");
        panelStyle = Choice.byId(PanelStyle.class, t.getString("panel"), PanelStyle.STAINLESS);
        copPosition = Choice.byId(CopPosition.class, t.getString("cop"), CopPosition.FRONT_RIGHT);
        indicatorStyle = Choice.byId(IndicatorStyle.class, t.getString("ind"), IndicatorStyle.RED_DOT);
        buttonLight = Choice.byId(ButtonLight.class, t.getString("btn"), ButtonLight.ORANGE);
        handrail = t.getBoolean("rail");
        mirror = t.getBoolean("mirror");
        cctv = t.getBoolean("cctv");
        capacityKg = t.getInt("kg");
        capacityPersons = t.getInt("persons");
        speedMpm = t.getInt("speed");
        accel = t.getFloat("accel");
        doorOpenSec = t.getFloat("dOpen");
        doorMoveSec = t.getFloat("dMove");
        decks = t.getInt("decks");
        deckSpacing = t.getFloat("deckSp");
        paternosterCabins = t.getInt("pCabins");
        counterweight = Choice.byId(CounterweightSide.class, t.getString("cw"), CounterweightSide.BACK);
        pitDepth = t.getFloat("pit");
        overhead = t.getFloat("oh");
        carriesVehicles = t.getBoolean("vehicles");
        storage = t.getBoolean("storage");
        riders = t.getBoolean("riders");
        fireService = t.getBoolean("fireSvc");
        mainFloor = t.getInt("main");
        parkingFloor = t.getInt("park");
        fireFloor = t.getInt("fire");
        autoReturnSec = t.getInt("ret");
        lightOffSec = t.getInt("lOff");
        lowestFloorNumber = t.getInt("lowest");
        useF4 = t.getBoolean("f4");
        autoNames = t.getBoolean("autoNames");
        chime = t.getBoolean("chime");
        voice = t.getBoolean("voice");
        groupControl = t.getBoolean("group");
        clamp();
    }

    /** Interior floor area in m² (blocks²). */
    public double floorArea() {
        double w = Math.max(0.1, carWidth - 0.125);
        double d = Math.max(0.1, carDepth - 0.125);
        if (shape == CarShape.ROUND) {
            double r = Math.min(w, d) / 2.0;
            return Math.PI * r * r;
        }
        return w * d;
    }

    /** Sets rated load and persons from the floor area (EN 81-20 표 6 / 국내 안전기준 준용, 1인 65kg). */
    public void autoCapacity() {
        capacityKg = Capacity.ratedLoadForArea(floorArea());
        capacityPersons = capacityKg / 65;
    }
}
