package com.qwerty.elevator.block.entity;

import com.qwerty.elevator.ElevatorMod;
import com.qwerty.elevator.block.MachineBlock;
import com.qwerty.elevator.elevator.*;
import com.qwerty.elevator.entity.CarStatus;
import com.qwerty.elevator.entity.ElevatorCarEntity;
import com.qwerty.elevator.registry.ModBlockEntities;
import com.qwerty.elevator.registry.ModEntities;
import com.qwerty.elevator.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 제어반 (elevator controller). Owns the configuration, floor list, call registers and the
 * motion/door state machine, and drives its {@link ElevatorCarEntity}.
 */
public class ControllerBlockEntity extends BlockEntity {
    public enum Phase { IDLE, MOVING, DOORS }
    public enum DoorState { CLOSED, OPENING, OPEN, CLOSING }

    private static final double INSPECTION_SPEED = 0.3 / 20.0; // 0.3 m/s

    // configuration
    private ElevatorSettings settings = new ElevatorSettings();
    private final List<Floor> floors = new ArrayList<>();
    @Nullable private Direction facing;
    @Nullable private BlockPos machinePos;
    @Nullable private BlockPos governorPos;
    @Nullable private BlockPos hydraulicPos;
    private final SimpleContainer storage = new SimpleContainer(27);

    // car
    @Nullable private UUID carId;
    @Nullable private ElevatorCarEntity car;
    private int carEntityId = -1;
    private int spawnCooldown;
    private CarGeometry geometry = new CarGeometry();
    private boolean geometryDirty = true;

    // motion
    private double s;
    private double v;
    private int dir;
    private int preferredDir;
    private int currentStop = -1;
    private int targetStop = -1;
    private int forcedStop = -1;
    private Phase phase = Phase.IDLE;
    private int runTicks;

    // doors
    private DoorState doorState = DoorState.CLOSED;
    private float door;
    private final boolean[] maskFront = new boolean[2];
    private final boolean[] maskRear = new boolean[2];
    private int doorTimer;
    private int obstructionTicks;
    private boolean nudging;
    private boolean doorHold;
    private final Set<BlockPos> openLandings = new HashSet<>();

    // calls
    private boolean[] carCall = new boolean[0];
    private boolean[] hallUp = new boolean[0];
    private boolean[] hallDown = new boolean[0];
    private final Map<Integer, Set<Integer>> pendingDest = new HashMap<>();
    private boolean callServedThisStop;

