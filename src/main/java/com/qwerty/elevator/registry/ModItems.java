package com.qwerty.elevator.registry;

import com.qwerty.elevator.ElevatorMod;
import com.qwerty.elevator.item.ElevatorKeyItem;
import com.qwerty.elevator.item.LinkerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ElevatorMod.MODID);
    public static final List<RegistryObject<Item>> ORDER = new ArrayList<>();

    public static final RegistryObject<Item> LINKER = reg("elevator_linker", () -> new LinkerItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> KEY = reg("elevator_key", () -> new ElevatorKeyItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WIRE_ROPE = reg("wire_rope", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> MOTOR = reg("elevator_motor", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CONTROL_BOARD = reg("control_board", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> DOOR_OPERATOR = reg("door_operator", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SAFETY_GEAR = reg("safety_gear", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CALL_BUTTON = reg("call_button", () -> new Item(new Item.Properties()));

    private ModItems() {}

    private static RegistryObject<Item> reg(String name, java.util.function.Supplier<Item> sup) {
        RegistryObject<Item> ro = ITEMS.register(name, sup);
        ORDER.add(ro);
        return ro;
    }

    public static void register(IEventBus bus) {
        for (RegistryObject<? extends Block> b : ModBlocks.ORDER) {
            ITEMS.register(b.getId().getPath(), () -> new BlockItem(b.get(), new Item.Properties()));
        }
        ITEMS.register(bus);
    }
}
