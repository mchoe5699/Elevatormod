package com.qwerty.elevator.block;

import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.block.entity.MachineBlockEntity;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Machine-room equipment: traction machines (권상기), governor (조속기), hydraulic unit, sheaves, main switch. */
public class MachineBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public enum Kind { GEARED, GEARLESS, GOVERNOR, HYDRAULIC, SHEAVE, MAIN_SWITCH }

    public final Kind kind;
    private final VoxelShape[] shapes = new VoxelShape[4];

    public MachineBlock(Properties props, Kind kind) {
        super(props);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            shapes[d.get2DDataValue()] = switch (kind) {
                case GEARED -> Shapes.or(DeviceBlock.box(d, 0, 0, 1, 16, 2, 15), DeviceBlock.box(d, 1, 2, 2, 15, 14, 14));
                case GEARLESS -> Shapes.or(DeviceBlock.box(d, 1, 0, 1, 15, 2, 15), DeviceBlock.box(d, 2, 2, 2, 14, 15, 14));
                case GOVERNOR -> Shapes.or(DeviceBlock.box(d, 2, 0, 2, 14, 2, 14), DeviceBlock.box(d, 4, 2, 6, 12, 14, 10));
                case HYDRAULIC -> DeviceBlock.box(d, 1, 0, 2, 15, 15, 14);
                case SHEAVE -> DeviceBlock.box(d, 2, 1, 4, 14, 15, 12);
                case MAIN_SWITCH -> DeviceBlock.box(d, 4, 3, 13, 12, 13, 16);
            };
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (kind == Kind.MAIN_SWITCH && ctx.getClickedFace().getAxis().isHorizontal()) {
            return defaultBlockState().setValue(FACING, ctx.getClickedFace());
        }
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
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
        return shapes[state.getValue(FACING).get2DDataValue()];
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, ModBlockEntities.MACHINE.get(), MachineBlockEntity::clientTick) : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof LinkerItem) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity be)) return InteractionResult.PASS;
        if (kind == Kind.MAIN_SWITCH) {
            if (!level.isClientSide) {
                be.powerOn = !be.powerOn;
                be.sync();
                if (be.getController() != null && level.getBlockEntity(be.getController()) instanceof ControllerBlockEntity c) {
                    c.setMainPower(be.powerOn);
                }
                level.playSound(null, pos, ModSounds.RELAY.get(), SoundSource.BLOCKS, 1f, 0.8f);
                player.displayClientMessage(Component.translatable(be.powerOn ? "message.qwertys_elevator.power_on" : "message.qwertys_elevator.power_off"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            ControllerBlockEntity c = be.getController() != null && level.getBlockEntity(be.getController()) instanceof ControllerBlockEntity cc ? cc : null;
            if (c == null) player.displayClientMessage(Component.translatable("message.qwertys_elevator.not_linked"), true);
            else player.displayClientMessage(Component.translatable("message.qwertys_elevator.machine_status",
                    c.getSettings().name, String.format("%.2f", Math.abs(c.getVelocity()) * 20.0)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
