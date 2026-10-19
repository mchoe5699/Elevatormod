package com.qwerty.elevator.network;

import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.elevator.CopLayout;
import com.qwerty.elevator.entity.ElevatorCarEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A button pressed inside a car (or the dumbwaiter storage opened). type = ordinal of {@link CopLayout.ButtonType}
 * or -1 for "open storage".
 */
public record CarButtonPacket(int entityId, int type, int floor) {
    public static final int STORAGE = -1;

    public static void encode(CarButtonPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeVarInt(p.type);
        buf.writeVarInt(p.floor);
    }

    public static CarButtonPacket decode(FriendlyByteBuf buf) {
        return new CarButtonPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(CarButtonPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null) return;
        Entity e = player.level().getEntity(p.entityId);
        if (!(e instanceof ElevatorCarEntity car)) return;
        if (!car.getBoundingBox().inflate(6).contains(player.position())) return;
        ControllerBlockEntity c = car.controller();
        if (c == null) return;
        if (p.type == STORAGE) {
            if (!c.getSettings().storage) return;
            MenuProvider provider = new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, c.getStorage()),
                    Component.translatable("container.qwertys_elevator.dumbwaiter", c.getSettings().name));
            player.openMenu(provider);
            return;
        }
        CopLayout.ButtonType[] types = CopLayout.ButtonType.values();
        if (p.type < 0 || p.type >= types.length) return;
        c.handleCarButton(player, types[p.type], p.floor);
    }
}
