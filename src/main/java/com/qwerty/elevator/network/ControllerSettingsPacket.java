package com.qwerty.elevator.network;

import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.elevator.ElevatorSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record ControllerSettingsPacket(BlockPos pos, CompoundTag settings, List<String> names, List<Boolean> service) {
    public static void encode(ControllerSettingsPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeNbt(p.settings);
        buf.writeVarInt(p.names.size());
        for (String n : p.names) buf.writeUtf(n, 32);
        buf.writeVarInt(p.service.size());
        for (Boolean b : p.service) buf.writeBoolean(b);
    }

    public static ControllerSettingsPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        CompoundTag tag = buf.readNbt();
        int n = Math.min(512, buf.readVarInt());
        List<String> names = new ArrayList<>();
        for (int i = 0; i < n; i++) names.add(buf.readUtf(32));
        int m = Math.min(512, buf.readVarInt());
        List<Boolean> service = new ArrayList<>();
        for (int i = 0; i < m; i++) service.add(buf.readBoolean());
        return new ControllerSettingsPacket(pos, tag == null ? new CompoundTag() : tag, names, service);
    }

    public static void handle(ControllerSettingsPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null || !PacketUtil.canReach(player, p.pos, 12)) return;
        if (player.level().getBlockEntity(p.pos) instanceof ControllerBlockEntity c) {
            ElevatorSettings s = new ElevatorSettings();
            s.load(p.settings);
            c.applySettings(s, p.names, p.service);
        }
    }
}
