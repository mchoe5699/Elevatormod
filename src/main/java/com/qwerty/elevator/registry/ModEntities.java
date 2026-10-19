package com.qwerty.elevator.registry;

import com.qwerty.elevator.ElevatorMod;
import com.qwerty.elevator.entity.ElevatorCarEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ElevatorMod.MODID);

    public static final RegistryObject<EntityType<ElevatorCarEntity>> CAR = TYPES.register("elevator_car",
            () -> EntityType.Builder.<ElevatorCarEntity>of(ElevatorCarEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .noSave()
                    .fireImmune()
                    .noSummon()
                    .build(ElevatorMod.MODID + ":elevator_car"));

    private ModEntities() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
