package com.qwerty.elevator.network;

import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.elevator.Choice;
import com.qwerty.elevator.elevator.ElevatorMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Buttons on the controller screen: operating mode, inspection jog, test calls, car respawn. */
public record ControllerActionPacket(BlockPos pos, String action, int arg) {
    public static void encode(ControllerActionPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeUtf(p.action, 64);
        buf.writeVarInt(p.arg);
    }

    public static ControllerActionPacket decode(FriendlyByteBuf buf) {
        return new ControllerActionPacket(buf.readBlockPos(), buf.readUtf(64), buf.readVarInt());
    }

    public static void handle(ControllerActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null || !PacketUtil.canReach(player, p.pos, 12)) return;
        if (!(player.level().getBlockEntity(p.pos) instanceof ControllerBlockEntity c)) return;
        switch (p.action) {
            case "mode" -> {
                ElevatorMode[] modes = ElevatorMode.values();
                if (p.arg >= 0 && p.arg < modes.length) {
                    ElevatorMode m = modes[p.arg];
                    if (m == ElevatorMode.FIRE_RECALL) c.setFireRecall(true, c.getSettings().fireFloor);
                    else if (m != ElevatorMode.STOPPED && m != ElevatorMode.FIRE_SERVICE) {
                        if (c.getMode() == ElevatorMode.FIRE_RECALL || c.getMode() == ElevatorMode.FIRE_SERVICE) c.setFireRecall(false, -1);
                        c.setMode(m);
                    }
                }
            }
            case "jog" -> c.jog(Integer.signum(p.arg));
            case "call" -> c.registerCarCall(p.arg, player);
            case "hall_up" -> c.registerHallCall(p.arg, 1);
            case "hall_down" -> c.registerHallCall(p.arg, -1);
            case "respawn" -> c.respawnCar();
            case "open" -> c.pressOpen();
            case "close" -> c.pressClose();
            case "power" -> c.setMainPower(p.arg != 0);
            default -> { }
        }
    }

    public static ElevatorMode modeById(String id) {
        return Choice.byId(ElevatorMode.class, id, ElevatorMode.NORMAL);
    }
}
