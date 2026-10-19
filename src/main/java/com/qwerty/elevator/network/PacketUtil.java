package com.qwerty.elevator.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

final class PacketUtil {
    private PacketUtil() {}

    static boolean canReach(ServerPlayer player, BlockPos pos, double range) {
        if (!player.level().isLoaded(pos)) return false;
        return player.position().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= range * range;
    }
}
