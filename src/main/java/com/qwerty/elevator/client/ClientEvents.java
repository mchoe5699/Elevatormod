package com.qwerty.elevator.client;

import com.qwerty.elevator.ElevatorMod;
import com.qwerty.elevator.elevator.CarCollision;
import com.qwerty.elevator.elevator.CarGeometry;
import com.qwerty.elevator.elevator.CopLayout;
import com.qwerty.elevator.entity.ElevatorCarEntity;
import com.qwerty.elevator.network.CarButtonPacket;
import com.qwerty.elevator.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Client side: carries the local player in cars and handles clicks on car operating panels. */
@Mod.EventBusSubscriber(modid = ElevatorMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final Map<Integer, Double> LAST_S = new HashMap<>();

    private ClientEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.player.level().isClientSide) return;
        if (!(event.player instanceof LocalPlayer player) || player != Minecraft.getInstance().player) return;
        Entity mover = player;
        if (player.isPassenger()) {
            Entity root = player.getRootVehicle();
            if (!root.isControlledByLocalInstance()) return;
            mover = root;
        }
        Level level = player.level();
        long gt = level.getGameTime();
        List<ElevatorCarEntity> cars = level.getEntitiesOfClass(ElevatorCarEntity.class, mover.getBoundingBox().inflate(3));
        for (ElevatorCarEntity car : cars) {
            CarGeometry g = car.getGeometry();
            if (!g.valid()) continue;
            double sNow = car.predictedS(gt);
            Double prev = LAST_S.get(car.getId());
            double sPrev = prev == null ? sNow : prev;
            LAST_S.put(car.getId(), sNow);
            CarCollision.Result r = CarCollision.apply(mover, g, car.getSettings(), car.cabins(sPrev), car.cabins(sNow), car.doors());
            if (r == CarCollision.Result.INSIDE || r == CarCollision.Result.ROOF) {
                player.fallDistance = 0;
            }
        }
        if (gt % 200 == 0) LAST_S.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1f);
        double blockDist = Double.MAX_VALUE;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            blockDist = mc.hitResult.getLocation().distanceTo(eye);
        }
        List<ElevatorCarEntity> cars = mc.level.getEntitiesOfClass(ElevatorCarEntity.class, p.getBoundingBox().inflate(8));
        for (ElevatorCarEntity car : cars) {
            CarGeometry g = car.getGeometry();
            if (!g.valid()) continue;
            for (CarGeometry.Cabin cabin : car.cabins(car.getS())) {
                Vec3 from = g.toLocal(cabin.anchor(), eye);
                Vec3 dir = g.dirToLocal(look);
                List<CopLayout.Mount> mounts = CopLayout.mounts(car.getSettings(), g, cabin.deck());
                double reach = Math.min(4.0, blockDist + 0.05);
                CopLayout.CopButton b = CopLayout.hit(mounts, from, dir, reach);
                if (b != null && !p.isShiftKeyDown()) {
                    ModNetwork.sendToServer(new CarButtonPacket(car.getId(), b.type().ordinal(), b.floor()));
                    event.setCanceled(true);
                    event.setSwingHand(true);
                    return;
                }
                if (p.isShiftKeyDown() && CopLayout.hitsPlate(mounts, from, dir, reach)) {
                    ClientHooks.openCarPanel(car, cabin.deck());
                    event.setCanceled(true);
                    event.setSwingHand(false);
                    return;
                }
                if (car.getSettings().storage) {
                    AABB box = g.cabinBox(cabin.anchor(), car.getSettings());
                    Optional<Vec3> hit = box.clip(eye, eye.add(look.scale(4)));
                    if (hit.isPresent() && hit.get().distanceTo(eye) <= blockDist + 0.6) {
                        ModNetwork.sendToServer(new CarButtonPacket(car.getId(), CarButtonPacket.STORAGE, -1));
                        event.setCanceled(true);
                        event.setSwingHand(true);
                        return;
                    }
                }
            }
        }
    }

    @SuppressWarnings("unused")
    private static boolean isBlock(HitResult r) {
        return r instanceof BlockHitResult;
    }
}
