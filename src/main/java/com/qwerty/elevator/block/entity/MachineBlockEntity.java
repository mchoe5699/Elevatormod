package com.qwerty.elevator.block.entity;

import com.qwerty.elevator.block.MachineBlock;
import com.qwerty.elevator.entity.ElevatorCarEntity;
import com.qwerty.elevator.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class MachineBlockEntity extends BlockEntity {
    @Nullable private BlockPos controller;
    public boolean powerOn = true;

    // client animation
    public float angle, angleOld;
    public float speed;

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE.get(), pos, state);
    }

    public MachineBlock.Kind kind() {
        return getBlockState().getBlock() instanceof MachineBlock m ? m.kind : MachineBlock.Kind.GEARED;
    }

    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(MachineBlock.FACING) ? s.getValue(MachineBlock.FACING) : Direction.NORTH;
    }

    @Nullable public BlockPos getController() { return controller; }

    public void setController(@Nullable BlockPos c) {
        controller = c;
        sync();
    }

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity be) {
        be.angleOld = be.angle;
        float target = 0;
        if (be.controller != null && level.isLoaded(be.controller) && level.getBlockEntity(be.controller) instanceof ControllerBlockEntity c) {
            ElevatorCarEntity car = c.clientCar();
            if (car != null) target = (float) car.getV();
            else target = (float) c.getVelocity();
        }
        be.speed = target;
        // sheave radius ~0.3 block: angle in degrees
        float radius = switch (be.kind()) {
            case GEARED -> 0.32f;
            case GEARLESS -> 0.36f;
            case GOVERNOR -> 0.18f;
            case SHEAVE -> 0.3f;
            default -> 0.2f;
        };
        be.angle += (float) Math.toDegrees(target / radius);
        if (be.angle > 360000 || be.angle < -360000) {
            be.angle %= 360f;
            be.angleOld = be.angle;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (controller != null) tag.put("ctrl", NbtUtils.writeBlockPos(controller));
        tag.putBoolean("power", powerOn);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        controller = tag.contains("ctrl") ? NbtUtils.readBlockPos(tag.getCompound("ctrl")) : null;
        powerOn = !tag.contains("power") || tag.getBoolean("power");
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public AABB getRenderBoundingBox() { return new AABB(worldPosition).inflate(1); }
}
