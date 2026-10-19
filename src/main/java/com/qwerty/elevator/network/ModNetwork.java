package com.qwerty.elevator.network;

import com.qwerty.elevator.ElevatorMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ElevatorMod.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(ControllerSettingsPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ControllerSettingsPacket::encode).decoder(ControllerSettingsPacket::decode)
                .consumerMainThread(ControllerSettingsPacket::handle).add();
        CHANNEL.messageBuilder(ControllerActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ControllerActionPacket::encode).decoder(ControllerActionPacket::decode)
                .consumerMainThread(ControllerActionPacket::handle).add();
        CHANNEL.messageBuilder(CarButtonPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CarButtonPacket::encode).decoder(CarButtonPacket::decode)
                .consumerMainThread(CarButtonPacket::handle).add();
        CHANNEL.messageBuilder(DeviceConfigPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DeviceConfigPacket::encode).decoder(DeviceConfigPacket::decode)
                .consumerMainThread(DeviceConfigPacket::handle).add();
        CHANNEL.messageBuilder(DestinationPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DestinationPacket::encode).decoder(DestinationPacket::decode)
                .consumerMainThread(DestinationPacket::handle).add();
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
