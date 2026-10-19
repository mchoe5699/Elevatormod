package com.qwerty.elevator.elevator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the car travels. Computed by the controller from the linked landing doors and synced to the car.
 *
 * <p>Car-local coordinates: {@code u} to the right (looking out of the car through the front door),
 * {@code y} up from the floor surface, {@code d} from the front door plane towards the back wall.
 * The anchor of a cabin is the point (u=0, y=0, d=0): the middle of the front edge of the floor surface.</p>
 */
public class CarGeometry {
    public static final double WALL = 0.0625;
    public static final double FLOOR_T = 0.125;
    public static final double ROOF_T = 0.125;
    public static final double DOOR_GAP = 0.03;

    public Direction facing = Direction.NORTH;
    public final List<Vec3> stopAnchors = new ArrayList<>();
    public double[] stopS = new double[0];
    /** stop → deck → floor index (or -1). */
    public int[][] stopFloors = new int[0][];
    public String[] floorNames = new String[0];
    public boolean[] floorFront = new boolean[0];
    public boolean[] floorRear = new boolean[0];
    public boolean[] floorService = new boolean[0];
    public int[] floorStop = new int[0];
    public int[] floorDeck = new int[0];

    public float doorWidth = 2f;
    public float doorHeight = 3f;
    public float doorU = 0f;
    public float rearDoorWidth = 2f;
    public float rearDoorU = 0f;
    public boolean anyRear = false;
    public boolean anyFront = true;
    public double deckSpacing = 0;
    public int decks = 1;

    public boolean paternoster = false;
    public Vec3 pBase = Vec3.ZERO;
    public double pHeight = 0;
    public double pCross = 2;

    @Nullable public BlockPos machinePos;

    public boolean valid() {
        return paternoster ? pHeight > 0 : !stopAnchors.isEmpty();
    }

    public int stopCount() { return stopS.length; }

    public double maxS() {
        if (paternoster) return loopLength();
        return stopS.length == 0 ? 0 : stopS[stopS.length - 1];
    }

    public double loopLength() {
        return 2 * pHeight + 2 * pCross;
    }

    // ---------------------------------------------------------------- frame

    public Vec3 right() { Direction r = facing.getClockWise(); return new Vec3(r.getStepX(), 0, r.getStepZ()); }
    public Vec3 back() { Direction b = facing.getOpposite(); return new Vec3(b.getStepX(), 0, b.getStepZ()); }

    public Vec3 toWorld(Vec3 anchor, double u, double y, double d) {
        Vec3 r = right(), b = back();
        return new Vec3(anchor.x + r.x * u + b.x * d, anchor.y + y, anchor.z + r.z * u + b.z * d);
    }

    /** Returns (u, y, d). */
    public Vec3 toLocal(Vec3 anchor, Vec3 p) {
        Vec3 r = right(), b = back();
        double dx = p.x - anchor.x, dz = p.z - anchor.z;
        return new Vec3(dx * r.x + dz * r.z, p.y - anchor.y, dx * b.x + dz * b.z);
    }

    public Vec3 dirToLocal(Vec3 v) {
        Vec3 r = right(), b = back();
        return new Vec3(v.x * r.x + v.z * r.z, v.y, v.x * b.x + v.z * b.z);
    }

    // ---------------------------------------------------------------- path

    /** Anchor of deck 0 at path position s (normal elevators). */
    public Vec3 anchorAt(double s) {
        int n = stopAnchors.size();
        if (n == 0) return Vec3.ZERO;
        if (n == 1 || s <= stopS[0]) {
            Vec3 a = stopAnchors.get(0);
            if (n == 1) return a.add(0, s - stopS[0], 0);
            Vec3 dir = stopAnchors.get(1).subtract(a).normalize();
            return a.add(dir.scale(s - stopS[0]));
        }
        for (int i = 1; i < n; i++) {
            if (s <= stopS[i] || i == n - 1) {
                Vec3 a = stopAnchors.get(i - 1), b = stopAnchors.get(i);
                double len = stopS[i] - stopS[i - 1];
                double t = len <= 1e-9 ? 0 : (s - stopS[i - 1]) / len;
                if (i == n - 1 && s > stopS[i]) {
                    Vec3 dir = b.subtract(a).normalize();
                    return b.add(dir.scale(s - stopS[i]));
                }
                return a.add(b.subtract(a).scale(t));
            }
        }
        return stopAnchors.get(n - 1);
    }

