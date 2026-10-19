package com.qwerty.elevator.test;

import com.qwerty.elevator.ElevatorMod;
import com.qwerty.elevator.block.LandingDoorBlock;
import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.block.entity.LandingDoorBlockEntity;
import com.qwerty.elevator.elevator.ElevatorSettings;
import com.qwerty.elevator.elevator.ElevatorType;
import com.qwerty.elevator.elevator.DesignPreset;
import com.qwerty.elevator.elevator.CopLayout;
import com.qwerty.elevator.elevator.CopPosition;
import com.qwerty.elevator.elevator.CarGeometry;
import com.qwerty.elevator.elevator.PanelStyle;
import com.qwerty.elevator.elevator.IndicatorStyle;
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

    @GameTest(template = "elevator_test", timeoutTicks = 100)
    public static void lgDesignPersistsAndButtonsMatchRays(GameTestHelper h) {
        ControllerBlockEntity c=build(h,ElevatorType.PASSENGER,3);
        ElevatorSettings settings=c.getSettings().copy();
        int capacity=settings.capacityKg;
        DesignPreset.LG_MGP.apply(settings);
        ElevatorSettings restored=new ElevatorSettings();
        restored.load(settings.save());
        if(!restored.lgInterior || restored.videoInterior || restored.mirror || restored.cctv
                || restored.panelStyle!=PanelStyle.LG_MGP || restored.indicatorStyle!=IndicatorStyle.LG_SEGMENT
                || restored.capacityKg!=capacity) h.fail("LG preset did not persist independently of the original reference");
        c.applySettings(restored,null,null);
        c.debugPlaceAt(0,false);
        List<CopLayout.Mount> mounts=CopLayout.mounts(restored,c.getGeometry(),0);
        int floorButtons=0;
        for(var m:mounts) for(var b:m.buttons()) {
            Vec3 face=m.point(b.cx(),b.cy(),0);
            var hit=CopLayout.hit(mounts,face.add(m.normal().scale(.35)),m.normal().scale(-1),1);
            if(hit==null || hit.type()!=b.type() || hit.floor()!=b.floor()) h.fail("LG visible button cannot be clicked");
            if(b.type()==CopLayout.ButtonType.FLOOR) floorButtons++;
        }
        if(floorButtons!=3) h.fail("LG panel lost a registered floor");
        DesignPreset.VIDEO_CLASSIC.apply(restored);
        if(restored.lgInterior || !restored.videoInterior) h.fail("LG finish leaked into the older reference preset");
        h.succeed();
    }

    @GameTest(template = "elevator_test", timeoutTicks = 100)
    public static void videoDisplayRollAndArrowTiming(GameTestHelper h) {
        var display = new com.qwerty.elevator.elevator.VideoDisplayState();
        display.update("1", 1, 0);
        if (display.progress(0) != 1) h.fail("initial floor must appear immediately");
        display.update("2", 1, 20);
        if (display.incomingRows(20) != -8 || display.outgoingRows(20) != 0) h.fail("up roll starts below the window");
        display.update("2", 1, 25);
        if (display.progress(25) != .5f || display.incomingRows(25) != -4 || display.outgoingRows(25) != 4)
            h.fail("render updates restarted or reversed the roll");
        if (display.progress(30) != 1 || display.incomingRows(30) != 0) h.fail("roll did not finish");
        display.update("B1", -1, 40);
        if (display.incomingRows(40) != 8 || display.incomingRows(45) != 4 || display.outgoingRows(45) != -4)
            h.fail("descending floor must enter from above");
        if (com.qwerty.elevator.elevator.VideoDisplayState.arrowRows(0, 1, true) != -8
                || com.qwerty.elevator.elevator.VideoDisplayState.arrowRows(6, 1, true) != 0
                || com.qwerty.elevator.elevator.VideoDisplayState.arrowRows(15, 1, true) != 0
                || com.qwerty.elevator.elevator.VideoDisplayState.arrowRows(16, -1, true) != 8
                || com.qwerty.elevator.elevator.VideoDisplayState.arrowRows(16, 1, false) != 0)
            h.fail("arrow reveal/hold/stop timing is inconsistent");
        h.succeed();
    }

    @GameTest(template = "elevator_test", timeoutTicks = 100)
    public static void videoMomentaryButtonsReleaseAndSynchronize(GameTestHelper h) {
        ControllerBlockEntity c = build(h, ElevatorType.PASSENGER, 3);
        var settings = c.getSettings().copy();
        DesignPreset.VIDEO_CLASSIC.apply(settings);
        c.applySettings(settings, null, null);
        h.runAfterDelay(20, () -> c.handleCarButton(h.makeMockPlayer(), CopLayout.ButtonType.OPEN, -1));
        h.runAfterDelay(23, () -> {
            var cars = h.getLevel().getEntitiesOfClass(ElevatorCarEntity.class,
                    new AABB(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(16, 28, 16))));
            if (cars.size() != 1) h.fail("missing car for button sync check");
            var st = com.qwerty.elevator.entity.CarStatus.load(cars.get(0).getStatus().save());
            if (!st.pressed(CopLayout.ButtonType.OPEN.ordinal(), -1)) h.fail("open button press did not reach car status");
        });
        h.runAfterDelay(32, () -> {
            var cars = h.getLevel().getEntitiesOfClass(ElevatorCarEntity.class,
                    new AABB(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(16, 28, 16))));
            if (cars.isEmpty() || cars.get(0).getStatus().pressedType != -1) h.fail("momentary button did not release");
            h.succeed();
        });
    }

    @GameTest(template = "elevator_test", timeoutTicks = 100)
    public static void videoHallButtonZonesMatchTheTallPanel(GameTestHelper h) {
        ControllerBlockEntity c = build(h, ElevatorType.PASSENGER, 3);
        var settings = c.getSettings().copy();
        DesignPreset.VIDEO_CLASSIC.apply(settings);
        c.applySettings(settings, null, null);
        c.debugPlaceAt(2, false);
        BlockPos relative = new BlockPos(4, 9, 1);
        h.setBlock(relative, ModBlocks.HALL_BUTTON.get().defaultBlockState());
        BlockPos pos = h.absolutePos(relative);
        var be = (com.qwerty.elevator.block.entity.DeviceBlockEntity) h.getLevel().getBlockEntity(pos);
        be.link(c.getBlockPos());
        var state = be.getBlockState();
        if (state.getShape(h.getLevel(), pos).max(Direction.Axis.Y) < 1.4) h.fail("tall panel shape does not cover the display");
        var block = (com.qwerty.elevator.block.DeviceBlock) state.getBlock();
        for (double y : new double[]{.405, .205}) {
            block.use(state, h.getLevel(), pos, h.makeMockPlayer(), net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(new Vec3(pos.getX()+.5,pos.getY()+y,pos.getZ()+.94),Direction.NORTH,pos,false));
        }
        if (!c.hasHallCall(1, 1) || !c.hasHallCall(1, -1)) h.fail("upper/lower reference buttons do not register up/down calls");
        h.succeed();
    }

    /** Builds landings on the north side (z 0..1) and a 3×3 hoistway (x 1..3, z 3..5); returns the controller. */
    private static ControllerBlockEntity build(GameTestHelper h, ElevatorType type, int floors) {
        BlockPos ctrlPos = new BlockPos(10, 1, 10);
        h.setBlock(ctrlPos, ModBlocks.CONTROLLER.get().defaultBlockState());
        ControllerBlockEntity c = (ControllerBlockEntity) h.getBlockEntity(ctrlPos);
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
            LandingDoorBlockEntity door = (LandingDoorBlockEntity) h.getBlockEntity(new BlockPos(1, y, 2));
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
            LandingDoorBlockEntity top = (LandingDoorBlockEntity) h.getBlockEntity(new BlockPos(1, LEVELS[2], 2));
            LandingDoorBlockEntity master = top.masterEntity();
            if (master == null || master.getOpen() < 0.99f) h.fail("top landing door not open");
            if (!h.getBlockState(new BlockPos(2, LEVELS[2] + 1, 2)).getValue(LandingDoorBlock.OPEN)) h.fail("door block state not open");
            List<ElevatorCarEntity> cars = h.getLevel().getEntitiesOfClass(ElevatorCarEntity.class, new AABB(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(16, 28, 16))));
            if (cars.size() != 1) h.fail("expected one car entity, found " + cars.size());
        });
    }

    @GameTest(template = "elevator_test", timeoutTicks = 100)
    public static void videoPanelPersistsAndEveryButtonCanBePressed(GameTestHelper h) {
        ControllerBlockEntity c = build(h, ElevatorType.PASSENGER, 3);
        ElevatorSettings s = c.getSettings().copy();
        DesignPreset.VIDEO_CLASSIC.apply(s);
        ElevatorSettings restored = s.copy();
        if (!restored.videoInterior || restored.panelStyle != PanelStyle.VIDEO_CLASSIC
                || restored.indicatorStyle != IndicatorStyle.VIDEO_AMBER) h.fail("video design lost in NBT round trip");
        var legacy = s.save();
        legacy.remove("videoInterior");
        ElevatorSettings old = new ElevatorSettings();
        old.load(legacy);
        if (old.videoInterior) h.fail("legacy settings unexpectedly enable video interior");
        // Exercise the reference B1..24 three-column layout, including fallback side mounting.
        CarGeometry g = CarGeometry.load(c.getGeometry().save());
        g.floorNames = new String[25];
        g.floorDeck = new int[25];
        g.floorService = new boolean[25];
        java.util.Arrays.fill(g.floorService, true);
        for (int i = 0; i < 25; i++) g.floorNames[i] = i == 0 ? "B1" : Integer.toString(i);
        for (CopPosition position : CopPosition.values()) {
            restored.copPosition = position;
            var mounts = CopLayout.mounts(restored, g, 0);
            if (mounts.isEmpty()) h.fail("no reference panel mounts");
            for (var m : mounts) {
                if (m.buttons().stream().filter(b -> b.type() == CopLayout.ButtonType.FLOOR).count() != 25)
                    h.fail("missing floor buttons");
                for (var b : m.buttons()) {
                    if (b.cx() - b.size() / 2 < 0 || b.cx() + b.size() / 2 > m.width()
                            || b.cy() - b.size() / 2 < 0 || b.cy() + b.size() / 2 > m.height())
                        h.fail("button outside plate: " + b);
                    Vec3 from = m.point(b.cx(), b.cy(), 0.5);
                    var hit = CopLayout.hit(List.of(m), from, m.normal().scale(-1), 1);
                    if (!b.equals(hit)) h.fail("button hit region disagrees with rendering: " + b);
                }
            }
        }
        DesignPreset.MODERN.apply(restored);
        if (restored.videoInterior || restored.panelStyle == PanelStyle.VIDEO_CLASSIC)
            h.fail("switching presets retained video geometry");
        h.succeed();
    }

    @GameTest(template = "elevator_test", timeoutTicks = 1200)
    public static void videoDesignTravelsAndSyncsToCar(GameTestHelper h) {
        ControllerBlockEntity c = build(h, ElevatorType.PASSENGER, 3);
        ElevatorSettings s = c.getSettings().copy();
        DesignPreset.VIDEO_CLASSIC.apply(s);
        c.applySettings(s, null, null);
        h.runAfterDelay(20, () -> c.registerCarCall(2, null));
        h.succeedWhen(() -> {
            if (c.getDisplayStop() != 2 || c.getPhase() == ControllerBlockEntity.Phase.MOVING)
                h.fail("reference car has not reached its destination");
            var cars = h.getLevel().getEntitiesOfClass(ElevatorCarEntity.class,
                    new AABB(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(16, 28, 16))));
            if (cars.size() != 1 || !cars.get(0).getSettings().videoInterior
                    || cars.get(0).getSettings().indicatorStyle != IndicatorStyle.VIDEO_AMBER)
                h.fail("reference settings not synchronized to car");
            if (c.getDoor() < 0.99f) h.fail("reference car doors not open at arrival");
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
