package com.qwerty.elevator.block.entity;

import com.qwerty.elevator.block.DeviceBlock;
import com.qwerty.elevator.elevator.*;
import com.qwerty.elevator.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Shared block entity for hall buttons, lanterns, indicators, destination panels and switches. */
public class DeviceBlockEntity extends BlockEntity {
    private final List<BlockPos> controllers = new ArrayList<>();
    public PanelStyle style = PanelStyle.STAINLESS;
    public IndicatorStyle indicatorStyle = IndicatorStyle.RED_DOT;
    public ButtonLight light = ButtonLight.ORANGE;
    /** Fire switch on / pit stop switch pressed. */
    public boolean active;
    /** Use the style of the linked elevator instead of our own. */
    public boolean followElevator = true;

    // client-side helpers
    public int clientFloor = -1;
    public float blink;
    public final VideoDisplayState videoDisplay = new VideoDisplayState();

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return kind() == DeviceBlock.Kind.HALL_BUTTON && (effectiveStyle() == PanelStyle.VIDEO_CLASSIC || effectiveStyle() == PanelStyle.LG_MGP)
                ? new net.minecraft.world.phys.AABB(worldPosition).expandTowards(0, 0.5, 0)
                : super.getRenderBoundingBox();
    }

    public DeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DEVICE.get(), pos, state);
    }

    public DeviceBlock.Kind kind() {
        return getBlockState().getBlock() instanceof DeviceBlock d ? d.kind : DeviceBlock.Kind.HALL_BUTTON;
    }

    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(DeviceBlock.FACING) ? s.getValue(DeviceBlock.FACING) : Direction.NORTH;
    }

    public List<BlockPos> getControllers() { return controllers; }

    public boolean link(BlockPos controller) {
        if (controllers.contains(controller)) return false;
        if (controllers.size() >= 8) return false;
        controllers.add(controller.immutable());
        changed();
        return true;
    }

    public boolean unlink(BlockPos controller) {
        boolean r = controllers.remove(controller);
        if (r) changed();
        return r;
    }

    @Nullable
    public ControllerBlockEntity primary() {
        for (ControllerBlockEntity c : loadedControllers()) return c;
        return null;
    }

    public List<ControllerBlockEntity> loadedControllers() {
        List<ControllerBlockEntity> list = new ArrayList<>();
        if (level == null) return list;
        for (BlockPos p : controllers) {
            if (!level.isLoaded(p)) continue;
            if (level.getBlockEntity(p) instanceof ControllerBlockEntity c) list.add(c);
        }
        return list;
    }

    public PanelStyle effectiveStyle() {
        ControllerBlockEntity c = followElevator ? primary() : null;
        return c != null ? c.getSettings().panelStyle : style;
    }

    public IndicatorStyle effectiveIndicator() {
        ControllerBlockEntity c = followElevator ? primary() : null;
        return c != null ? c.getSettings().indicatorStyle : indicatorStyle;
    }

    public ButtonLight effectiveLight() {
        ControllerBlockEntity c = followElevator ? primary() : null;
        return c != null ? c.getSettings().buttonLight : light;
    }

    /** Floor this fixture belongs to, for the given controller. */
    public int floorFor(ControllerBlockEntity c) {
        return c.findFloorNear(worldPosition);
    }

    // ------------------------------------------------------------------ actions (server)

    public void pressHallButton(Player player, int dir) {
        List<ControllerBlockEntity> list = loadedControllers();
        if (list.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.qwertys_elevator.not_linked"), true);
            return;
        }
        int floor = floorFor(list.get(0));
        ControllerBlockEntity first = list.get(0);
        if (floor < 0) {
            player.displayClientMessage(Component.translatable("message.qwertys_elevator.no_floor"), true);
            return;
        }
        // single-button panels at terminal floors
        if (first.isTopFloor(floor)) dir = -1;
        else if (first.isBottomFloor(floor)) dir = 1;
        ControllerBlockEntity best = null;
        double bestCost = Double.MAX_VALUE;
        for (ControllerBlockEntity c : list) {
            int f = floorFor(c);
            if (f < 0) continue;
            double cost = c.estimateCost(f, dir);
            if (cost < bestCost) {
                bestCost = cost;
                best = c;
            }
        }
        if (best == null) {
            player.displayClientMessage(Component.translatable("message.qwertys_elevator.out_of_service"), true);
            return;
        }
        best.registerHallCall(floorFor(best), dir);
    }

    public void pressDestination(Player player, int destFloor) {
        List<ControllerBlockEntity> list = loadedControllers();
        if (list.isEmpty()) return;
        ControllerBlockEntity best = null;
        double bestCost = Double.MAX_VALUE;
        int bestFrom = -1;
        for (ControllerBlockEntity c : list) {
            int from = floorFor(c);
            if (from < 0 || destFloor >= c.floorCount() || from == destFloor) continue;
            int dir = c.floorY(destFloor) > c.floorY(from) ? 1 : -1;
            double cost = c.estimateCost(from, dir);
            if (cost < bestCost) {
                bestCost = cost;
                best = c;
                bestFrom = from;
            }
        }
        if (best == null) {
            player.displayClientMessage(Component.translatable("message.qwertys_elevator.out_of_service"), true);
            return;
        }
        best.registerDestination(bestFrom, destFloor);
        player.displayClientMessage(Component.translatable("message.qwertys_elevator.assigned",
                best.getSettings().name, best.floorName(destFloor)), true);
    }

    public void toggleFireSwitch(Player player) {
        active = !active;
        for (ControllerBlockEntity c : loadedControllers()) {
            int floor = floorFor(c);
            c.setFireRecall(active, floor);
        }
        player.displayClientMessage(Component.translatable(active ? "message.qwertys_elevator.fire_on" : "message.qwertys_elevator.fire_off"), true);
        changed();
    }

    public void togglePitStop(Player player) {
        active = !active;
        for (ControllerBlockEntity c : loadedControllers()) c.setPitStop(worldPosition, active);
        player.displayClientMessage(Component.translatable(active ? "message.qwertys_elevator.stop_on" : "message.qwertys_elevator.stop_off"), true);
        changed();
    }

    public void onRemoved() {
        if (active) {
            for (ControllerBlockEntity c : loadedControllers()) {
                if (kind() == DeviceBlock.Kind.PIT_STOP) c.setPitStop(worldPosition, false);
                if (kind() == DeviceBlock.Kind.FIRE_SWITCH) c.setFireRecall(false, -1);
            }
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DeviceBlockEntity be) {
        if (level.isClientSide) {
            be.blink += 1;
            if (level.getGameTime() % 20 == (pos.asLong() & 15)) {
                ControllerBlockEntity c = be.primary();
                be.clientFloor = c == null ? -1 : be.floorFor(c);
            }
        }
    }

    // ------------------------------------------------------------------ nbt

    public CompoundTag saveConfig() {
        CompoundTag t = new CompoundTag();
        t.putString("style", style.id());
        t.putString("ind", indicatorStyle.id());
        t.putString("light", light.id());
        t.putBoolean("follow", followElevator);
        return t;
    }

    public void loadConfig(CompoundTag t) {
        style = Choice.byId(PanelStyle.class, t.getString("style"), PanelStyle.STAINLESS);
        indicatorStyle = Choice.byId(IndicatorStyle.class, t.getString("ind"), IndicatorStyle.RED_DOT);
        light = Choice.byId(ButtonLight.class, t.getString("light"), ButtonLight.ORANGE);
        followElevator = !t.contains("follow") || t.getBoolean("follow");
    }

    public void applyConfig(CompoundTag t) {
        loadConfig(t);
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (BlockPos p : controllers) list.add(NbtUtils.writeBlockPos(p));
        tag.put("controllers", list);
        tag.put("cfg", saveConfig());
        tag.putBoolean("active", active);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        controllers.clear();
        ListTag list = tag.getList("controllers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) controllers.add(NbtUtils.readBlockPos(list.getCompound(i)));
        loadConfig(tag.getCompound("cfg"));
        active = tag.getBoolean("active");
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
}
