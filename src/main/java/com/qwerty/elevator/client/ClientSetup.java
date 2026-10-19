package com.qwerty.elevator.client;

import com.qwerty.elevator.ElevatorMod;
import com.qwerty.elevator.client.render.CarRenderer;
import com.qwerty.elevator.client.render.DeviceRenderer;
import com.qwerty.elevator.client.render.LandingDoorRenderer;
import com.qwerty.elevator.client.render.MachineRenderer;
import com.qwerty.elevator.registry.ModBlockEntities;
import com.qwerty.elevator.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ElevatorMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CAR.get(), CarRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LANDING_DOOR.get(), LandingDoorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DEVICE.get(), DeviceRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MACHINE.get(), MachineRenderer::new);
    }
}
