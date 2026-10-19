package com.qwerty.elevator.block;

import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.block.entity.DeviceBlockEntity;
import com.qwerty.elevator.client.ClientHooks;
import com.qwerty.elevator.item.ElevatorKeyItem;
import com.qwerty.elevator.item.LinkerItem;
import com.qwerty.elevator.registry.ModBlockEntities;
import com.qwerty.elevator.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

/** Wall-mounted landing / pit fixtures that talk to one or more elevator controllers. */
public class DeviceBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public enum Kind {
        HALL_BUTTON(6, 3, 10, 13, 15),
        HALL_LANTERN(2, 10, 14, 15, 15),
        INDICATOR(2, 3, 14, 13, 15),
        WIDE_INDICATOR(0, 4, 16, 12, 14),
        DESTINATION(3, 2, 13, 14, 15),
        FIRE_SWITCH(5, 5, 11, 11, 15),
        PIT_STOP(5, 4, 11, 12, 14),
        SIGN(3, 5, 13, 11, 15);

        final VoxelShape[] shapes = new VoxelShape[4];

        Kind(double x0, double y0, double x1, double y1, double depth) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                shapes[d.get2DDataValue()] = box(d, x0, y0, depth, x1, y1, 16);
            }
        }
    }

    public final Kind kind;

    public DeviceBlock(Properties props, Kind kind) {
        super(props);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /**
     * Rotates a box given for a block facing NORTH (front at z=0, wall/back at z=16) to the given facing.
     */
    public static VoxelShape box(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return kind.shapes[state.getValue(FACING).get2DDataValue()];
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeviceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.DEVICE.get(), DeviceBlockEntity::tick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof LinkerItem) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof DeviceBlockEntity be)) return InteractionResult.PASS;

        if (player.isShiftKeyDown() && held.isEmpty()) {
            if (level.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openDeviceScreen(pos));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        switch (kind) {
            case HALL_BUTTON -> {
                if (!level.isClientSide) {
                    double localY = hit.getLocation().y - pos.getY();
                    int dir = localY >= 0.5 ? 1 : -1;
                    be.pressHallButton(player, dir);
                    level.playSound(null, pos, ModSounds.BUTTON.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            case DESTINATION -> {
                if (level.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openDestinationScreen(pos));
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            case FIRE_SWITCH -> {
                if (!(held.getItem() instanceof ElevatorKeyItem)) {
                    if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.qwertys_elevator.need_key"), true);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                if (!level.isClientSide) be.toggleFireSwitch(player);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            case PIT_STOP -> {
                if (!level.isClientSide) be.togglePitStop(player);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            case SIGN, INDICATOR, WIDE_INDICATOR, HALL_LANTERN -> {
                if (!level.isClientSide) {
                    ControllerBlockEntity c = be.primary();
                    if (c == null) player.displayClientMessage(Component.translatable("message.qwertys_elevator.not_linked"), true);
                }
                return InteractionResult.PASS;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof DeviceBlockEntity be) {
            be.onRemoved();
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
