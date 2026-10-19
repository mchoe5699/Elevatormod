package com.qwerty.elevator.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Hoistway / pit hardware without logic (가이드레일, 완충기, 리미트 스위치 …). */
public class PartBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public enum Kind { GUIDE_RAIL, RAIL_BRACKET, SPRING_BUFFER, OIL_BUFFER, LIMIT_SWITCH, LEVELING_VANE, HOISTWAY_LIGHT }

    public final Kind kind;
    private final VoxelShape[] shapes = new VoxelShape[4];

    public PartBlock(Properties props, Kind kind) {
        super(props);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            shapes[d.get2DDataValue()] = switch (kind) {
                case GUIDE_RAIL -> Shapes.or(DeviceBlock.box(d, 4, 0, 14, 12, 16, 16), DeviceBlock.box(d, 7, 0, 8, 9, 16, 14));
                case RAIL_BRACKET -> Shapes.or(DeviceBlock.box(d, 2, 4, 12, 14, 12, 16), DeviceBlock.box(d, 6, 4, 4, 10, 12, 12));
                case SPRING_BUFFER -> Shapes.or(DeviceBlock.box(d, 2, 0, 2, 14, 2, 14), DeviceBlock.box(d, 4, 2, 4, 12, 13, 12), DeviceBlock.box(d, 3, 13, 3, 13, 15, 13));
                case OIL_BUFFER -> Shapes.or(DeviceBlock.box(d, 2, 0, 2, 14, 2, 14), DeviceBlock.box(d, 4, 2, 4, 12, 11, 12), DeviceBlock.box(d, 6, 11, 6, 10, 15, 10), DeviceBlock.box(d, 4, 15, 4, 12, 16, 12));
                case LIMIT_SWITCH -> DeviceBlock.box(d, 5, 3, 11, 11, 13, 16);
                case LEVELING_VANE -> DeviceBlock.box(d, 7, 0, 10, 9, 16, 16);
                case HOISTWAY_LIGHT -> DeviceBlock.box(d, 4, 5, 13, 12, 11, 16);
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
        Direction face = ctx.getClickedFace();
        boolean wall = kind == Kind.GUIDE_RAIL || kind == Kind.RAIL_BRACKET || kind == Kind.LIMIT_SWITCH
                || kind == Kind.LEVELING_VANE || kind == Kind.HOISTWAY_LIGHT;
        if (wall && face.getAxis().isHorizontal()) {
            return defaultBlockState().setValue(FACING, face);
        }
        if (kind == Kind.GUIDE_RAIL) {
            BlockState below = ctx.getLevel().getBlockState(ctx.getClickedPos().below());
            if (below.getBlock() == this) return defaultBlockState().setValue(FACING, below.getValue(FACING));
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