    /** All cabins at path position s. */
    public List<Cabin> cabins(double s, int cabinCount) {
        List<Cabin> list = new ArrayList<>(2);
        if (paternoster) {
            double len = loopLength();
            for (int i = 0; i < cabinCount; i++) {
                double p = mod(s + i * len / cabinCount, len);
                list.add(new Cabin(paternosterPoint(p), 0, i));
            }
            return list;
        }
        Vec3 a = anchorAt(s);
        list.add(new Cabin(a, 0, 0));
        if (decks > 1) list.add(new Cabin(a.add(0, deckSpacing, 0), 1, 1));
        return list;
    }

    public Vec3 paternosterPoint(double p) {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 r = right();
        double h = pHeight, c = pCross;
        if (p < h) return pBase.add(up.scale(p));
        if (p < h + c) return pBase.add(up.scale(h)).add(r.scale(p - h));
        if (p < 2 * h + c) return pBase.add(r.scale(c)).add(up.scale(h - (p - h - c)));
        return pBase.add(r.scale(c - (p - 2 * h - c)));
    }

    public static double mod(double a, double b) {
        double m = a % b;
        return m < 0 ? m + b : m;
    }

    /** The world-space box around one cabin. */
    public AABB cabinBox(Vec3 anchor, ElevatorSettings s) {
        Vec3 a = toWorld(anchor, -s.carWidth / 2.0, -FLOOR_T, 0);
        Vec3 b = toWorld(anchor, s.carWidth / 2.0, s.carHeight + ROOF_T, s.carDepth);
        return new AABB(a, b);
    }

    /** Box covering the whole travel path, used for render culling. */
    public AABB travelBox(ElevatorSettings s) {
        if (!valid()) return new AABB(0, 0, 0, 0, 0, 0);
        AABB box;
        if (paternoster) {
            box = cabinBox(pBase, s).minmax(cabinBox(pBase.add(0, pHeight, 0).add(right().scale(pCross)), s));
        } else {
            box = cabinBox(stopAnchors.get(0), s);
            for (Vec3 a : stopAnchors) box = box.minmax(cabinBox(a.add(0, (decks - 1) * deckSpacing, 0), s));
        }
        box = box.inflate(1.0).expandTowards(0, -s.pitDepth - 1, 0).expandTowards(0, s.overhead + 2, 0);
        if (machinePos != null) box = box.minmax(new AABB(machinePos).inflate(1));
        return box;
    }

    public double minAnchorY() {
        if (paternoster) return pBase.y;
        double m = Double.MAX_VALUE;
        for (Vec3 a : stopAnchors) m = Math.min(m, a.y);
        return m == Double.MAX_VALUE ? 0 : m;
    }

    public double maxAnchorY() {
        if (paternoster) return pBase.y + pHeight;
        double m = -Double.MAX_VALUE;
        for (Vec3 a : stopAnchors) m = Math.max(m, a.y);
        return m == -Double.MAX_VALUE ? 0 : m + (decks - 1) * deckSpacing;
    }

    public boolean isVertical() {
        if (stopAnchors.size() < 2) return true;
        Vec3 a = stopAnchors.get(0);
        for (Vec3 b : stopAnchors) {
            if (Math.abs(b.x - a.x) > 1e-3 || Math.abs(b.z - a.z) > 1e-3) return false;
        }
        return true;
    }

    /** Which floors a deck can send the car to. */
    public List<Integer> floorsForDeck(int deck) {
        List<Integer> list = new ArrayList<>();
        for (int f = 0; f < floorNames.length; f++) {
            if (floorDeck.length > f && floorDeck[f] == deck && floorService[f]) list.add(f);
        }
        return list;
    }

