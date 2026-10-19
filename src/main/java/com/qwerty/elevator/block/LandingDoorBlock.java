package com.qwerty.elevator.block;

import com.qwerty.elevator.block.entity.LandingDoorBlockEntity;
import com.qwerty.elevator.client.ClientHooks;
import com.qwerty.elevator.item.ElevatorKeyItem;
import com.qwerty.elevator.item.LinkerItem;
import com.qwerty.elevator.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

/**
 * 승강장 문 (landing door). Place blocks side by side / on top of each other in the hoistway wall: touching
 * door blocks with the same facing form one door (e.g. 2 wide × 3 high). The block faces the landing.
 */
public class LandingDoorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    private static final VoxelShape[] CLOSED = new VoxelShape[4];
    private static final VoxelShape[] OUTLINE = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            CLOSED[d.get2DDataValue()] = DeviceBlock.box(d, 0, 0, 12, 16, 16, 16);
            OUTLINE[d.get2DDataValue()] = DeviceBlock.box(d, 0, 0, 11, 16, 16, 15);
        }
    }

    public LandingDoorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, OPEN);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        // join an adjacent door if there is one
        for (Direction d : Direction.values()) {
            BlockState n = level.getBlockState(pos.relative(d));
            if (n.getBlock() instanceof LandingDoorBlock) {
                Direction nf = n.getValue(FACING);
                if (d.getAxis() != nf.getAxis()) {
                    facing = nf;
                    break;
                }
            }
        }
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
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return OUTLINE[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(OPEN) ? Shapes.empty() : CLOSED[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LandingDoorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ModBlockEntities.LANDING_DOOR.get(), LandingDoorBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntities.LANDING_DOOR.get(), LandingDoorBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) LandingDoorBlockEntity.rebuildGroupAround(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            LandingDoorBlockEntity.GroupConfig cfg = null;
            if (level.getBlockEntity(pos) instanceof LandingDoorBlockEntity be) cfg = be.configForRebuild();
            super.onRemove(state, level, pos, newState, moving);
            if (!level.isClientSide) {
                for (Direction d : Direction.values()) {
                    BlockPos n = pos.relative(d);
                    if (level.getBlockState(n).getBlock() instanceof LandingDoorBlock) {
                        LandingDoorBlockEntity.rebuildGroupAround(level, n, cfg);
                    }
                }
            }
            return;
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof LinkerItem) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof LandingDoorBlockEntity be)) return InteractionResult.PASS;
        if (held.getItem() instanceof ElevatorKeyItem) {
            if (!level.isClientSide) be.manualUnlock(player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown() && held.isEmpty()) {
            if (level.isClientSide) {
                BlockPos master = be.getMasterPos();
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openLandingDoorScreen(master));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}
