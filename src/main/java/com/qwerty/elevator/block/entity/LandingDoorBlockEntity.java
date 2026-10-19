package com.qwerty.elevator.block.entity;

import com.qwerty.elevator.block.LandingDoorBlock;
import com.qwerty.elevator.elevator.Choice;
import com.qwerty.elevator.elevator.DoorStyle;
import com.qwerty.elevator.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Every block of a landing door has one of these; the master (lowest-left block) owns the group's
 * configuration and open state and renders the whole door.
 */
public class LandingDoorBlockEntity extends BlockEntity {
    private static final int MAX_GROUP = 64;

    private BlockPos master = null;
    // master-only data
    private int width = 1, height = 1;
    private BlockPos minCorner = null;
    private GroupConfig config = new GroupConfig();
    private float open;
    private float clientOpen, clientOpenOld;
    private int manualTicks;
    @Nullable private BlockPos controller;

    public LandingDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LANDING_DOOR.get(), pos, state);
    }

    /** User-editable door appearance. */
    public static class GroupConfig {
        /** null = same as the car door style of the linked elevator. */
        @Nullable public DoorStyle style = null;
        public String panelMat = "";
        public String frameMat = "";
        public boolean wideFrame = true;
        public boolean showFloorLabel = true;
        public String label = "";

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("style", style == null ? "auto" : style.id());
            t.putString("panel", panelMat);
            t.putString("frame", frameMat);
            t.putBoolean("wide", wideFrame);
            t.putBoolean("lbl", showFloorLabel);
            t.putString("label", label);
            return t;
        }

        public static GroupConfig load(CompoundTag t) {
            GroupConfig c = new GroupConfig();
            String s = t.getString("style");
            c.style = s.isEmpty() || s.equals("auto") ? null : Choice.byId(DoorStyle.class, s, DoorStyle.CENTER_2);
            c.panelMat = t.getString("panel");
            c.frameMat = t.getString("frame");
            c.wideFrame = !t.contains("wide") || t.getBoolean("wide");
            c.showFloorLabel = !t.contains("lbl") || t.getBoolean("lbl");
            c.label = t.getString("label");
            return c;
        }
    }

    // ---------------------------------------------------------------- group handling

    public BlockPos getMasterPos() { return master == null ? worldPosition : master; }
    public boolean isMaster() { return getMasterPos().equals(worldPosition); }
    public int getGroupWidth() { return width; }
    public int getGroupHeight() { return height; }
    public GroupConfig getConfig() { return config; }
    @Nullable public BlockPos getController() { return controller; }

    public Direction facing() {
        BlockState s = getBlockState();
        return s.getBlock() instanceof LandingDoorBlock ? s.getValue(LandingDoorBlock.FACING) : Direction.NORTH;
    }

    @Nullable
    public LandingDoorBlockEntity masterEntity() {
        if (level == null) return null;
        if (isMaster()) return this;
        BlockEntity be = level.getBlockEntity(getMasterPos());
        return be instanceof LandingDoorBlockEntity d ? d : null;
    }

    public GroupConfig configForRebuild() {
        LandingDoorBlockEntity m = masterEntity();
        return m == null ? config : m.config;
    }

    public static void rebuildGroupAround(Level level, BlockPos start) {
        rebuildGroupAround(level, start, null);
    }

    /** Finds all connected door blocks with the same facing and makes the lowest-left one the master. */
    public static void rebuildGroupAround(Level level, BlockPos start, @Nullable GroupConfig fallback) {
        BlockState st = level.getBlockState(start);
        if (!(st.getBlock() instanceof LandingDoorBlock)) return;
        Direction facing = st.getValue(LandingDoorBlock.FACING);
        Direction right = facing.getCounterClockWise(); // "left to right" as seen from the landing
        Set<BlockPos> group = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        group.add(start);
        GroupConfig cfg = null;
        BlockPos oldController = null;
        float oldOpen = 0;
        while (!queue.isEmpty() && group.size() < MAX_GROUP) {
            BlockPos p = queue.poll();
            if (level.getBlockEntity(p) instanceof LandingDoorBlockEntity be) {
                LandingDoorBlockEntity m = be.masterEntity();
                if (m != null) {
                    if (cfg == null) cfg = m.config;
                    if (oldController == null) oldController = m.controller;
                    oldOpen = Math.max(oldOpen, m.open);
                }
            }
            for (Direction d : new Direction[]{Direction.UP, Direction.DOWN, right, right.getOpposite()}) {
                BlockPos n = p.relative(d);
                if (group.contains(n)) continue;
                BlockState ns = level.getBlockState(n);
                if (ns.getBlock() instanceof LandingDoorBlock && ns.getValue(LandingDoorBlock.FACING) == facing) {
                    group.add(n);
                    queue.add(n);
                }
            }
        }
        if (cfg == null) cfg = fallback != null ? fallback : new GroupConfig();
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE, minL = Integer.MAX_VALUE, maxL = Integer.MIN_VALUE;
        for (BlockPos p : group) {
            int l = lateral(p, right);
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
            minL = Math.min(minL, l);
            maxL = Math.max(maxL, l);
        }
        BlockPos masterPos = null;
        for (BlockPos p : group) {
            if (p.getY() == minY && lateral(p, right) == minL) { masterPos = p; break; }
        }
        if (masterPos == null) masterPos = start;
        for (BlockPos p : group) {
            if (level.getBlockEntity(p) instanceof LandingDoorBlockEntity be) {
                be.master = masterPos;
                if (p.equals(masterPos)) {
                    be.width = maxL - minL + 1;
                    be.height = maxY - minY + 1;
                    be.minCorner = masterPos;
                    be.config = GroupConfig.load(cfg.save());
                    be.controller = oldController;
                    be.open = oldOpen;
                }
                be.setChanged();
                be.sync();
            }
        }
    }

    private static int lateral(BlockPos p, Direction right) {
        return p.getX() * right.getStepX() + p.getZ() * right.getStepZ();
    }

    // ---------------------------------------------------------------- geometry for the controller

    /** Lateral unit vector (left → right as seen from the landing). */
    public Vec3 lateralVec() {
        Direction r = facing().getCounterClockWise();
        return new Vec3(r.getStepX(), 0, r.getStepZ());
    }

    /** Centre of the bottom edge of the door opening, on the hoistway-side face of the wall. */
    public Vec3 shaftFaceCenterBottom() {
        BlockPos m = getMasterPos();
        Direction f = facing();
        Vec3 lat = lateralVec();
        Vec3 cellCenter = Vec3.atBottomCenterOf(m);
        Vec3 c = cellCenter.add(lat.scale((width - 1) / 2.0));
        return c.add(-f.getStepX() * 0.5, 0, -f.getStepZ() * 0.5);
    }

    public int floorY() { return getMasterPos().getY(); }

    // ---------------------------------------------------------------- state

    public float getOpen() { return open; }

    public float getOpen(float partial) {
        return clientOpenOld + (clientOpen - clientOpenOld) * partial;
    }

    public boolean isManuallyOpen() { return manualTicks > 0; }

    public void setController(@Nullable BlockPos c) {
        LandingDoorBlockEntity m = masterEntity();
        if (m != null && m != this) { m.setController(c); return; }
        this.controller = c;
        setChanged();
        sync();
    }

    public void setConfig(GroupConfig cfg) {
        LandingDoorBlockEntity m = masterEntity();
        if (m != null && m != this) { m.setConfig(cfg); return; }
        this.config = cfg;
        setChanged();
        sync();
    }

    /** Called by the controller every tick while a car is docked here. */
    public void setOpen(float f) {
        LandingDoorBlockEntity m = masterEntity();
        if (m != null && m != this) { m.setOpen(f); return; }
        if (manualTicks > 0) f = Math.max(f, 1f);
        if (Math.abs(f - open) < 1e-4) return;
        boolean wasOpen = open > 0.02f;
        open = f;
        boolean nowOpen = open > 0.02f;
        if (wasOpen != nowOpen) applyOpenState(nowOpen);
        setChanged();
        sync();
    }

    private void applyOpenState(boolean value) {
        if (level == null) return;
        Direction facing = facing();
        Direction right = facing.getCounterClockWise();
        BlockPos m = getMasterPos();
        for (int dy = 0; dy < height; dy++) {
            for (int dl = 0; dl < width; dl++) {
                BlockPos p = m.above(dy).relative(right, dl);
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof LandingDoorBlock && s.getValue(LandingDoorBlock.OPEN) != value) {
                    level.setBlock(p, s.setValue(LandingDoorBlock.OPEN, value), 2 | 16);
                }
            }
        }
    }

    /** 비상 해제키: opens the door by hand for a while. The controller stops the car while this is active. */
    public void manualUnlock(Player player) {
        LandingDoorBlockEntity m = masterEntity();
        if (m != null && m != this) { m.manualUnlock(player); return; }
        if (manualTicks > 0) {
            manualTicks = 0;
            player.displayClientMessage(Component.translatable("message.qwertys_elevator.door_relocked"), true);
            return;
        }
        manualTicks = 20 * 20;
        player.displayClientMessage(Component.translatable("message.qwertys_elevator.door_unlocked"), true);
        setOpen(1f);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LandingDoorBlockEntity be) {
        if (!be.isMaster()) return;
        if (be.manualTicks > 0) {
            be.manualTicks--;
            if (be.manualTicks == 0) {
                // car not here anymore? close it again
                float f = be.open;
                be.open = 1f;
                be.setOpen(0f);
                if (f > 0 && be.controller != null && level.getBlockEntity(be.controller) instanceof ControllerBlockEntity c) {
                    c.onLandingRelocked(be.getMasterPos());
                }
            }
        }
    }

    // ---------------------------------------------------------------- persistence / sync

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (master != null) tag.put("master", NbtUtils.writeBlockPos(master));
        tag.putInt("w", width);
        tag.putInt("h", height);
        tag.put("cfg", config.save());
        tag.putFloat("open", open);
        tag.putInt("manual", manualTicks);
        if (controller != null) tag.put("ctrl", NbtUtils.writeBlockPos(controller));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        master = tag.contains("master") ? NbtUtils.readBlockPos(tag.getCompound("master")) : null;
        width = Math.max(1, tag.getInt("w"));
        height = Math.max(1, tag.getInt("h"));
        config = GroupConfig.load(tag.getCompound("cfg"));
        open = tag.getFloat("open");
        manualTicks = tag.getInt("manual");
        controller = tag.contains("ctrl") ? NbtUtils.readBlockPos(tag.getCompound("ctrl")) : null;
    }

    /** Client: called every client tick so interpolation stops when no updates arrive. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, LandingDoorBlockEntity be) {
        be.clientOpenOld = be.clientOpen;
        be.clientOpen = be.open;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        Direction right = facing().getCounterClockWise();
        BlockPos far = worldPosition.above(height + 1).relative(right, width + 1);
        return new AABB(worldPosition.below(height + 1).relative(right, -width - 1)).minmax(new AABB(far)).inflate(1);
    }
}