    // ---------------------------------------------------------------- nbt

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("facing", facing.get2DDataValue());
        ListTag anchors = new ListTag();
        for (int i = 0; i < stopAnchors.size(); i++) {
            CompoundTag a = new CompoundTag();
            Vec3 v = stopAnchors.get(i);
            a.putDouble("x", v.x);
            a.putDouble("y", v.y);
            a.putDouble("z", v.z);
            a.putDouble("s", stopS[i]);
            a.putIntArray("f", stopFloors[i]);
            anchors.add(a);
        }
        t.put("stops", anchors);
        ListTag floors = new ListTag();
        for (int i = 0; i < floorNames.length; i++) {
            CompoundTag f = new CompoundTag();
            f.putString("n", floorNames[i]);
            f.putBoolean("fr", floorFront[i]);
            f.putBoolean("re", floorRear[i]);
            f.putBoolean("sv", floorService[i]);
            f.putInt("st", floorStop[i]);
            f.putInt("dk", floorDeck[i]);
            floors.add(f);
        }
        t.put("floors", floors);
        t.putFloat("dw", doorWidth);
        t.putFloat("dh", doorHeight);
        t.putFloat("du", doorU);
        t.putFloat("rdw", rearDoorWidth);
        t.putFloat("rdu", rearDoorU);
        t.putBoolean("rear", anyRear);
        t.putBoolean("front", anyFront);
        t.putDouble("deckSp", deckSpacing);
        t.putInt("decks", decks);
        t.putBoolean("pat", paternoster);
        t.putDouble("px", pBase.x);
        t.putDouble("py", pBase.y);
        t.putDouble("pz", pBase.z);
        t.putDouble("ph", pHeight);
        t.putDouble("pc", pCross);
        if (machinePos != null) t.put("machine", NbtUtils.writeBlockPos(machinePos));
        return t;
    }

    public static CarGeometry load(CompoundTag t) {
        CarGeometry g = new CarGeometry();
        if (t == null || t.isEmpty()) return g;
        g.facing = Direction.from2DDataValue(t.getInt("facing"));
        ListTag anchors = t.getList("stops", Tag.TAG_COMPOUND);
        g.stopS = new double[anchors.size()];
        g.stopFloors = new int[anchors.size()][];
        for (int i = 0; i < anchors.size(); i++) {
            CompoundTag a = anchors.getCompound(i);
            g.stopAnchors.add(new Vec3(a.getDouble("x"), a.getDouble("y"), a.getDouble("z")));
            g.stopS[i] = a.getDouble("s");
            g.stopFloors[i] = a.getIntArray("f");
        }
        ListTag floors = t.getList("floors", Tag.TAG_COMPOUND);
        int n = floors.size();
        g.floorNames = new String[n];
        g.floorFront = new boolean[n];
        g.floorRear = new boolean[n];
        g.floorService = new boolean[n];
        g.floorStop = new int[n];
        g.floorDeck = new int[n];
        for (int i = 0; i < n; i++) {
            CompoundTag f = floors.getCompound(i);
            g.floorNames[i] = f.getString("n");
            g.floorFront[i] = f.getBoolean("fr");
            g.floorRear[i] = f.getBoolean("re");
            g.floorService[i] = f.getBoolean("sv");
            g.floorStop[i] = f.getInt("st");
            g.floorDeck[i] = f.getInt("dk");
        }
        g.doorWidth = t.getFloat("dw");
        g.doorHeight = t.getFloat("dh");
        g.doorU = t.getFloat("du");
        g.rearDoorWidth = t.getFloat("rdw");
        g.rearDoorU = t.getFloat("rdu");
        g.anyRear = t.getBoolean("rear");
        g.anyFront = t.getBoolean("front");
        g.deckSpacing = t.getDouble("deckSp");
        g.decks = Math.max(1, t.getInt("decks"));
        g.paternoster = t.getBoolean("pat");
        g.pBase = new Vec3(t.getDouble("px"), t.getDouble("py"), t.getDouble("pz"));
        g.pHeight = t.getDouble("ph");
        g.pCross = t.getDouble("pc");
        g.machinePos = t.contains("machine") ? NbtUtils.readBlockPos(t.getCompound("machine")) : null;
        return g;
    }

    public record Cabin(Vec3 anchor, int deck, int index) {}
}
