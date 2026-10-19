package com.qwerty.elevator.client;

import com.qwerty.elevator.client.screen.*;
import com.qwerty.elevator.entity.ElevatorCarEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Client-only entry points, called through DistExecutor from common code. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openControllerScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new ControllerScreen(pos));
    }

    public static void openLandingDoorScreen(BlockPos master) {
        Minecraft.getInstance().setScreen(new DeviceConfigScreen(master));
    }

    public static void openDeviceScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new DeviceConfigScreen(pos));
    }

    public static void openDestinationScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new DestinationScreen(pos));
    }

    public static void openCarPanel(ElevatorCarEntity car, int deck) {
        Minecraft.getInstance().setScreen(new CarPanelScreen(car, deck));
    }
}
