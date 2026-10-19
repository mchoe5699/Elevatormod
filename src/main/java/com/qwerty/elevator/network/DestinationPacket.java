package com.qwerty.elevator.network;

import com.qwerty.elevator.block.entity.DeviceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 행선층 예약: the player picked a destination on a destination panel. */
public record DestinationPacket(BlockPos pos, int floor) {
    public static void encode(DestinationPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeVarInt(p.floor);
    }

    public static DestinationPacket decode(FriendlyByteBuf buf) {
        return new DestinationPacket(buf.readBlockPos(), buf.readVarInt());
    }

    public static void handle(DestinationPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null || !PacketUtil.canReach(player, p.pos, 8)) return;
        if (player.level().getBlockEntity(p.pos) instanceof DeviceBlockEntity d) d.pressDestination(player, p.floor);
    }
}