    // modes / status
    private ElevatorMode mode = ElevatorMode.NORMAL;
    private ElevatorMode modeBeforeStop = ElevatorMode.NORMAL;
    private final Set<BlockPos> pitStops = new HashSet<>();
    private boolean mainPower = true;
    private boolean redstoneFire;
    private boolean fireSwitch;
    private int fireStop = -1;
    private boolean landingUnlocked;
    private int jogDir;
    private long jogUntil;
    private int idleTicks;
    private boolean lightOn = true;
    private boolean overload;
    private int loadKg;
    private int alarmTicks;
    private int displayStop = -1;
    private int lanternStop = -1;
    private int lanternDir;
    private int lanternTicks;
    private final List<String> warnings = new ArrayList<>();
    private boolean syncPending;
    private int lastSyncTick;
    private int ticker;

    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTROLLER.get(), pos, state);
    }

    // =================================================================== getters

    public ElevatorSettings getSettings() { return settings; }
    public List<Floor> getFloors() { return floors; }
    public CarGeometry getGeometry() { return geometry; }
    public double getVelocity() { return v; }
    public double getPosition() { return s; }
    public ElevatorMode getMode() { return mode; }
    public Phase getPhase() { return phase; }
    public int getDisplayStop() { return displayStop; }
    public int getDir() { return dir; }
    public boolean isOverload() { return overload; }
    public int getLoadKg() { return loadKg; }
    public List<String> getWarnings() { return warnings; }
    public float getDoor() { return door; }
    public SimpleContainer getStorage() { return storage; }
    @Nullable public Direction getFacing() { return facing; }
    public boolean isMainPower() { return mainPower; }
    public int getLanternStop() { return lanternStop; }
    public int getLanternDir() { return lanternTicks > 0 ? lanternDir : 0; }
    public int floorCount() { return floors.size(); }
    public int floorY(int f) { return f >= 0 && f < floors.size() ? floors.get(f).y : 0; }
    public String floorName(int f) { return f >= 0 && f < floors.size() ? floors.get(f).name : "-"; }
    public boolean isTopFloor(int f) { return f == floors.size() - 1; }
    public boolean isBottomFloor(int f) { return f == 0; }

    public boolean isMyCar(ElevatorCarEntity e) {
        return carId != null && carId.equals(e.getUUID());
    }

    public boolean hasHallCall(int floor, int d) {
        if (floor < 0) return false;
        boolean[] arr = d > 0 ? hallUp : hallDown;
        return floor < arr.length && arr[floor];
    }

    public boolean hasCarCall(int floor) {
        return floor >= 0 && floor < carCall.length && carCall[floor];
    }

    /** Name of the floor the indicator should show. */
    public String displayName() {
        if (mode == ElevatorMode.STOPPED || !mainPower) return "--";
        if (displayStop < 0 || displayStop >= geometry.stopFloors.length) return "-";
        int[] fl = geometry.stopFloors[displayStop];
        for (int f : fl) if (f >= 0 && f < floors.size()) return floors.get(f).name;
        return "-";
    }

    /** Direction arrow for indicators: travel direction, or the announced direction while stopped. */
    public int displayDir() {
        if (phase == Phase.MOVING) return dir;
        return lanternTicks > 0 ? lanternDir : preferredDir;
    }

    public boolean isMovingNow() { return Math.abs(v) > 1e-6; }

    @Nullable
    public ElevatorCarEntity clientCar() {
        if (level == null || carEntityId < 0) return null;
        Entity e = level.getEntity(carEntityId);
        return e instanceof ElevatorCarEntity c ? c : null;
    }

    @Nullable
    public ElevatorCarEntity serverCar() { return car; }

    // =================================================================== linking

    public Component linkLandingDoor(LandingDoorBlockEntity clicked) {
        LandingDoorBlockEntity door = clicked.masterEntity();
        if (door == null) door = clicked;
        BlockPos m = door.getMasterPos();
        Direction f = door.facing();
        boolean rear;
        if (facing == null || floors.stream().noneMatch(fl -> fl.front != null || fl.rear != null)) {
            facing = f;
            rear = false;
        } else if (f == facing) {
            rear = false;
        } else if (f == facing.getOpposite()) {
            rear = true;
        } else {
            return Component.translatable("message.qwertys_elevator.door_wrong_side").withStyle(ChatFormatting.RED);
        }
        for (Floor fl : floors) {
            if (m.equals(fl.front) || m.equals(fl.rear)) {
                return Component.translatable("message.qwertys_elevator.already_linked");
            }
        }
        int y = m.getY();
        Floor target = null;
        for (Floor fl : floors) if (fl.y == y) target = fl;
        if (target == null) {
            target = new Floor(y);
            floors.add(target);
            floors.sort(Comparator.comparingInt(fl -> fl.y));
        }
        if (rear) target.rear = m; else target.front = m;
        door.setController(worldPosition);
        floorsChanged();
        return Component.translatable("message.qwertys_elevator.door_linked", target.name, Component.translatable(rear ? "qwertys_elevator.side.rear" : "qwertys_elevator.side.front"));
    }

    public Component unlinkLandingDoor(LandingDoorBlockEntity clicked) {
        BlockPos m = clicked.getMasterPos();
        Iterator<Floor> it = floors.iterator();
        boolean found = false;
        while (it.hasNext()) {
            Floor fl = it.next();
            if (m.equals(fl.front)) { fl.front = null; found = true; }
            if (m.equals(fl.rear)) { fl.rear = null; found = true; }
            if (fl.front == null && fl.rear == null) it.remove();
        }
        if (found) {
            clicked.setController(null);
            if (floors.isEmpty()) facing = null;
            floorsChanged();
            return Component.translatable("message.qwertys_elevator.door_unlinked");
        }
        return Component.translatable("message.qwertys_elevator.not_linked");
    }

    public Component linkMachine(MachineBlockEntity m) {
        MachineBlock.Kind k = m.kind();
        switch (k) {
            case GOVERNOR -> governorPos = m.getBlockPos();
            case HYDRAULIC -> hydraulicPos = m.getBlockPos();
            case MAIN_SWITCH -> mainPower = m.powerOn;
            default -> machinePos = m.getBlockPos();
        }
        m.setController(worldPosition);
        geometryDirty = true;
        changed();
        return Component.translatable("message.qwertys_elevator.machine_linked", settings.name);
    }

    private void floorsChanged() {
        int n = floors.size();
        carCall = new boolean[n];
        hallUp = new boolean[n];
        hallDown = new boolean[n];
        pendingDest.clear();
        renameFloors();
        geometryDirty = true;
        changed();
    }

    public void renameFloors() {
        for (int i = 0; i < floors.size(); i++) {
            Floor f = floors.get(i);
            if (!f.customName || settings.autoNames && !f.customName) {
                f.name = Floor.autoName(Floor.floorNumber(settings.lowestFloorNumber, i), settings.useF4);
            }
        }
    }

    // =================================================================== geometry

    private void rebuildGeometry() {
        geometryDirty = false;
        warnings.clear();
        // drop floors whose doors vanished
        boolean removed = false;
        Iterator<Floor> it = floors.iterator();
        while (it.hasNext()) {
            Floor f = it.next();
            if (f.front != null && !(level.getBlockEntity(f.front) instanceof LandingDoorBlockEntity)) { f.front = null; removed = true; }
            if (f.rear != null && !(level.getBlockEntity(f.rear) instanceof LandingDoorBlockEntity)) { f.rear = null; removed = true; }
            if (f.front == null && f.rear == null) { it.remove(); removed = true; }
        }
        if (removed) {
            int n = floors.size();
            carCall = new boolean[n];
            hallUp = new boolean[n];
            hallDown = new boolean[n];
            renameFloors();
        }

        CarGeometry g = new CarGeometry();
        g.decks = settings.type == ElevatorType.PATERNOSTER ? 1 : settings.decks;
        g.machinePos = machinePos;
        if (floors.isEmpty() || facing == null) {
            geometry = g;
            warnings.add("warning.qwertys_elevator.no_floors");
            pushGeometry();
            return;
        }
        g.facing = facing;
        Vec3 right = g.right();
        Vec3 back = g.back();
        Vec3 fwd = back.scale(-1);

        LandingDoorBlockEntity primary = null;
        for (Floor f : floors) {
            if (f.front != null && level.getBlockEntity(f.front) instanceof LandingDoorBlockEntity d) { primary = d; break; }
        }
        LandingDoorBlockEntity primaryRear = null;
        for (Floor f : floors) {
            if (f.rear != null && level.getBlockEntity(f.rear) instanceof LandingDoorBlockEntity d) { primaryRear = d; break; }
        }

        g.doorWidth = settings.doorWidth > 0 ? settings.doorWidth : (primary != null ? primary.getGroupWidth() : primaryRear != null ? primaryRear.getGroupWidth() : 1);
        g.doorHeight = settings.doorHeight > 0 ? settings.doorHeight : (primary != null ? primary.getGroupHeight() : primaryRear != null ? primaryRear.getGroupHeight() : 2);
        g.doorHeight = Math.min(g.doorHeight, settings.carHeight);
        g.doorWidth = Math.min(g.doorWidth, settings.carWidth - 2 * (float) CarGeometry.WALL);
        g.doorU = settings.carOffset;

        boolean inclined = settings.type == ElevatorType.INCLINED;
        Vec3[] anchors = new Vec3[floors.size()];
        Vec3 base = null;
        for (int i = 0; i < floors.size(); i++) {
            Floor f = floors.get(i);
            Vec3 a = null;
            if (f.front != null && level.getBlockEntity(f.front) instanceof LandingDoorBlockEntity d) {
                Vec3 c = d.shaftFaceCenterBottom();
                a = c.add(back.scale(CarGeometry.DOOR_GAP)).subtract(right.scale(settings.carOffset));
            } else if (f.rear != null && level.getBlockEntity(f.rear) instanceof LandingDoorBlockEntity d) {
                Vec3 c = d.shaftFaceCenterBottom();
                a = c.add(fwd.scale(settings.carDepth + CarGeometry.DOOR_GAP));
            }
            if (a == null) a = new Vec3(worldPosition.getX() + 0.5, f.y, worldPosition.getZ() + 0.5);
            if (base == null && f.front != null) base = a;
            anchors[i] = a;
        }
        if (base == null) base = anchors[0];
        if (!inclined) {
            for (int i = 0; i < anchors.length; i++) anchors[i] = new Vec3(base.x, floors.get(i).y, base.z);
        }
        // rear door position relative to the car
        g.anyRear = false;
        g.anyFront = false;
        for (int i = 0; i < floors.size(); i++) {
            Floor f = floors.get(i);
            if (f.front != null) g.anyFront = true;
            if (f.rear != null && level.getBlockEntity(f.rear) instanceof LandingDoorBlockEntity d) {
                g.anyRear = true;
                Vec3 c = d.shaftFaceCenterBottom();
                Vec3 local = g.toLocal(anchors[i], c);
                g.rearDoorU = (float) local.x;
                g.rearDoorWidth = Math.min(d.getGroupWidth(), settings.carWidth - 2 * (float) CarGeometry.WALL);
                if (Math.abs(local.z - (settings.carDepth + CarGeometry.DOOR_GAP)) > 0.6) {
                    warnings.add("warning.qwertys_elevator.rear_depth");
                }
            }
        }

        int n = floors.size();
        g.floorNames = new String[n];
        g.floorFront = new boolean[n];
        g.floorRear = new boolean[n];
        g.floorService = new boolean[n];
        g.floorStop = new int[n];
        g.floorDeck = new int[n];
        for (int i = 0; i < n; i++) {
            Floor f = floors.get(i);
            g.floorNames[i] = f.name;
            g.floorFront[i] = f.front != null;
            g.floorRear[i] = f.rear != null;
            g.floorService[i] = f.service;
        }

        if (settings.type == ElevatorType.PATERNOSTER) {
            g.paternoster = true;
            g.pCross = settings.carWidth + 0.25;
            double bottomY = floors.get(0).y;
            double topY = floors.get(n - 1).y;
            Vec3 b0 = anchors[0];
            g.pBase = new Vec3(b0.x, bottomY - 1.0, b0.z).subtract(right.scale(g.pCross / 2.0));
            g.pHeight = (topY + 1.0) - g.pBase.y;
            g.stopS = new double[0];
            g.stopFloors = new int[0][];
            for (int i = 0; i < n; i++) { g.floorStop[i] = -1; g.floorDeck[i] = 0; }
            g.doorU = 0;
            g.doorWidth = (float) (settings.carWidth - 0.1);
        } else if (g.decks > 1) {
            g.deckSpacing = settings.deckSpacing > 0 ? settings.deckSpacing : (n > 1 ? floors.get(1).y - floors.get(0).y : settings.carHeight + 1);
            int stops = (n + 1) / 2;
            g.stopS = new double[stops];
            g.stopFloors = new int[stops][];
            double acc = 0;
            for (int k = 0; k < stops; k++) {
                Vec3 a = anchors[2 * k];
                if (k > 0) acc += a.distanceTo(g.stopAnchors.get(k - 1));
                g.stopAnchors.add(a);
                g.stopS[k] = acc;
                int upper = 2 * k + 1 < n ? 2 * k + 1 : -1;
                g.stopFloors[k] = new int[]{2 * k, upper};
                g.floorStop[2 * k] = k;
                g.floorDeck[2 * k] = 0;
                if (upper >= 0) {
                    g.floorStop[upper] = k;
                    g.floorDeck[upper] = 1;
                    double gap = floors.get(upper).y - floors.get(2 * k).y;
                    if (Math.abs(gap - g.deckSpacing) > 0.01) warnings.add("warning.qwertys_elevator.deck_spacing");
                }
            }
        } else {
            g.stopS = new double[n];
            g.stopFloors = new int[n][];
            double acc = 0;
            for (int i = 0; i < n; i++) {
                if (i > 0) acc += anchors[i].distanceTo(anchors[i - 1]);
                g.stopAnchors.add(anchors[i]);
                g.stopS[i] = acc;
                g.stopFloors[i] = new int[]{i};
                g.floorStop[i] = i;
                g.floorDeck[i] = 0;
            }
        }
        geometry = g;
        checkWarnings();
        if (!g.paternoster) {
            // keep position sane
            s = Math.max(g.stopS.length > 0 ? g.stopS[0] - settings.pitDepth : 0, Math.min(g.maxS(), s));
            if (currentStop >= g.stopCount()) currentStop = -1;
            if (currentStop >= 0 && Math.abs(g.stopS[currentStop] - s) > 1e-3) currentStop = -1;
            if (phase != Phase.MOVING && currentStop < 0 && g.stopCount() > 0) {
                int near = nearestStop(s);
                if (Math.abs(g.stopS[near] - s) < 1e-3) currentStop = near;
            }
        } else {
            s = CarGeometry.mod(s, Math.max(1e-3, g.loopLength()));
        }
        pushGeometry();
    }

    private void checkWarnings() {
        if (settings.type == ElevatorType.HYDRAULIC && geometry.maxS() > 25) warnings.add("warning.qwertys_elevator.hydraulic_rise");
        if (settings.speedMpm >= 300 && geometry.maxS() < 40) warnings.add("warning.qwertys_elevator.speed_short");
        if (settings.drive.ropes && settings.drive != DriveType.HYDRAULIC_ROPED && machinePos == null) warnings.add("warning.qwertys_elevator.no_machine");
        if (settings.drive == DriveType.HYDRAULIC || settings.drive == DriveType.HYDRAULIC_ROPED) {
            if (hydraulicPos == null) warnings.add("warning.qwertys_elevator.no_hydraulic");
        }
        if (governorPos == null && settings.drive.ropes) warnings.add("warning.qwertys_elevator.no_governor");
        double needH = settings.carHeight;
        if (geometry.doorHeight + 0.01 < 1.8 && settings.riders && settings.type != ElevatorType.PLATFORM_LIFT) warnings.add("warning.qwertys_elevator.low_door");
        if (needH < 1.85 && settings.riders && settings.ceiling) warnings.add("warning.qwertys_elevator.low_car");
        if (findObstruction() != null) warnings.add("warning.qwertys_elevator.obstruction");
    }

    /** First solid block inside the car's travel volume, if any (승강로 간섭 검사). */
    @Nullable
    public BlockPos findObstruction() {
        if (level == null || !geometry.valid() || geometry.paternoster) return null;
        int checked = 0;
        double step = 0.5;
        for (double ps = geometry.stopS[0]; ps <= geometry.maxS() + 1e-6 && checked < 20000; ps += step) {
            for (CarGeometry.Cabin c : geometry.cabins(ps, geometry.decks)) {
                AABB box = geometry.cabinBox(c.anchor(), settings).deflate(0.08);
                int x0 = (int) Math.floor(box.minX), x1 = (int) Math.floor(box.maxX);
                int y0 = (int) Math.floor(box.minY), y1 = (int) Math.floor(box.maxY);
                int z0 = (int) Math.floor(box.minZ), z1 = (int) Math.floor(box.maxZ);
                BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
                for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
                    checked++;
                    mp.set(x, y, z);
                    if (!level.isLoaded(mp)) continue;
                    BlockState st = level.getBlockState(mp);
                    if (st.isAir() || st.getCollisionShape(level, mp).isEmpty()) continue;
                    AABB bb = st.getCollisionShape(level, mp).bounds().move(mp);
                    if (bb.intersects(box)) return mp.immutable();
                }
            }
        }
        return null;
    }

    // =================================================================== car entity

    private void ensureCar() {
        if (!(level instanceof ServerLevel sl)) return;
        if (car != null && !car.isRemoved()) return;
        car = null;
        if (carId != null) {
            Entity e = sl.getEntity(carId);
            if (e instanceof ElevatorCarEntity c && !c.isRemoved() && c.getControllerPos().equals(worldPosition)) {
                car = c;
                carEntityId = c.getId();
                return;
            }
        }
        if (spawnCooldown > 0) { spawnCooldown--; return; }
        Vec3 a = geometry.paternoster ? geometry.pBase : geometry.anchorAt(s);
        if (!sl.isLoaded(BlockPos.containing(a))) { spawnCooldown = 20; return; }
        ElevatorCarEntity c = new ElevatorCarEntity(ModEntities.CAR.get(), sl);
        c.setup(worldPosition, settings, geometry, s);
        if (sl.addFreshEntity(c)) {
            car = c;
            carId = c.getUUID();
            carEntityId = c.getId();
            changed();
        }
        spawnCooldown = 20;
    }

    private void removeCar() {
        if (car != null) car.discard();
        car = null;
    }

    private void pushGeometry() {
        if (car != null && !car.isRemoved()) car.pushGeometry(geometry);
        changed();
    }

    public void respawnCar() {
        removeCar();
        carId = null;
        spawnCooldown = 0;
        changed();
    }

    // =================================================================== settings (from GUI)

    public void applySettings(ElevatorSettings ns, @Nullable List<String> names, @Nullable List<Boolean> service) {
        boolean typeChanged = ns.type != settings.type;
        ns.clamp();
        this.settings = ns;
        if (names != null) {
            for (int i = 0; i < Math.min(names.size(), floors.size()); i++) {
                String nn = names.get(i).trim();
                if (nn.isEmpty()) continue;
                Floor f = floors.get(i);
                if (!nn.equals(f.name)) {
                    f.name = nn.length() > 6 ? nn.substring(0, 6) : nn;
                    f.customName = true;
                }
            }
        }
        if (service != null) {
            for (int i = 0; i < Math.min(service.size(), floors.size()); i++) floors.get(i).service = service.get(i);
        }
        if (settings.autoNames) for (Floor f : floors) f.customName = false;
        renameFloors();
        if (typeChanged) {
            for (Floor fo : floors) {
                for (BlockPos p : new BlockPos[]{fo.front, fo.rear}) {
                    if (p != null && level != null && level.getBlockEntity(p) instanceof LandingDoorBlockEntity d) d.setOpen(0f);
                }
            }
            respawnCar();
            s = geometry.stopCount() > 0 ? geometry.stopS[0] : 0;
            v = 0;
            phase = Phase.IDLE;
            door = 0;
            doorState = DoorState.CLOSED;
            currentStop = -1;
        }
        geometryDirty = true;
        if (car != null && !car.isRemoved()) car.pushSettings(settings);
        changed();
    }

    // =================================================================== external commands

    public double estimateCost(int floor, int d) {
        if (!acceptsHallCalls() || floor < 0 || floor >= floors.size() || !floors.get(floor).service) return Double.MAX_VALUE;
        if (geometry.paternoster) return Double.MAX_VALUE;
        int stop = geometry.floorStop.length > floor ? geometry.floorStop[floor] : -1;
        if (stop < 0) return Double.MAX_VALUE;
        double vmax = Math.max(1e-4, settings.speedPerTick());
        double target = geometry.stopS[stop];
        double cost = Math.abs(target - s) / vmax;
        int calls = 0;
        for (int i = 0; i < floors.size(); i++) if (carCall[i] || hallUp[i] || hallDown[i]) calls++;
        cost += calls * (settings.doorOpenSec + 2 * settings.doorMoveSec) * 20;
        if (phase == Phase.MOVING && dir != 0 && (target - s) * dir < 0) cost += 2 * Math.abs(target - s) / vmax;
        if (hasHallCall(floor, d)) cost -= 100000;
        return cost;
    }

    private boolean acceptsHallCalls() {
        return mainPower && mode == ElevatorMode.NORMAL && !landingUnlocked;
    }

    public boolean registerHallCall(int floor, int d) {
        if (floor < 0 || floor >= floors.size() || !acceptsHallCalls() || !floors.get(floor).service) return false;
        if (geometry.paternoster) return false;
        int stop = geometry.floorStop[floor];
        if (stop == currentStop && phase != Phase.MOVING && (preferredDir == 0 || preferredDir == d)) {
            // car is here: (re)open the doors
            preferredDir = d;
            lanternStop = stop;
            lanternDir = d;
            lanternTicks = 60;
            reopen();
            markSync();
            return true;
        }
        if (d > 0) hallUp[floor] = true; else hallDown[floor] = true;
        lightOn = true;
        idleTicks = 0;
        markSync();
        return true;
    }

    public void registerDestination(int from, int to) {
        if (from < 0 || to < 0 || from >= floors.size() || to >= floors.size()) return;
        if (settings.type == ElevatorType.DUMBWAITER) {
            registerCarCall(to, null);
            return;
        }
        pendingDest.computeIfAbsent(from, k -> new HashSet<>()).add(to);
        registerHallCall(from, floors.get(to).y > floors.get(from).y ? 1 : -1);
    }

    public boolean registerCarCall(int floor, @Nullable Player player) {
        if (floor < 0 || floor >= floors.size() || !floors.get(floor).service) return false;
        if (!mainPower) return false;
        if (mode == ElevatorMode.FIRE_RECALL || mode == ElevatorMode.OUT_OF_SERVICE || mode == ElevatorMode.STOPPED || mode == ElevatorMode.INSPECTION) {
            if (player != null) player.displayClientMessage(Component.translatable("message.qwertys_elevator.calls_disabled"), true);
            return false;
        }
        int stop = geometry.floorStop.length > floor ? geometry.floorStop[floor] : -1;
        if (stop < 0) return false;
        if (stop == currentStop && phase != Phase.MOVING) {
            if (doorState == DoorState.CLOSING || doorState == DoorState.CLOSED) reopen();
            return true;
        }
        carCall[floor] = true;
        lightOn = true;
        idleTicks = 0;
        if (mode == ElevatorMode.FIRE_SERVICE) {
            // firefighter: only the latest call counts
            Arrays.fill(carCall, false);
            carCall[floor] = true;
        }
        markSync();
        return true;
    }

    public void cancelCarCall(int floor) {
        if (floor >= 0 && floor < carCall.length && carCall[floor]) {
            carCall[floor] = false;
            markSync();
        }
    }

    public void pressOpen() {
        if (phase == Phase.MOVING) return;
        if (currentStop < 0) return;
        reopen();
        if (doorState == DoorState.OPEN) doorTimer = Math.max(doorTimer, holdTicks());
    }

    public void pressClose() {
        if (doorState == DoorState.OPEN) {
            doorHold = false;
            if (!overload) doorTimer = 0;
            if (mode == ElevatorMode.FIRE_SERVICE || mode == ElevatorMode.INDEPENDENT) startClosing();
        } else if (doorState == DoorState.OPENING && mode == ElevatorMode.FIRE_SERVICE) {
            startClosing();
        }
    }

    public void toggleHold() {
        doorHold = !doorHold;
        if (doorHold) pressOpen();
        markSync();
    }

    public void pressAlarm(@Nullable Player player) {
        alarmTicks = 60;
        Vec3 at = car != null ? car.position() : Vec3.atCenterOf(worldPosition);
        level.playSound(null, at.x, at.y + 2, at.z, ModSounds.ALARM.get(), SoundSource.BLOCKS, 1.5f, 1f);
        level.playSound(null, worldPosition, ModSounds.ALARM.get(), SoundSource.BLOCKS, 1.0f, 1f);
        String near = displayName();
        Component msg = Component.translatable("message.qwertys_elevator.alarm_broadcast", settings.name, near,
                player != null ? player.getDisplayName() : Component.literal("?")).withStyle(ChatFormatting.RED);
        broadcastNear(msg, 96);
        markSync();
    }

    public void pressPhone(@Nullable Player player) {
        level.playSound(null, worldPosition, ModSounds.RELAY.get(), SoundSource.BLOCKS, 1f, 1.4f);
        Component msg = Component.translatable("message.qwertys_elevator.phone_broadcast", settings.name, displayName(),
                player != null ? player.getDisplayName() : Component.literal("?")).withStyle(ChatFormatting.AQUA);
        broadcastNear(msg, 96);
    }

    private void broadcastNear(Component msg, double radius) {
        if (!(level instanceof ServerLevel sl)) return;
        Vec3 at = car != null ? car.position() : Vec3.atCenterOf(worldPosition);
        for (ServerPlayer p : sl.players()) {
            if (p.position().distanceTo(at) < radius || p.position().distanceTo(Vec3.atCenterOf(worldPosition)) < radius) {
                p.sendSystemMessage(msg);
            }
        }
    }

    public void pressFireButton(@Nullable Player player) {
        if (!settings.fireService) return;
        if (mode == ElevatorMode.FIRE_RECALL && currentStop == fireStop && phase != Phase.MOVING) {
            setMode(ElevatorMode.FIRE_SERVICE);
            if (player != null) player.displayClientMessage(Component.translatable("message.qwertys_elevator.fire_service_on"), true);
        } else if (mode == ElevatorMode.FIRE_SERVICE) {
            setMode(ElevatorMode.FIRE_RECALL);
            if (player != null) player.displayClientMessage(Component.translatable("message.qwertys_elevator.fire_service_off"), true);
        } else if (player != null) {
            player.displayClientMessage(Component.translatable("message.qwertys_elevator.fire_service_need_recall"), true);
        }
    }

    public void setFireRecall(boolean on, int floor) {
        fireSwitch = on;
        updateFireState(floor);
    }

    private void updateFireState(int floor) {
        boolean want = fireSwitch || redstoneFire;
        if (want) {
            int f = floor >= 0 ? floor : settings.fireFloor;
            if (f < 0 || f >= floors.size()) f = 0;
            int stop = floors.isEmpty() || geometry.floorStop.length <= f ? -1 : geometry.floorStop[f];
            fireStop = stop;
            if (mode != ElevatorMode.FIRE_RECALL && mode != ElevatorMode.FIRE_SERVICE) {
                setMode(ElevatorMode.FIRE_RECALL);
                Vec3 at = car != null ? car.position() : Vec3.atCenterOf(worldPosition);
                level.playSound(null, at.x, at.y + 1, at.z, ModSounds.FIRE_BELL.get(), SoundSource.BLOCKS, 1.2f, 1f);
            }
        } else if (mode == ElevatorMode.FIRE_RECALL || mode == ElevatorMode.FIRE_SERVICE) {
            setMode(ElevatorMode.NORMAL);
        }
    }

    public void setPitStop(BlockPos switchPos, boolean on) {
        if (on) pitStops.add(switchPos.immutable()); else pitStops.remove(switchPos);
        changed();
    }

    public void setMainPower(boolean on) {
        mainPower = on;
        if (!on) lightOn = false;
        changed();
    }

    public void onLandingRelocked(BlockPos door) {
        markSync();
    }

    public void setMode(ElevatorMode m) {
        if (m == mode) return;
        ElevatorMode old = mode;
        mode = m;
        switch (m) {
            case FIRE_RECALL, OUT_OF_SERVICE -> {
                clearAllCalls();
                forcedStop = m == ElevatorMode.FIRE_RECALL ? fireStop : stopOfFloor(settings.parkingFloor);
                if (doorState == DoorState.OPEN || doorState == DoorState.OPENING) {
                    if (currentStop != forcedStop) { nudging = true; startClosing(); }
                }
                doorHold = false;
            }
            case FIRE_SERVICE -> {
                clearAllCalls();
                forcedStop = -1;
            }
            case INSPECTION -> {
                forcedStop = -1;
                jogDir = 0;
            }
            case INDEPENDENT -> forcedStop = -1;
            default -> forcedStop = -1;
        }
        if (old == ElevatorMode.INSPECTION && m != ElevatorMode.INSPECTION) {
            jogDir = 0;
            if (currentStop < 0) phase = Phase.IDLE;
        }
        changed();
    }

    public void jog(int d) {
        if (mode != ElevatorMode.INSPECTION) return;
        jogDir = d;
        jogUntil = level.getGameTime() + 6;
    }

    private void clearAllCalls() {
        Arrays.fill(carCall, false);
        Arrays.fill(hallUp, false);
        Arrays.fill(hallDown, false);
        pendingDest.clear();
        markSync();
    }

    private int stopOfFloor(int f) {
        if (f < 0 || f >= geometry.floorStop.length) return geometry.stopCount() > 0 ? 0 : -1;
        return geometry.floorStop[f];
    }

    // =================================================================== ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state, ControllerBlockEntity be) {
        be.tickServer();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ControllerBlockEntity be) {
        if (be.lanternTicks > 0) be.lanternTicks--;
    }

    private void tickServer() {
        ticker++;
        if (geometryDirty) rebuildGeometry();
        if (!geometry.valid()) {
            removeCar();
            flush();
            return;
        }
        ensureCar();

        if (ticker % 5 == 0) {
            boolean powered = level.hasNeighborSignal(worldPosition);
            if (powered != redstoneFire) {
                redstoneFire = powered;
                updateFireState(settings.fireFloor);
            }
            boolean unlocked = false;
            for (Floor f : floors) {
                for (BlockPos p : new BlockPos[]{f.front, f.rear}) {
                    if (p != null && level.getBlockEntity(p) instanceof LandingDoorBlockEntity d && d.isManuallyOpen()) {
                        int fi = floors.indexOf(f);
                        int st = geometry.floorStop.length > fi ? geometry.floorStop[fi] : -1;
                        if (geometry.paternoster || st != currentStop || phase == Phase.MOVING) unlocked = true;
                    }
                }
            }
            if (unlocked != landingUnlocked) { landingUnlocked = unlocked; markSync(); }
        }
        boolean stopNow = !pitStops.isEmpty();
        if (stopNow && mode != ElevatorMode.STOPPED) { modeBeforeStop = mode; mode = ElevatorMode.STOPPED; changed(); }
        if (!stopNow && mode == ElevatorMode.STOPPED) { mode = modeBeforeStop; changed(); }

        if (geometry.paternoster) tickPaternoster(); else tickElevator();

        if (lanternTicks > 0) lanternTicks--;
        if (alarmTicks > 0) alarmTicks--;
        if (ticker % 10 == 0) weigh();
        updateDisplay();
        updateLandingDoors();
        pushToCar();
        flush();
    }

    // ------------------------------------------------------------------ paternoster

    private void tickPaternoster() {
        double len = geometry.loopLength();
        boolean halted = mode == ElevatorMode.STOPPED || !mainPower || landingUnlocked || mode == ElevatorMode.OUT_OF_SERVICE;
        double target = halted ? 0 : settings.speedPerTick();
        if (mode == ElevatorMode.INSPECTION) target = jogDir != 0 && level.getGameTime() < jogUntil ? INSPECTION_SPEED * jogDir : 0;
        double a = settings.accelPerTick();
        if (v < target) v = Math.min(target, v + a);
        else if (v > target) v = Math.max(target, v - a * 2);
        s = CarGeometry.mod(s + v, len);
        phase = Math.abs(v) > 1e-6 ? Phase.MOVING : Phase.IDLE;
        dir = v > 0 ? 1 : v < 0 ? -1 : 0;
        if (Math.abs(v) > 1e-6 && ticker % 60 == 0 && car != null) {
            level.playSound(null, car.getX(), car.getY(), car.getZ(), ModSounds.MOTOR_RUN.get(), SoundSource.BLOCKS, 0.35f, 0.7f);
        }
    }

    // ------------------------------------------------------------------ normal elevators

    private void tickElevator() {
        boolean halted = mode == ElevatorMode.STOPPED || !mainPower || landingUnlocked;
        if (halted) {
            if (Math.abs(v) > 1e-6) {
                v = 0;
                playAtCar(ModSounds.BRAKE.get(), 1f, 1f);
                if (phase == Phase.MOVING) { phase = Phase.IDLE; currentStop = -1; }
            }
            if (doorState == DoorState.OPENING || doorState == DoorState.CLOSING) {
                // freeze doors
            }
            return;
        }
        if (mode == ElevatorMode.INSPECTION) {
            tickInspection();
            return;
        }
        switch (phase) {
            case MOVING -> tickMoving();
            case DOORS -> tickDoors();
            case IDLE -> tickIdle();
        }
    }

    private void tickInspection() {
        double step = 1.0 / Math.max(10, settings.doorMoveSec * 20);
        if (door > 0) {
            door = Math.max(0, door - (float) step);
            doorState = door > 0 ? DoorState.CLOSING : DoorState.CLOSED;
            return;
        }
        doorState = DoorState.CLOSED;
        phase = Phase.IDLE;
        boolean jogging = jogDir != 0 && level.getGameTime() < jogUntil;
        if (!jogging) {
            if (Math.abs(v) > 1e-6) playAtCar(ModSounds.BRAKE.get(), 0.6f, 1.2f);
            v = 0;
            return;
        }
        double lo = geometry.stopS[0];
        double hi = geometry.maxS();
        double ns = s + jogDir * INSPECTION_SPEED;
        ns = Math.max(lo, Math.min(hi, ns));
        v = ns - s;
        s = ns;
        dir = jogDir;
        currentStop = -1;
        int near = nearestStop(s);
        if (Math.abs(geometry.stopS[near] - s) < 1e-3) currentStop = near;
    }

    private void tickIdle() {
        if (currentStop < 0) {
            // not at a landing (after a stop / inspection): run to the nearest one at low speed
            int near = nearestStop(s);
            double d = geometry.stopS[near] - s;
            if (Math.abs(d) < 1e-3) {
                s = geometry.stopS[near];
                currentStop = near;
                return;
            }
            forcedStopRescue = near;
            startMoving(d > 0 ? 1 : -1);
            return;
        }
        // light & idle timers
        idleTicks++;
        boolean anyCalls = anyCall();
        if (anyCalls || (car != null && !occupantsEmpty)) idleTicks = 0;

        if (mode == ElevatorMode.FIRE_RECALL) {
            if (forcedStop >= 0 && currentStop != forcedStop) {
                startMoving(geometry.stopS[forcedStop] > s ? 1 : -1);
            } else if (doorState == DoorState.CLOSED && hasLandingDoorAt(currentStop)) {
                openDoorsAtStop(currentStop);
            }
            return;
        }
        if (mode == ElevatorMode.OUT_OF_SERVICE) {
            if (forcedStop >= 0 && currentStop != forcedStop) startMoving(geometry.stopS[forcedStop] > s ? 1 : -1);
            else lightOn = false;
            return;
        }
        // calls at this stop?
        if (shouldServeHere()) {
            arrive(currentStop, false);
            return;
        }
        int d = pickDirection();
        if (d != 0) {
            startMoving(d);
            return;
        }
        preferredDir = 0;
        if (mode == ElevatorMode.NORMAL && settings.autoReturnSec > 0 && idleTicks > settings.autoReturnSec * 20) {
            int main = settings.mainFloor;
            if (main >= 0 && main < floors.size() && geometry.floorStop[main] != currentStop) {
                carCall[main] = true;
                idleTicks = 0;
                markSync();
            }
        }
        if (settings.lightOffSec > 0 && idleTicks > settings.lightOffSec * 20 && lightOn && occupantsEmpty) {
            lightOn = false;
            markSync();
        }
    }

    private int forcedStopRescue = -1;

    private void startMoving(int d) {
        dir = d;
        preferredDir = d;
        phase = Phase.MOVING;
        currentStop = -1;
        targetStop = -1;
        runTicks = 0;
        lightOn = true;
        idleTicks = 0;
        playAtCar(ModSounds.MOTOR_START.get(), 0.6f, pitchForSpeed());
        if (machinePos != null) level.playSound(null, machinePos, ModSounds.RELAY.get(), SoundSource.BLOCKS, 0.8f, 1f);
        markSync();
    }

    private float pitchForSpeed() {
        return (float) Math.max(0.6, Math.min(1.6, 0.7 + settings.speedMpm / 600.0));
    }

    private void tickMoving() {
        runTicks++;
        double a = settings.accelPerTick();
        double vmax = settings.speedPerTick();
        if (forcedStopRescue >= 0) vmax = Math.min(vmax, 0.5 / 20.0);
        double speed = Math.abs(v);
        double brake = speed * speed / (2 * a);
        int t = chooseTarget(brake);
        if (t < 0) {
            // overshoot safety: go back to the nearest stop
            t = nearestStop(s);
            if ((geometry.stopS[t] - s) * dir < 0) {
                dir = -dir;
                v = 0;
                speed = 0;
            }
        }
        targetStop = t;
        double targetS = geometry.stopS[t];
        double dist = (targetS - s) * dir;
        if (dist < 0) {
            dir = -dir;
            dist = -dist;
            speed = 0;
        }
        if (dist <= brake + speed * 0.5) {
            speed = Math.max(speed - a, Math.min(dist, 0.004));
        } else {
            speed = Math.min(speed + a, vmax);
        }
        if (speed >= dist) speed = dist;
        s += dir * speed;
        v = dir * speed;
        if (runTicks % 40 == 0 && speed > vmax * 0.5) playAtCar(ModSounds.MOTOR_RUN.get(), 0.25f, pitchForSpeed());
        if (dist - speed < 1e-5) {
            s = targetS;
            v = 0;
            playAtCar(ModSounds.BRAKE.get(), 0.4f, 1.1f);
            forcedStopRescue = -1;
            boolean serveCalls = forcedStop < 0 || forcedStop == t;
            arrive(t, true);
            if (!serveCalls) {
                // passing stop during fire recall/parking run: no doors, keep going
                closeDoorsImmediately();
            }
        }
    }

    /** Picks the stop to stop at, among stops still reachable with the current braking distance. */
    private int chooseTarget(double brake) {
        int n = geometry.stopCount();
        List<Integer> ahead = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            double d = (geometry.stopS[k] - s) * dir;
            if (d >= brake - 1e-6) ahead.add(k);
        }
        ahead.sort(Comparator.comparingDouble(k -> (geometry.stopS[k] - s) * dir));
        if (ahead.isEmpty()) return -1;
        if (forcedStopRescue >= 0) {
            return ahead.contains(forcedStopRescue) ? forcedStopRescue : ahead.get(0);
        }
        if (forcedStop >= 0) {
            return ahead.contains(forcedStop) ? forcedStop : ahead.get(0);
        }
        for (int k : ahead) if (shouldStop(k)) return k;
        boolean anyAhead = false;
        for (int k : ahead) if (callsAtStop(k)) { anyAhead = true; break; }
        if (!anyAhead) return ahead.get(0);
        return ahead.get(ahead.size() - 1);
    }

    private boolean shouldStop(int k) {
        for (int f : geometry.stopFloors[k]) {
            if (f < 0 || f >= floors.size() || !floors.get(f).service) continue;
            if (carCall[f]) return true;
            if (mode == ElevatorMode.NORMAL) {
                if (dir > 0 && hallUp[f]) return true;
                if (dir < 0 && hallDown[f]) return true;
            }
        }
        if (mode != ElevatorMode.NORMAL) return false;
        boolean opposite = false;
        for (int f : geometry.stopFloors[k]) {
            if (f < 0 || f >= floors.size()) continue;
            if (dir > 0 && hallDown[f]) opposite = true;
            if (dir < 0 && hallUp[f]) opposite = true;
        }
        return opposite && !anyCallBeyond(k, dir);
    }

    private boolean callsAtStop(int k) {
        if (k < 0 || k >= geometry.stopFloors.length) return false;
        for (int f : geometry.stopFloors[k]) {
            if (f < 0 || f >= floors.size() || !floors.get(f).service) continue;
            if (carCall[f] || hallUp[f] || hallDown[f]) return true;
        }
        return false;
    }

    /** While standing at a landing with the doors shut: is there a call here we should answer now? */
    private boolean shouldServeHere() {
        if (currentStop < 0) return false;
        for (int f : geometry.stopFloors[currentStop]) {
            if (f < 0 || f >= floors.size() || !floors.get(f).service) continue;
            if (carCall[f]) return true;
            if (hallUp[f] && (preferredDir >= 0 || !anyCallBeyond(currentStop, -1))) return true;
            if (hallDown[f] && (preferredDir <= 0 || !anyCallBeyond(currentStop, 1))) return true;
        }
        return false;
    }

    private boolean anyCallBeyond(int k, int d) {
        for (int j = k + d; j >= 0 && j < geometry.stopCount(); j += d) {
            if (callsAtStop(j)) return true;
        }
        return false;
    }

    private boolean anyCall() {
        for (int i = 0; i < floors.size(); i++) if (carCall[i] || hallUp[i] || hallDown[i]) return true;
        return false;
    }

    private int pickDirection() {
        if (forcedStop >= 0 && currentStop != forcedStop) return geometry.stopS[forcedStop] > s ? 1 : -1;
        boolean above = anyCallBeyond(currentStop, 1), below = anyCallBeyond(currentStop, -1);
        if (preferredDir > 0 && above) return 1;
        if (preferredDir < 0 && below) return -1;
        if (above && below) {
            int nu = -1, nd = -1;
            for (int j = currentStop + 1; j < geometry.stopCount(); j++) if (callsAtStop(j)) { nu = j; break; }
            for (int j = currentStop - 1; j >= 0; j--) if (callsAtStop(j)) { nd = j; break; }
            double du = geometry.stopS[nu] - s, dd = s - geometry.stopS[nd];
            return du <= dd ? 1 : -1;
        }
        if (above) return 1;
        if (below) return -1;
        return 0;
    }

    private int nearestStop(double at) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int k = 0; k < geometry.stopCount(); k++) {
            double d = Math.abs(geometry.stopS[k] - at);
            if (d < bd) { bd = d; best = k; }
        }
        return best;
    }

    private void arrive(int t, boolean fromTravel) {
        currentStop = t;
        targetStop = -1;
        phase = Phase.IDLE;
        int incomingDir = fromTravel ? dir : preferredDir;

        if (mode == ElevatorMode.FIRE_RECALL || mode == ElevatorMode.OUT_OF_SERVICE) {
            preferredDir = 0;
            dir = 0;
            if (forcedStop == t) {
                if (mode == ElevatorMode.FIRE_RECALL && hasLandingDoorAt(t)) openDoorsAtStop(t);
                if (mode == ElevatorMode.OUT_OF_SERVICE) lightOn = false;
            }
            markSync();
            return;
        }

        // announced direction (도착 예고)
        boolean above = anyCallBeyond(t, 1), below = anyCallBeyond(t, -1);
        boolean hu = false, hd = false;
        for (int f : geometry.stopFloors[t]) {
            if (f < 0 || f >= floors.size()) continue;
            hu |= hallUp[f];
            hd |= hallDown[f];
        }
        int announce;
        if (incomingDir > 0) announce = (above || hu) ? 1 : (hd || below) ? -1 : 0;
        else if (incomingDir < 0) announce = (below || hd) ? -1 : (hu || above) ? 1 : 0;
        else announce = hu ? 1 : hd ? -1 : above ? 1 : below ? -1 : 0;
        if (mode != ElevatorMode.NORMAL) announce = 0;

        boolean served = false;
        for (int f : geometry.stopFloors[t]) {
            if (f < 0 || f >= floors.size()) continue;
            if (carCall[f]) { carCall[f] = false; served = true; }
            if (announce >= 0 && hallUp[f]) { hallUp[f] = false; served = true; if (announce == 0) announce = 1; }
            if (announce <= 0 && hallDown[f]) { hallDown[f] = false; served = true; if (announce == 0) announce = -1; }
            Set<Integer> dests = pendingDest.get(f);
            if (dests != null) {
                Iterator<Integer> it = dests.iterator();
                while (it.hasNext()) {
                    int dest = it.next();
                    int dd = floors.get(dest).y > floors.get(f).y ? 1 : -1;
                    if (announce == 0 || dd == announce) {
                        carCall[dest] = true;
                        it.remove();
                        if (announce == 0) announce = dd;
                    }
                }
                if (dests.isEmpty()) pendingDest.remove(f);
            }
        }
        preferredDir = announce;
        dir = 0;
        callServedThisStop = served;
        if (served || !fromTravel) {
            lanternStop = t;
            lanternDir = announce;
            lanternTicks = 80;
            if (settings.chime) chime(t, announce);
        }
        if (settings.voice) announceArrival(t, announce);
        if (mode == ElevatorMode.FIRE_SERVICE) {
            markSync();
            return; // doors open only on button
        }
        if (hasLandingDoorAt(t) || settings.doorStyle == DoorStyle.NONE) openDoorsAtStop(t);
        markSync();
    }

    private boolean hasLandingDoorAt(int t) {
        if (t < 0 || t >= geometry.stopFloors.length) return false;
        for (int f : geometry.stopFloors[t]) {
            if (f < 0 || f >= floors.size() || !floors.get(f).service) continue;
            if (floors.get(f).front != null || floors.get(f).rear != null) return true;
        }
        return false;
    }

    private void openDoorsAtStop(int t) {
        Arrays.fill(maskFront, false);
        Arrays.fill(maskRear, false);
        int[] fl = geometry.stopFloors[t];
        for (int deck = 0; deck < fl.length && deck < 2; deck++) {
            int f = fl[deck];
            if (f < 0 || f >= floors.size() || !floors.get(f).service) continue;
            maskFront[deck] = floors.get(f).front != null;
            maskRear[deck] = floors.get(f).rear != null;
        }
        phase = Phase.DOORS;
        if (doorState == DoorState.CLOSED || doorState == DoorState.CLOSING) {
            doorState = DoorState.OPENING;
            if (settings.doorStyle != DoorStyle.NONE) playAtCar(ModSounds.DOOR_OPEN.get(), 0.5f, 1f);
        }
    }

    private void reopen() {
        if (currentStop < 0 || phase == Phase.MOVING) return;
        if (doorState == DoorState.OPEN) {
            doorTimer = Math.max(doorTimer, holdTicks());
            return;
        }
        if (!hasLandingDoorAt(currentStop) && settings.doorStyle != DoorStyle.NONE) return;
        nudging = false;
        openDoorsAtStop(currentStop);
    }

    private void startClosing() {
        if (doorState == DoorState.OPEN || doorState == DoorState.OPENING) {
            doorState = DoorState.CLOSING;
            if (settings.doorStyle != DoorStyle.NONE) playAtCar(ModSounds.DOOR_CLOSE.get(), 0.5f, 1f);
            if (settings.voice) tellOccupants(Component.translatable("voice.qwertys_elevator.closing"));
        }
    }

    private void closeDoorsImmediately() {
        if (doorState == DoorState.OPENING) doorState = DoorState.CLOSING;
    }

    private int holdTicks() {
        int base = (int) (settings.doorOpenSec * 20);
        if (!callServedThisStop) return base;
        return base;
    }

    private void tickDoors() {
        double step = 1.0 / Math.max(10, settings.doorMoveSec * 20);
        if (settings.doorStyle == DoorStyle.NONE) step = 1;
        switch (doorState) {
            case OPENING -> {
                door = (float) Math.min(1, door + step);
                if (door >= 1) {
                    doorState = DoorState.OPEN;
                    doorTimer = holdTicks();
                }
            }
            case OPEN -> {
                boolean obstructed = obstructed();
                boolean waitForClose = mode == ElevatorMode.INDEPENDENT || mode == ElevatorMode.FIRE_SERVICE
                        || (mode == ElevatorMode.FIRE_RECALL && currentStop == forcedStop) || doorHold;
                if (obstructed || overload) {
                    obstructionTicks++;
                    doorTimer = Math.max(doorTimer, 20);
                } else {
                    obstructionTicks = 0;
                }
                if (overload && ticker % 20 == 0) {
                    playAtCar(ModSounds.BUZZER.get(), 0.8f, 1f);
                    tellOccupants(Component.translatable("voice.qwertys_elevator.overload").withStyle(ChatFormatting.RED));
                }
                if (!waitForClose) doorTimer--;
                if (obstructionTicks > 20 * 20 && !overload && !waitForClose) {
                    nudging = true;
                    playAtCar(ModSounds.BUZZER.get(), 0.6f, 0.8f);
                    startClosing();
                } else if (doorTimer <= 0 && !overload && !waitForClose) {
                    startClosing();
                }
            }
            case CLOSING -> {
                if ((!nudging && obstructed()) || overload) {
                    doorState = DoorState.OPENING;
                    playAtCar(ModSounds.DOOR_OPEN.get(), 0.4f, 1.1f);
                    return;
                }
                door = (float) Math.max(0, door - step * (nudging ? 0.5 : 1));
                if (door <= 0) {
                    doorState = DoorState.CLOSED;
                    nudging = false;
                    obstructionTicks = 0;
                    phase = Phase.IDLE;
                    Arrays.fill(maskFront, false);
                    Arrays.fill(maskRear, false);
                }
            }
            case CLOSED -> phase = Phase.IDLE;
        }
    }

    /** Light curtain / safety edge: something standing in a door opening. */
    private boolean obstructed() {
        if (settings.doorStyle == DoorStyle.NONE || car == null) return false;
        for (CarGeometry.Cabin c : geometry.cabins(s, geometry.decks)) {
            int deck = Math.min(c.deck(), 1);
            if (maskFront[deck] && entityIn(c.anchor(), geometry.doorU, geometry.doorWidth, -0.4, 0.3)) return true;
            if (maskRear[deck] && entityIn(c.anchor(), geometry.rearDoorU, geometry.rearDoorWidth, settings.carDepth - 0.3, settings.carDepth + 0.4)) return true;
        }
        return false;
    }

    private boolean entityIn(Vec3 anchor, double u, double width, double d0, double d1) {
        Vec3 p1 = geometry.toWorld(anchor, u - width / 2 + 0.05, 0.1, d0);
        Vec3 p2 = geometry.toWorld(anchor, u + width / 2 - 0.05, Math.min(1.6, geometry.doorHeight * 0.85), d1);
        AABB box = new AABB(p1, p2);
        return !level.getEntities((Entity) null, box, e -> (e instanceof LivingEntity || e instanceof ItemEntity)
                && !e.isSpectator() && !(e instanceof ElevatorCarEntity)).isEmpty();
    }

    private boolean occupantsEmpty = true;

    private void weigh() {
        if (car == null || car.isRemoved()) {
            loadKg = 0;
            overload = false;
            occupantsEmpty = true;
            return;
        }
        List<Entity> occ = car.occupants();
        double kg = 0;
        for (Entity e : occ) kg += Capacity.weightOf(e);
        loadKg = (int) Math.round(kg);
        occupantsEmpty = occ.isEmpty();
        if (!occupantsEmpty) { lightOn = mainPower; idleTicks = 0; }
        boolean ov = phase != Phase.MOVING && kg > settings.capacityKg * 1.0 && !geometry.paternoster;
        if (ov != overload) {
            overload = ov;
            markSync();
        }
    }

    private void updateDisplay() {
        int ds;
        if (geometry.paternoster || geometry.stopCount() == 0) ds = -1;
        else if (phase == Phase.MOVING && dir != 0) {
            // show the floor being passed (rounded towards the travel direction once half way)
            ds = nearestStop(s);
        } else ds = currentStop >= 0 ? currentStop : nearestStop(s);
        if (ds != displayStop) {
            displayStop = ds;
            markSync();
        }
    }

    private void updateLandingDoors() {
        Set<BlockPos> active = new HashSet<>();
        if (geometry.paternoster) {
            // paternoster landings are open entrances: keep them passable
            for (Floor fo : floors) {
                for (BlockPos p : new BlockPos[]{fo.front, fo.rear}) {
                    if (p != null && level.getBlockEntity(p) instanceof LandingDoorBlockEntity d) d.setOpen(1f);
                }
            }
            return;
        }
        if (!geometry.paternoster && currentStop >= 0 && phase != Phase.MOVING && door > 0) {
            int[] fl = geometry.stopFloors[currentStop];
            for (int deck = 0; deck < fl.length && deck < 2; deck++) {
                int f = fl[deck];
                if (f < 0 || f >= floors.size()) continue;
                Floor fo = floors.get(f);
                if (maskFront[deck] && fo.front != null) active.add(fo.front);
                if (maskRear[deck] && fo.rear != null) active.add(fo.rear);
            }
        }
        for (BlockPos p : active) {
            if (level.getBlockEntity(p) instanceof LandingDoorBlockEntity d) d.setOpen(door);
        }
        for (BlockPos p : openLandings) {
            if (!active.contains(p) && level.getBlockEntity(p) instanceof LandingDoorBlockEntity d) d.setOpen(0);
        }
        openLandings.clear();
        openLandings.addAll(active);
    }

    private void pushToCar() {
        if (car == null || car.isRemoved()) return;
        float f0 = maskFront[0] ? door : 0, r0 = maskRear[0] ? door : 0;
        float f1 = maskFront[1] ? door : 0, r1 = maskRear[1] ? door : 0;
        if (settings.doorStyle == DoorStyle.NONE) { f0 = r0 = f1 = r1 = 1; }
        car.pushState(s, v, f0, r0, f1, r1);
        if (ticker % 2 == 0) car.pushStatus(buildStatus());
    }

    private CarStatus buildStatus() {
        CarStatus st = new CarStatus();
        st.display = displayName();
        st.stop = displayStop;
        st.dir = displayDir();
        st.moving = phase == Phase.MOVING;
        byte[] calls = new byte[floors.size()];
        for (int i = 0; i < calls.length; i++) calls[i] = (byte) (carCall[i] ? 1 : 0);
        st.calls = calls;
        st.light = lightOn && mainPower;
        st.overload = overload;
        st.alarm = alarmTicks > 0;
        st.mode = mode;
        st.load = loadKg;
        st.doorHold = doorHold;
        st.message = switch (mode) {
            case INSPECTION -> "car.qwertys_elevator.msg.inspection";
            case FIRE_RECALL -> "car.qwertys_elevator.msg.fire_recall";
            case FIRE_SERVICE -> "car.qwertys_elevator.msg.fire_service";
            case OUT_OF_SERVICE -> "car.qwertys_elevator.msg.out_of_service";
            case STOPPED -> "car.qwertys_elevator.msg.stopped";
            case INDEPENDENT -> "car.qwertys_elevator.msg.independent";
            default -> overload ? "car.qwertys_elevator.msg.overload" : !mainPower ? "car.qwertys_elevator.msg.power" : "";
        };
        return st;
    }

    // ------------------------------------------------------------------ sounds / messages

    private void playAtCar(SoundEvent sound, float vol, float pitch) {
        Vec3 at = car != null ? car.position().add(0, 1.5, 0) : Vec3.atCenterOf(worldPosition);
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, vol, pitch);
    }

    private void chime(int t, int announce) {
        SoundEvent landing = announce < 0 ? ModSounds.CHIME_DOWN.get() : ModSounds.CHIME_UP.get();
        for (int f : geometry.stopFloors[t]) {
            if (f < 0 || f >= floors.size()) continue;
            Floor fo = floors.get(f);
            for (BlockPos p : new BlockPos[]{fo.front, fo.rear}) {
                if (p != null) level.playSound(null, p.above(2), landing, SoundSource.BLOCKS, 0.8f, 1f);
            }
        }
        playAtCar(ModSounds.CHIME_CAR.get(), 0.5f, 1f);
    }

    private void announceArrival(int t, int announce) {
        if (car == null) return;
        String name = displayNameForStop(t);
        Component msg = Component.translatable("voice.qwertys_elevator.arrive", name);
        if (announce > 0) msg = msg.copy().append(" ").append(Component.translatable("voice.qwertys_elevator.going_up"));
        else if (announce < 0) msg = msg.copy().append(" ").append(Component.translatable("voice.qwertys_elevator.going_down"));
        tellOccupants(msg);
    }

    private String displayNameForStop(int t) {
        if (t < 0 || t >= geometry.stopFloors.length) return "-";
        for (int f : geometry.stopFloors[t]) if (f >= 0 && f < floors.size()) return floors.get(f).name;
        return "-";
    }

    private void tellOccupants(Component msg) {
        if (car == null) return;
        for (Entity e : car.occupants()) {
            if (e instanceof Player p) p.displayClientMessage(msg, true);
        }
    }

    // =================================================================== car buttons (from packets)

    public void handleCarButton(Player player, CopLayout.ButtonType type, int floor) {
        switch (type) {
            case FLOOR -> {
                if (hasCarCall(floor) && player.isShiftKeyDown()) cancelCarCall(floor);
                else registerCarCall(floor, player);
            }
            case OPEN -> pressOpen();
            case CLOSE -> pressClose();
            case ALARM -> pressAlarm(player);
            case PHONE -> pressPhone(player);
            case HOLD -> toggleHold();
            case FIRE -> pressFireButton(player);
        }
        playAtCar(ModSounds.BUTTON.get(), 0.4f, 1.2f);
    }

    /** Finds the floor whose landing is closest to a fixture position. */
    public int findFloorNear(BlockPos p) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < floors.size(); i++) {
            Floor f = floors.get(i);
            int dy = p.getY() - f.y;
            if (dy < -2 || dy > 6) continue;
            BlockPos d = f.front != null ? f.front : f.rear;
            if (d == null) continue;
            double dx = p.getX() - d.getX(), dz = p.getZ() - d.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz) + Math.abs(dy) * 0.5;
            if (dist > 24) continue;
            if (dist < bestD) { bestD = dist; best = i; }
        }
        return best;
    }

    // =================================================================== removal

    public void onRemoved() {
        removeCar();
        for (Floor f : floors) {
            for (BlockPos p : new BlockPos[]{f.front, f.rear}) {
                if (p != null && level.getBlockEntity(p) instanceof LandingDoorBlockEntity d) {
                    d.setOpen(0);
                    d.setController(null);
                }
            }
        }
        Containers.dropContents(level, worldPosition, storage);
    }

    // =================================================================== sync / nbt

    private void changed() {
        setChanged();
        markSync();
    }

    public void markSync() {
        syncPending = true;
    }

    private void flush() {
        if (syncPending && level != null && !level.isClientSide && ticker - lastSyncTick >= 2) {
            syncPending = false;
            lastSyncTick = ticker;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveCommon(tag);
        tag.put("storage", storage.createTag());
        if (carId != null) tag.putUUID("car", carId);
    }

    private void saveCommon(CompoundTag tag) {
        tag.put("settings", settings.save());
        ListTag fl = new ListTag();
        for (Floor f : floors) fl.add(f.save());
        tag.put("floors", fl);
        if (facing != null) tag.putInt("facing", facing.get2DDataValue());
        if (machinePos != null) tag.put("machine", NbtUtils.writeBlockPos(machinePos));
        if (governorPos != null) tag.put("governor", NbtUtils.writeBlockPos(governorPos));
        if (hydraulicPos != null) tag.put("hydraulic", NbtUtils.writeBlockPos(hydraulicPos));
        tag.putDouble("s", s);
        tag.putInt("stop", currentStop);
        tag.putString("mode", mode.id());
        tag.putString("modeBefore", modeBeforeStop.id());
        tag.putBoolean("power", mainPower);
        tag.putBoolean("fireSw", fireSwitch);
        tag.putInt("fireStop", fireStop);
        tag.putInt("forced", forcedStop);
        tag.put("carCalls", boolArray(carCall));
        tag.put("hallUp", boolArray(hallUp));
        tag.put("hallDown", boolArray(hallDown));
        ListTag ps = new ListTag();
        for (BlockPos p : pitStops) ps.add(NbtUtils.writeBlockPos(p));
        tag.put("pitStops", ps);
        tag.putFloat("door", door);
        tag.putString("doorState", doorState.name());
        tag.putString("phase", phase == Phase.MOVING ? Phase.IDLE.name() : phase.name());
        tag.putBoolean("mf0", maskFront[0]);
        tag.putBoolean("mf1", maskFront[1]);
        tag.putBoolean("mr0", maskRear[0]);
        tag.putBoolean("mr1", maskRear[1]);
        tag.putBoolean("light", lightOn);
    }

    private static ByteArrayTag boolArray(boolean[] arr) {
        byte[] b = new byte[arr.length];
        for (int i = 0; i < arr.length; i++) b[i] = (byte) (arr[i] ? 1 : 0);
        return new ByteArrayTag(b);
    }

    private static boolean[] readBools(CompoundTag tag, String key, int n) {
        boolean[] out = new boolean[n];
        byte[] b = tag.getByteArray(key);
        for (int i = 0; i < Math.min(n, b.length); i++) out[i] = b[i] != 0;
        return out;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        settings = new ElevatorSettings();
        settings.load(tag.getCompound("settings"));
        floors.clear();
        ListTag fl = tag.getList("floors", Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size(); i++) floors.add(Floor.load(fl.getCompound(i)));
        facing = tag.contains("facing") ? Direction.from2DDataValue(tag.getInt("facing")) : null;
        machinePos = tag.contains("machine") ? NbtUtils.readBlockPos(tag.getCompound("machine")) : null;
        governorPos = tag.contains("governor") ? NbtUtils.readBlockPos(tag.getCompound("governor")) : null;
        hydraulicPos = tag.contains("hydraulic") ? NbtUtils.readBlockPos(tag.getCompound("hydraulic")) : null;
        s = tag.getDouble("s");
        currentStop = tag.contains("stop") ? tag.getInt("stop") : -1;
        mode = Choice.byId(ElevatorMode.class, tag.getString("mode"), ElevatorMode.NORMAL);
        modeBeforeStop = Choice.byId(ElevatorMode.class, tag.getString("modeBefore"), ElevatorMode.NORMAL);
        mainPower = !tag.contains("power") || tag.getBoolean("power");
        fireSwitch = tag.getBoolean("fireSw");
        fireStop = tag.contains("fireStop") ? tag.getInt("fireStop") : -1;
        forcedStop = tag.contains("forced") ? tag.getInt("forced") : -1;
        int n = floors.size();
        carCall = readBools(tag, "carCalls", n);
        hallUp = readBools(tag, "hallUp", n);
        hallDown = readBools(tag, "hallDown", n);
        pitStops.clear();
        ListTag ps = tag.getList("pitStops", Tag.TAG_COMPOUND);
        for (int i = 0; i < ps.size(); i++) pitStops.add(NbtUtils.readBlockPos(ps.getCompound(i)));
        door = tag.getFloat("door");
        try { doorState = DoorState.valueOf(tag.getString("doorState")); } catch (Exception e) { doorState = DoorState.CLOSED; }
        try { phase = Phase.valueOf(tag.getString("phase")); } catch (Exception e) { phase = Phase.IDLE; }
        maskFront[0] = tag.getBoolean("mf0");
        maskFront[1] = tag.getBoolean("mf1");
        maskRear[0] = tag.getBoolean("mr0");
        maskRear[1] = tag.getBoolean("mr1");
        lightOn = !tag.contains("light") || tag.getBoolean("light");
        if (tag.contains("storage")) storage.fromTag(tag.getList("storage", Tag.TAG_COMPOUND));
        if (tag.hasUUID("car")) carId = tag.getUUID("car");
        // client-only sync fields
        if (tag.contains("c_disp")) {
            displayStop = tag.getInt("c_disp");
            dir = tag.getInt("c_dir");
            preferredDir = tag.getInt("c_pdir");
            phase = tag.getBoolean("c_moving") ? Phase.MOVING : phase;
            carEntityId = tag.getInt("c_car");
            int newLantern = tag.getInt("c_lticks");
            lanternStop = tag.getInt("c_lstop");
            lanternDir = tag.getInt("c_ldir");
            lanternTicks = newLantern;
            overload = tag.getBoolean("c_over");
            loadKg = tag.getInt("c_load");
            landingUnlocked = tag.getBoolean("c_unlock");
            geometry = CarGeometry.load(tag.getCompound("c_geo"));
            warnings.clear();
            ListTag w = tag.getList("c_warn", Tag.TAG_STRING);
            for (int i = 0; i < w.size(); i++) warnings.add(w.getString(i));
            v = tag.getDouble("c_v");
        }
        if (level == null || !level.isClientSide) geometryDirty = true;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveCommon(tag);
        tag.putInt("c_disp", displayStop);
        tag.putInt("c_dir", displayDir());
        tag.putInt("c_pdir", preferredDir);
        tag.putBoolean("c_moving", phase == Phase.MOVING);
        tag.putInt("c_car", car != null ? car.getId() : -1);
        tag.putInt("c_lticks", lanternTicks);
        tag.putInt("c_lstop", lanternStop);
        tag.putInt("c_ldir", lanternDir);
        tag.putBoolean("c_over", overload);
        tag.putInt("c_load", loadKg);
        tag.putBoolean("c_unlock", landingUnlocked);
        tag.put("c_geo", geometry.save());
        ListTag w = new ListTag();
        for (String s1 : warnings) w.add(StringTag.valueOf(s1));
        tag.put("c_warn", w);
        tag.putDouble("c_v", v);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) geometryDirty = true;
    }

    public boolean isLandingUnlocked() { return landingUnlocked; }

    /** Test/debug helper: puts the car at a stop immediately, optionally with the doors held open. */
    public void debugPlaceAt(int stop, boolean openDoors) {
        debugPlaceAt(stop, openDoors, true);
    }

    public void debugPlaceAt(int stop, boolean openDoors, boolean hold) {
        if (geometryDirty) rebuildGeometry();
        if (geometry.paternoster || stop < 0 || stop >= geometry.stopCount()) return;
        clearAllCalls();
        s = geometry.stopS[stop];
        v = 0;
        dir = 0;
        currentStop = stop;
        targetStop = -1;
        forcedStopRescue = -1;
        phase = Phase.IDLE;
        door = 0;
        doorState = DoorState.CLOSED;
        doorHold = false;
        if (openDoors) {
            openDoorsAtStop(stop);
            door = 1;
            doorState = DoorState.OPEN;
            doorTimer = hold ? 20 * 60 : (int) (settings.doorOpenSec * 20);
            doorHold = hold;
        }
        lightOn = true;
        markSync();
    }

    public String debugState() {
        return String.format("%s s=%.2f v=%.3f phase=%s stop=%d disp=%d door=%.2f mode=%s calls=%s up=%s down=%s",
                settings.name, s, v, phase, currentStop, displayStop, door, mode,
                Arrays.toString(carCall), Arrays.toString(hallUp), Arrays.toString(hallDown));
    }

    public void logDebug() {
        ElevatorMod.LOGGER.debug("Elevator {} s={} v={} phase={} stop={}", settings.name, s, v, phase, currentStop);
    }
}
