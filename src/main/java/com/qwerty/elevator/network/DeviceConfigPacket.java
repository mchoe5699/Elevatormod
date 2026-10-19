package com.qwerty.elevator.network;

import com.qwerty.elevator.block.entity.DeviceBlockEntity;
import com.qwerty.elevator.block.entity.LandingDoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Appearance settings for landing doors and hall fixtures. */
public record DeviceConfigPacket(BlockPos pos, CompoundTag config) {
    public static void encode(DeviceConfigPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeNbt(p.config);
    }

    public static DeviceConfigPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        CompoundTag t = buf.readNbt();
        return new DeviceConfigPacket(pos, t == null ? new CompoundTag() : t);
    }

    public static void handle(DeviceConfigPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null || !PacketUtil.canReach(player, p.pos, 12)) return;
        var be = player.level().getBlockEntity(p.pos);
        if (be instanceof DeviceBlockEntity d) d.applyConfig(p.config);
        else if (be instanceof LandingDoorBlockEntity door) door.setConfig(LandingDoorBlockEntity.GroupConfig.load(p.config));
    }
}
