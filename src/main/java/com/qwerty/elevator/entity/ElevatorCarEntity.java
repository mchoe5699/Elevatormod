package com.qwerty.elevator.entity;

import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.elevator.CarCollision;
import com.qwerty.elevator.elevator.CarGeometry;
import com.qwerty.elevator.elevator.ElevatorSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The moving elevator car (카). It is a puppet of its {@link ControllerBlockEntity}: the controller decides
 * position, speed and door states; the entity syncs them to clients, renders, and carries whatever stands inside.
 */
public class ElevatorCarEntity extends Entity {
    private static final EntityDataAccessor<BlockPos> CONTROLLER = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<CompoundTag> SETTINGS = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> GEOMETRY = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> STATUS = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Float> POS = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> VEL = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DOOR_F0 = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DOOR_R0 = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DOOR_F1 = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DOOR_R1 = SynchedEntityData.defineId(ElevatorCarEntity.class, EntityDataSerializers.FLOAT);

    private ElevatorSettings settings = new ElevatorSettings();
    private CarGeometry geometry = new CarGeometry();
    private CarStatus status = new CarStatus();

    /** Path position (server: authoritative; client: predicted). */
    private double s;
    private double sOld;
    private double v;
    private double lastCarryS = Double.NaN;
    private long lastClientTick = Long.MIN_VALUE;
    private final float[] doorF = new float[2], doorR = new float[2];
    private final float[] doorFOld = new float[2], doorROld = new float[2];
    /** Accumulated rotation for animated parts (sheaves, fans). */
    public float spin, spinOld;

