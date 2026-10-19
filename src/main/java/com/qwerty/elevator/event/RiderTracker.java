package com.qwerty.elevator.event;

import com.qwerty.elevator.ElevatorMod;
import com.qwerty.elevator.elevator.CarGeometry;
import com.qwerty.elevator.entity.ElevatorCarEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which players are riding an elevator, so the server does not kick them for "flying"
 * while the car carries them through an empty hoistway, and resets fall damage.
 */
@Mod.EventBusSubscriber(modid = ElevatorMod.MODID)
public final class RiderTracker {
    private static final Map<UUID, Long> RIDING = new ConcurrentHashMap<>();

    private RiderTracker() {}

    public static boolean isRiding(Entity e) {
        Long t = RIDING.get(e.getUUID());
        return t != null && e.level().getGameTime() - t < 40;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        AABB area = player.getBoundingBox().inflate(4);
        List<ElevatorCarEntity> cars = player.level().getEntitiesOfClass(ElevatorCarEntity.class, area);
        for (ElevatorCarEntity car : cars) {
            CarGeometry g = car.getGeometry();
            if (!g.valid()) continue;
            for (CarGeometry.Cabin c : car.cabins(car.getS())) {
                AABB box = g.cabinBox(c.anchor(), car.getSettings()).inflate(0.6, 1.2, 0.6);
                if (box.intersects(player.getBoundingBox())) {
                    RIDING.put(player.getUUID(), player.level().getGameTime());
                    player.fallDistance = 0;
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        RIDING.remove(event.getEntity().getUUID());
    }
}
