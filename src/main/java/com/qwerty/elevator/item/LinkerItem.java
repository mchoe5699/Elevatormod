package com.qwerty.elevator.item;

import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.block.entity.DeviceBlockEntity;
import com.qwerty.elevator.block.entity.LandingDoorBlockEntity;
import com.qwerty.elevator.block.entity.MachineBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 엘리베이터 링커: right-click a controller to select it, then right-click landing doors, hall fixtures and
 * machine-room equipment to link them. Sneak-right-click a linked part to unlink it.
 */
public class LinkerItem extends Item {
    public LinkerItem(Properties props) {
        super(props);
    }

    @Nullable
    public static BlockPos getController(ItemStack stack, Level level) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("controller")) return null;
        if (!tag.getString("dim").equals(level.dimension().location().toString())) return null;
        return NbtUtils.readBlockPos(tag.getCompound("controller"));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof ControllerBlockEntity c) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.put("controller", NbtUtils.writeBlockPos(pos));
            tag.putString("dim", level.dimension().location().toString());
            tag.putString("name", c.getSettings().name);
            player.displayClientMessage(Component.translatable("message.qwertys_elevator.linker_selected", c.getSettings().name)
                    .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.SUCCESS;
        }

        BlockPos cpos = getController(stack, level);
        if (cpos == null || !(level.getBlockEntity(cpos) instanceof ControllerBlockEntity controller)) {
            if (be instanceof LandingDoorBlockEntity || be instanceof DeviceBlockEntity || be instanceof MachineBlockEntity) {
                player.displayClientMessage(Component.translatable("message.qwertys_elevator.linker_none").withStyle(ChatFormatting.YELLOW), true);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        boolean unlink = player.isShiftKeyDown();

        if (be instanceof LandingDoorBlockEntity door) {
            Component msg = unlink ? controller.unlinkLandingDoor(door) : controller.linkLandingDoor(door);
            player.displayClientMessage(msg, true);
            return InteractionResult.SUCCESS;
        }
        if (be instanceof DeviceBlockEntity dev) {
            if (unlink) {
                boolean ok = dev.unlink(cpos);
                player.displayClientMessage(Component.translatable(ok ? "message.qwertys_elevator.device_unlinked" : "message.qwertys_elevator.not_linked"), true);
            } else {
                boolean ok = dev.link(cpos);
                int floor = controller.findFloorNear(pos);
                Component where = floor >= 0 ? Component.literal(controller.floorName(floor)) : Component.translatable("qwertys_elevator.no_floor");
                player.displayClientMessage(ok
                        ? Component.translatable("message.qwertys_elevator.device_linked", controller.getSettings().name, where).withStyle(ChatFormatting.GREEN)
                        : Component.translatable("message.qwertys_elevator.already_linked"), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (be instanceof MachineBlockEntity m) {
            if (unlink) {
                m.setController(null);
                player.displayClientMessage(Component.translatable("message.qwertys_elevator.device_unlinked"), true);
            } else {
                player.displayClientMessage(controller.linkMachine(m), true);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.hasTag()) {
            if (!level.isClientSide) {
                stack.setTag(null);
                player.displayClientMessage(Component.translatable("message.qwertys_elevator.linker_cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("controller");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("controller")) {
            BlockPos p = NbtUtils.readBlockPos(tag.getCompound("controller"));
            tips.add(Component.translatable("tooltip.qwertys_elevator.linker_target", tag.getString("name"), p.getX(), p.getY(), p.getZ()).withStyle(ChatFormatting.GRAY));
        }
        tips.add(Component.translatable("tooltip.qwertys_elevator.linker_1").withStyle(ChatFormatting.DARK_GRAY));
        tips.add(Component.translatable("tooltip.qwertys_elevator.linker_2").withStyle(ChatFormatting.DARK_GRAY));
        tips.add(Component.translatable("tooltip.qwertys_elevator.linker_3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
