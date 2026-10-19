package com.qwerty.elevator.test;

import com.qwerty.elevator.ElevatorMod;
import com.qwerty.elevator.block.LandingDoorBlock;
import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.block.entity.LandingDoorBlockEntity;
import com.qwerty.elevator.elevator.ElevatorSettings;
import com.qwerty.elevator.elevator.ElevatorType;
import com.qwerty.elevator.entity.ElevatorCarEntity;
import com.qwerty.elevator.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Server-side game tests (run with {@code gradlew runGameTestServer}). They build a small three-stop
 * hoistway, link it and check that the car really travels, opens the landing doors and carries entities.
 */
@GameTestHolder(ElevatorMod.MODID)
@PrefixGameTestTemplate(false)
public class ElevatorGameTests {
    private static final int[] LEVELS = {2, 8, 14};

    /** Builds landings on the north side (z 0..1) and a 3×3 hoistway (x 1..3, z 3..5); returns the controller. */
    private static ControllerBlockEntity build(GameTestHelper h, ElevatorType type, int floors) {
        BlockPos ctrlPos = new BlockPos(10, 1, 10);
        h.setBlock(ctrlPos, ModBlocks.CONTROLLER.get().defaultBlockState());
        ControllerBlockEntity c = h.getBlockEntity(ctrlPos);
        ElevatorSettings s = c.getSettings().copy();
        type.applyDefaults(s);
        s.carWidth = 3f;
        s.carDepth = 2.75f;
        s.carHeight = 2.75f;
        s.pitDepth = 1f;
        s.overhead = 1f;
        s.speedMpm = 120;
        s.doorOpenSec = 1.5f;
        s.doorMoveSec = 0.5f;
        c.applySettings(s, null, null);
        for (int i = 0; i < floors; i++) {
            int y = LEVELS[i];
            for (int x = 0; x <= 4; x++) for (int z = 0; z <= 2; z++) h.setBlock(new BlockPos(x, y - 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
            for (int x = 1; x <= 2; x++) {
                for (int dy = 0; dy < 2; dy++) {
                    BlockPos p = new BlockPos(x, y + dy, 2);
                    h.setBlock(p, ModBlocks.LANDING_DOOR.get().defaultBlockState().setValue(LandingDoorBlock.FACING, Direction.NORTH));
                }
            }
            for (int x = 1; x <= 2; x++) for (int dy = 0; dy < 2; dy++) {
                LandingDoorBlockEntity.rebuildGroupAround(h.getLevel(), h.absolutePos(new BlockPos(x, y + dy, 2)));
            }
            LandingDoorBlockEntity door = h.getBlockEntity(new BlockPos(1, y, 2));
            c.linkLandingDoor(door);
        }
        return c;
    }

    @GameTest(template = "elevator_test", timeoutTicks = 1200)
    public static void carTravelsAndOpensDoors(GameTestHelper h) {
        ControllerBlockEntity c = build(h, ElevatorType.PASSENGER, 3);
        h.runAfterDelay(20, () -> {
            if (c.floorCount() != 3) h.fail("expected 3 floors, got " + c.floorCount());
            c.registerCarCall(2, null);
        });
        h.succeedWhen(() -> {
            if (c.getDisplayStop() != 2) h.fail("car not at top yet: stop " + c.getDisplayStop() + " s=" + c.getPosition());
            LandingDoorBlockEntity top = h.getBlockEntity(new BlockPos(1, LEVELS[2], 2));
            LandingDoorBlockEntity master = top.masterEntity();
            if (master == null || master.getOpen() < 0.99f) h.fail("top landing door not open");
            if (!h.getBlockState(new BlockPos(2, LEVELS[2] + 1, 2)).getValue(LandingDoorBlock.OPEN)) h.fail("door block state not open");
            List<ElevatorCarEntity> cars = h.getLevel().getEntitiesOfClass(ElevatorCarEntity.class, new AABB(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(16, 28, 16))));
            if (cars.size() != 1) h.fail("expected one car entity, found " + cars.size());
        });
    }

    @GameTest(template = "elevator_test", timeoutTicks = 1600)
    public static void carriesEntities(GameTestHelper h) {
        ControllerBlockEntity c = build(h, ElevatorType.PASSENGER, 3);
        ArmorStand[] stand = new ArmorStand[1];
        h.runAfterDelay(40, () -> {
            Vec3 inside = h.absoluteVec(new Vec3(1.5, LEVELS[0] + 0.2, 4.2));
            ArmorStand a = new ArmorStand(EntityType.ARMOR_STAND, h.getLevel());
            a.moveTo(inside.x, inside.y, inside.z);
            h.getLevel().addFreshEntity(a);
            stand[0] = a;
        });
        h.runAfterDelay(80, () -> c.registerCarCall(2, null));
        h.succeedWhen(() -> {
            if (stand[0] == null) h.fail("no stand yet");
            if (c.getDisplayStop() != 2 || c.getPhase() == ControllerBlockEntity.Phase.MOVING) h.fail("car still travelling");
            double expected = h.absolutePos(BlockPos.ZERO).getY() + LEVELS[2];
            if (Math.abs(stand[0].getY() - expected) > 0.3) h.fail("armor stand at y=" + stand[0].getY() + " expected " + expected);
        });
    }

    @GameTest(template = "elevator_test", timeoutTicks = 600)
    public static void paternosterKeepsMoving(GameTestHelper h) {
        ControllerBlockEntity c = build(h, ElevatorType.PATERNOSTER, 2);
        double[] start = new double[1];
        h.runAfterDelay(40, () -> start[0] = c.getPosition());
        h.runAfterDelay(200, () -> {
            if (Math.abs(c.getPosition() - start[0]) < 0.5) h.fail("paternoster did not move: " + c.getPosition());
            h.succeed();
        });
    }

    @GameTest(template = "elevator_test", timeoutTicks = 1200)
    public static void hallCallAndFireRecall(GameTestHelper h) {
        ControllerBlockEntity c = build(h, ElevatorType.EMERGENCY, 3);
        h.runAfterDelay(30, () -> c.registerHallCall(1, 1));
        h.runAfterDelay(160, () -> c.setFireRecall(true, 0));
        h.succeedWhen(() -> {
            if (c.getMode() != com.qwerty.elevator.elevator.ElevatorMode.FIRE_RECALL) h.fail("not in fire recall");
            if (c.getDisplayStop() != 0 || c.getPhase() == ControllerBlockEntity.Phase.MOVING) h.fail("not back at recall floor");
            if (c.getDoor() < 0.99f) h.fail("doors not open at recall floor");
        });
    }
}
