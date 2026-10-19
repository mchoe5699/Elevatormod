package com.qwerty.elevator.registry;

import com.qwerty.elevator.ElevatorMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElevatorMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.qwertys_elevator"))
            .icon(() -> new ItemStack(ModBlocks.CONTROLLER.get()))
            .displayItems((params, out) -> {
                out.accept(ModItems.LINKER.get());
                out.accept(ModItems.KEY.get());
                for (RegistryObject<? extends Block> b : ModBlocks.ORDER) out.accept(b.get());
                for (RegistryObject<net.minecraft.world.item.Item> i : ModItems.ORDER) {
                    if (i != ModItems.LINKER && i != ModItems.KEY) out.accept(i.get());
                }
            })
            .build());

    private ModTabs() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