    public ElevatorCarEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(CONTROLLER, BlockPos.ZERO);
        this.entityData.define(SETTINGS, new CompoundTag());
        this.entityData.define(GEOMETRY, new CompoundTag());
        this.entityData.define(STATUS, new CompoundTag());
        this.entityData.define(POS, 0f);
        this.entityData.define(VEL, 0f);
        this.entityData.define(DOOR_F0, 0f);
        this.entityData.define(DOOR_R0, 0f);
        this.entityData.define(DOOR_F1, 0f);
        this.entityData.define(DOOR_R1, 0f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SETTINGS.equals(key)) {
            ElevatorSettings ns = new ElevatorSettings();
            ns.load(this.entityData.get(SETTINGS));
            this.settings = ns;
            refreshBox();
        } else if (GEOMETRY.equals(key)) {
            this.geometry = CarGeometry.load(this.entityData.get(GEOMETRY));
            refreshBox();
        } else if (STATUS.equals(key)) {
            this.status = CarStatus.load(this.entityData.get(STATUS));
        } else if (POS.equals(key) && level().isClientSide) {
            double server = this.entityData.get(POS);
            double err = server - s;
            if (geometry.paternoster) {
                double len = geometry.loopLength();
                if (len > 0) err = CarGeometry.mod(err + len / 2, len) - len / 2;
            }
            if (Math.abs(err) > 0.75 || Double.isNaN(s) || this.tickCount < 3) {
                s = server;
                sOld = server;
            } else {
                s += err * 0.35;
            }
        } else if (VEL.equals(key)) {
            this.v = this.entityData.get(VEL);
        }
    }

    // ------------------------------------------------------------------ server API

    public void setup(BlockPos controller, ElevatorSettings settings, CarGeometry geometry, double s) {
        this.entityData.set(CONTROLLER, controller);
        this.settings = settings.copy();
        this.geometry = geometry;
        this.entityData.set(SETTINGS, settings.save());
        this.entityData.set(GEOMETRY, geometry.save());
        this.s = s;
        this.sOld = s;
        this.entityData.set(POS, (float) s);
        refreshBox();
        updatePosition();
    }

    public void pushSettings(ElevatorSettings settings) {
        this.settings = settings.copy();
        this.entityData.set(SETTINGS, settings.save());
        refreshBox();
    }

    public void pushGeometry(CarGeometry geometry) {
        this.geometry = geometry;
        this.entityData.set(GEOMETRY, geometry.save());
        this.lastCarryS = Double.NaN;
        refreshBox();
    }

    public void pushState(double s, double v, float f0, float r0, float f1, float r1) {
        this.s = s;
        this.v = v;
        this.entityData.set(POS, (float) s);
        this.entityData.set(VEL, (float) v);
        this.entityData.set(DOOR_F0, f0);
        this.entityData.set(DOOR_R0, r0);
        this.entityData.set(DOOR_F1, f1);
        this.entityData.set(DOOR_R1, r1);
        doorF[0] = f0; doorR[0] = r0; doorF[1] = f1; doorR[1] = r1;
    }

    public void pushStatus(CarStatus st) {
        CompoundTag tag = st.save();
        if (!tag.equals(this.entityData.get(STATUS))) {
            this.status = st;
            this.entityData.set(STATUS, tag);
        }
    }

    // ------------------------------------------------------------------ getters

    public BlockPos getControllerPos() { return this.entityData.get(CONTROLLER); }
    public ElevatorSettings getSettings() { return settings; }
    public CarGeometry getGeometry() { return geometry; }
    public CarStatus getStatus() { return status; }
    public double getS() { return s; }
    public double getV() { return v; }

    public double getS(float partial) {
        if (geometry.paternoster) {
            double len = geometry.loopLength();
            double d = CarGeometry.mod(s - sOld + len / 2, len) - len / 2;
            return sOld + d * partial;
        }
        return sOld + (s - sOld) * partial;
    }

    public float doorFront(int deck, float partial) {
        deck = Math.min(deck, 1);
        return doorFOld[deck] + (doorF[deck] - doorFOld[deck]) * partial;
    }

    public float doorRear(int deck, float partial) {
        deck = Math.min(deck, 1);
        return doorROld[deck] + (doorR[deck] - doorROld[deck]) * partial;
    }

    public CarCollision.Doors[] doors() {
        return new CarCollision.Doors[]{new CarCollision.Doors(doorF[0], doorR[0]), new CarCollision.Doors(doorF[1], doorR[1])};
    }

    public int cabinCount() {
        return geometry.paternoster ? settings.paternosterCabins : geometry.decks;
    }

    public List<CarGeometry.Cabin> cabins(double atS) {
        return geometry.cabins(atS, cabinCount());
    }

    /** The car position the client should use for the current game tick, even if the car hasn't ticked yet. */
    public double predictedS(long gameTime) {
        if (lastClientTick == gameTime) return s;
        double ns = s + v;
        return clampS(ns);
    }

    private double clampS(double value) {
        if (geometry.paternoster) {
            double len = geometry.loopLength();
            return len > 0 ? CarGeometry.mod(value, len) : value;
        }
        if (geometry.stopCount() == 0) return value;
        double lo = geometry.stopS[0] - settings.pitDepth;
        double hi = geometry.maxS() + settings.overhead;
        return Math.max(lo, Math.min(hi, value));
    }

    @Nullable
    public ControllerBlockEntity controller() {
        BlockPos p = getControllerPos();
        if (!level().isLoaded(p)) return null;
        BlockEntity be = level().getBlockEntity(p);
        return be instanceof ControllerBlockEntity c ? c : null;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        spinOld = spin;
        for (int i = 0; i < 2; i++) {
            doorFOld[i] = doorF[i];
            doorROld[i] = doorR[i];
        }
        if (level().isClientSide) {
            clientTick();
        } else {
            serverTick();
        }
        spin += (float) v;
    }

    private void clientTick() {
        doorF[0] = this.entityData.get(DOOR_F0);
        doorR[0] = this.entityData.get(DOOR_R0);
        doorF[1] = this.entityData.get(DOOR_F1);
        doorR[1] = this.entityData.get(DOOR_R1);
        sOld = s;
        s = clampS(s + v);
        lastClientTick = level().getGameTime();
        updatePosition();
    }

    private void serverTick() {
        if (this.tickCount % 20 == 0) {
            ControllerBlockEntity c = controller();
            BlockPos p = getControllerPos();
            if (level().isLoaded(p) && (c == null || !c.isMyCar(this))) {
                discard();
                return;
            }
        }
        sOld = s;
        updatePosition();
        carryServerEntities();
    }

    private void updatePosition() {
        if (!geometry.valid()) return;
        Vec3 a = geometry.paternoster ? geometry.pBase : geometry.anchorAt(s);
        super.setPos(a.x, a.y, a.z);
        refreshBox();
    }

    private void refreshBox() {
        if (geometry == null || settings == null) return;
        this.setBoundingBox(this.makeBoundingBox());
    }

    @Override
    protected AABB makeBoundingBox() {
        if (geometry == null || settings == null || !geometry.valid()) return super.makeBoundingBox();
        List<CarGeometry.Cabin> cabins = cabins(s);
        AABB box = null;
        for (CarGeometry.Cabin c : cabins) {
            AABB b = geometry.cabinBox(c.anchor(), settings);
            box = box == null ? b : box.minmax(b);
        }
        return box == null ? super.makeBoundingBox() : box;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        if (geometry == null || !geometry.valid()) return super.getBoundingBoxForCulling();
        return geometry.travelBox(settings);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 384 * 384;
    }

    private void carryServerEntities() {
        if (!geometry.valid()) return;
        List<CarGeometry.Cabin> now = cabins(s);
        if (Double.isNaN(lastCarryS)) lastCarryS = s;
        List<CarGeometry.Cabin> prev = cabins(lastCarryS);
        AABB area = getBoundingBox().minmax(boxAt(lastCarryS)).inflate(1.5, 1.0, 1.5);
        List<Entity> list = level().getEntities(this, area, e -> !(e instanceof ElevatorCarEntity)
                && !(e instanceof Player) && !(e instanceof HangingEntity) && !e.isPassenger()
                && !(e.getControllingPassenger() instanceof Player));
        CarCollision.Doors[] doors = doors();
        for (Entity e : list) {
            CarCollision.apply(e, geometry, settings, prev, now, doors);
        }
        lastCarryS = s;
    }

    private AABB boxAt(double at) {
        AABB box = null;
        for (CarGeometry.Cabin c : cabins(at)) {
            AABB b = geometry.cabinBox(c.anchor(), settings);
            box = box == null ? b : box.minmax(b);
        }
        return box == null ? getBoundingBox() : box;
    }

    /** Entities currently inside a cabin (for load weighing and announcements). */
    public List<Entity> occupants() {
        List<Entity> out = new java.util.ArrayList<>();
        if (!geometry.valid()) return out;
        List<CarGeometry.Cabin> cabins = cabins(s);
        for (Entity e : level().getEntities(this, getBoundingBox().inflate(0.5), e -> !(e instanceof ElevatorCarEntity))) {
            if (isInside(e, cabins)) out.add(e);
        }
        return out;
    }

    public boolean isInside(Entity e, List<CarGeometry.Cabin> cabins) {
        for (CarGeometry.Cabin c : cabins) {
            Vec3 l = geometry.toLocal(c.anchor(), e.position());
            if (l.x > -settings.carWidth / 2 && l.x < settings.carWidth / 2 && l.z > 0 && l.z < settings.carDepth
                    && l.y > -0.6 && l.y < settings.carHeight) return true;
        }
        return false;
    }

    /** Which deck the entity stands in, or -1. */
    public int deckOf(Entity e) {
        if (!geometry.valid()) return -1;
        for (CarGeometry.Cabin c : cabins(s)) {
            Vec3 l = geometry.toLocal(c.anchor(), e.position());
            if (l.x > -settings.carWidth / 2 - 0.5 && l.x < settings.carWidth / 2 + 0.5 && l.z > -0.8 && l.z < settings.carDepth + 0.5
                    && l.y > -0.8 && l.y < settings.carHeight + 0.5) return c.deck();
        }
        return -1;
    }

    // ------------------------------------------------------------------ misc entity behaviour

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        // position is computed locally from the synced path position
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        CarRegistry.add(this);
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        CarRegistry.remove(this);
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean canBeCollidedWith() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }

    @Override
    public boolean displayFireAnimation() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
