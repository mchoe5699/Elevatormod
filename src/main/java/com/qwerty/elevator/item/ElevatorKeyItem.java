package com.qwerty.elevator.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 엘리베이터 키: fire service switch key and landing door emergency unlock key (해제키). */
public class ElevatorKeyItem extends Item {
    public ElevatorKeyItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.translatable("tooltip.qwertys_elevator.key_1").withStyle(ChatFormatting.GRAY));
        tips.add(Component.translatable("tooltip.qwertys_elevator.key_2").withStyle(ChatFormatting.GRAY));
    }
}
