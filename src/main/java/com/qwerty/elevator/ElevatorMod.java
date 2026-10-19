package com.qwerty.elevator;

import com.qwerty.elevator.network.ModNetwork;
import com.qwerty.elevator.registry.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(ElevatorMod.MODID)
public class ElevatorMod {
    public static final String MODID = "qwertys_elevator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ElevatorMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(bus);
        ModItems.register(bus);
        ModBlockEntities.register(bus);
        ModEntities.register(bus);
        ModSounds.register(bus);
        ModTabs.register(bus);
        bus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
