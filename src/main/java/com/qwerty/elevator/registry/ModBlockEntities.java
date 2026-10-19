package com.qwerty.elevator.registry;

import com.qwerty.elevator.ElevatorMod;
import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.block.entity.DeviceBlockEntity;
import com.qwerty.elevator.block.entity.LandingDoorBlockEntity;
import com.qwerty.elevator.block.entity.MachineBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ElevatorMod.MODID);

    public static final RegistryObject<BlockEntityType<ControllerBlockEntity>> CONTROLLER = TYPES.register("controller",
            () -> BlockEntityType.Builder.of(ControllerBlockEntity::new, ModBlocks.CONTROLLER.get()).build(null));

    public static final RegistryObject<BlockEntityType<LandingDoorBlockEntity>> LANDING_DOOR = TYPES.register("landing_door",
            () -> BlockEntityType.Builder.of(LandingDoorBlockEntity::new, ModBlocks.LANDING_DOOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<DeviceBlockEntity>> DEVICE = TYPES.register("device",
            () -> BlockEntityType.Builder.of(DeviceBlockEntity::new,
                    ModBlocks.HALL_BUTTON.get(), ModBlocks.HALL_LANTERN.get(), ModBlocks.FLOOR_INDICATOR.get(),
                    ModBlocks.WIDE_INDICATOR.get(), ModBlocks.DESTINATION_PANEL.get(), ModBlocks.FIRE_SWITCH.get(),
                    ModBlocks.PIT_STOP_SWITCH.get(), ModBlocks.CAPACITY_SIGN.get()).build(null));

    public static final RegistryObject<BlockEntityType<MachineBlockEntity>> MACHINE = TYPES.register("machine",
            () -> BlockEntityType.Builder.of(MachineBlockEntity::new,
                    ModBlocks.TRACTION_MACHINE.get(), ModBlocks.GEARLESS_MACHINE.get(), ModBlocks.GOVERNOR.get(),
                    ModBlocks.HYDRAULIC_UNIT.get(), ModBlocks.DEFLECTOR_SHEAVE.get(), ModBlocks.MAIN_SWITCH.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
