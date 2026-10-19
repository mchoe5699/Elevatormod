package com.qwerty.elevator.registry;

import com.qwerty.elevator.ElevatorMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ElevatorMod.MODID);

    public static final RegistryObject<SoundEvent> CHIME_UP = reg("chime_up");
    public static final RegistryObject<SoundEvent> CHIME_DOWN = reg("chime_down");
    public static final RegistryObject<SoundEvent> CHIME_CAR = reg("chime_car");
    public static final RegistryObject<SoundEvent> BUTTON = reg("button");
    public static final RegistryObject<SoundEvent> DOOR_OPEN = reg("door_open");
    public static final RegistryObject<SoundEvent> DOOR_CLOSE = reg("door_close");
    public static final RegistryObject<SoundEvent> BUZZER = reg("buzzer");
    public static final RegistryObject<SoundEvent> ALARM = reg("alarm");
    public static final RegistryObject<SoundEvent> MOTOR_START = reg("motor_start");
    public static final RegistryObject<SoundEvent> MOTOR_RUN = reg("motor_run");
    public static final RegistryObject<SoundEvent> BRAKE = reg("brake");
    public static final RegistryObject<SoundEvent> RELAY = reg("relay");
    public static final RegistryObject<SoundEvent> FIRE_BELL = reg("fire_bell");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ElevatorMod.MODID, name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
