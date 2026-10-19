package com.qwerty.elevator.test;

import com.qwerty.elevator.block.DeviceBlock;
import com.qwerty.elevator.block.LandingDoorBlock;
import com.qwerty.elevator.block.MachineBlock;
import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.block.entity.DeviceBlockEntity;
import com.qwerty.elevator.block.entity.LandingDoorBlockEntity;
import com.qwerty.elevator.block.entity.MachineBlockEntity;
import com.qwerty.elevator.elevator.CounterweightSide;
import com.qwerty.elevator.elevator.DriveType;
import com.qwerty.elevator.elevator.ElevatorSettings;
import com.qwerty.elevator.elevator.ElevatorType;
import com.qwerty.elevator.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Builds complete demo elevators (hoistway, landings, doors, fixtures, machine room). Used by tests. */
public final class ShowcaseBuilder {
    private ShowcaseBuilder() {}

    public static final ElevatorType[] TYPES = {
            ElevatorType.PASSENGER, ElevatorType.PANORAMIC, ElevatorType.FREIGHT, ElevatorType.HOME, ElevatorType.PATERNOSTER,
            ElevatorType.VACUUM, ElevatorType.DOUBLE_DECK, ElevatorType.HYDRAULIC, ElevatorType.CONSTRUCTION,
            ElevatorType.PLATFORM_LIFT, ElevatorType.BED};

    public record Site(BlockPos controller, int floors, int[] levels, double doorCenterX, int landingZ, double carCenterZ, int machineY) {
        public int floorY(int i) { return levels[Math.max(0, Math.min(levels.length - 1, i))]; }
    }

    public static List<Site> buildAll(ServerLevel level, BlockPos origin) {
        List<Site> out = new ArrayList<>();
        int x = origin.getX();
        for (ElevatorType t : TYPES) {
            int[] used = new int[1];
            out.add(build(level, new BlockPos(x, origin.getY(), origin.getZ()), t, used));
            x += used[0] + 4;
        }
        return out;
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState st) {
        level.setBlock(new BlockPos(x, y, z), st, 2 | 16);
    }

    public static Site build(ServerLevel level, BlockPos base, ElevatorType type, int[] usedWidth) {
        return build(level, base, type, usedWidth, false);
    }

    public static Site buildVideoReference(ServerLevel level, BlockPos base) {
        return build(level, base, ElevatorType.PASSENGER, new int[1], true);
    }

    private static Site build(ServerLevel level, BlockPos base, ElevatorType type, int[] usedWidth, boolean video) {
        ElevatorSettings s = new ElevatorSettings();
        type.applyDefaults(s);
        s.name = type.id();
        if (video) {
            com.qwerty.elevator.elevator.DesignPreset.VIDEO_CLASSIC.apply(s);
            s.doorHeight = 2.125f;
            s.lowestFloorNumber = -1;
        }
        int floors = video ? 25 : type == ElevatorType.DOUBLE_DECK ? 4 : 3;
        int spacing = type == ElevatorType.DOUBLE_DECK ? 5 : Math.max(5, (int) Math.ceil(s.carHeight + 1.6));
        boolean pat = type == ElevatorType.PATERNOSTER;
        int doorW = switch (type) {
            case PATERNOSTER -> (int) Math.ceil(2 * s.carWidth + 0.25);
            case VACUUM, HOME, PLATFORM_LIFT -> 1;
            case FREIGHT -> 3;
            default -> 2;
        };
        int doorH = type == ElevatorType.PLATFORM_LIFT ? 2 : Math.min(3, Math.max(2, (int) Math.floor(s.carHeight)));
        int bx = base.getX(), by = base.getY(), bz = base.getZ();
        int dx0 = bx + 1, dx1 = bx + doorW;
        double doorCenter = bx + 1 + doorW / 2.0;
        double pCross = s.carWidth + 0.25;
        double half = pat ? pCross / 2 + s.carWidth / 2 : s.carWidth / 2;
        double extraL = 0.7, extraR = 0.9;
        if (s.drive.counterweight && s.counterweight == CounterweightSide.LEFT) extraL += 0.8;
        if (s.drive.counterweight && s.counterweight == CounterweightSide.RIGHT) extraR += 0.8;
        if (s.drive == DriveType.HYDRAULIC_ROPED) extraR += 0.5;
        int minX = (int) Math.floor(doorCenter - half - extraL);
        int maxX = (int) Math.floor(doorCenter + half + extraR);
        double extraD = 0.5;
        if (s.drive.counterweight && s.counterweight == CounterweightSide.BACK) extraD += 0.7;
        if (s.drive == DriveType.RACK_PINION) extraD += 1.0;
        if (s.drive == DriveType.SCREW) extraD += 0.4;
        int doorZ = bz + 2;
        int backZ = (int) Math.floor(doorZ + 1 + s.carDepth + extraD) + 1;
        int[] levels = new int[floors];
        for (int i = 0; i < floors; i++) levels[i] = by + i * spacing;
        int pitFloor = levels[0] - (int) Math.ceil(s.pitDepth) - 1;
        int top = levels[floors - 1] + (int) Math.ceil(s.carHeight + s.overhead) + 2;
        if (pat) {
            pitFloor = levels[0] - (int) Math.ceil(s.carHeight) - 3;
            top = levels[floors - 1] + (int) Math.ceil(2 * s.carHeight) + 3;
        }
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        // hoistway enclosure
        for (int y = pitFloor; y <= top; y++) {
            for (int z = doorZ; z <= backZ; z++) {
                set(level, minX - 1, y, z, glass);
                set(level, maxX + 1, y, z, glass);
            }
            for (int x = minX - 1; x <= maxX + 1; x++) {
                set(level, x, y, backZ, glass);
                set(level, x, y, doorZ, brick);
            }
        }
        for (int x = minX; x <= maxX; x++) for (int z = doorZ + 1; z < backZ; z++) {
            set(level, x, pitFloor, z, brick);
            set(level, x, top, z, brick);
            for (int y = pitFloor + 1; y < top; y++) set(level, x, y, z, Blocks.AIR.defaultBlockState());
        }
        // landings
        for (int i = 0; i < floors; i++) {
            int y = levels[i];
            for (int x = minX - 2; x <= maxX + 3; x++) for (int z = bz - 4; z < doorZ; z++) {
                set(level, x, y - 1, z, floor);
                for (int h = 0; h < 4; h++) set(level, x, y + h, z, Blocks.AIR.defaultBlockState());
            }
        }
        // controller
        BlockPos ctrlPos = new BlockPos(maxX + 3, levels[0], doorZ - 1);
        level.setBlock(ctrlPos, ModBlocks.CONTROLLER.get().defaultBlockState().setValue(com.qwerty.elevator.block.ControllerBlock.FACING, Direction.NORTH), 3);
        ControllerBlockEntity c = (ControllerBlockEntity) level.getBlockEntity(ctrlPos);
        if (c == null) throw new IllegalStateException("controller missing");
        // landing doors
        for (int i = 0; i < floors; i++) {
            int y = levels[i];
            for (int x = dx0; x <= dx1; x++) for (int h = 0; h < doorH; h++) {
                set(level, x, y + h, doorZ, ModBlocks.LANDING_DOOR.get().defaultBlockState().setValue(LandingDoorBlock.FACING, Direction.NORTH));
            }
            for (int x = dx0; x <= dx1; x++) for (int h = 0; h < doorH; h++) {
                LandingDoorBlockEntity.rebuildGroupAround(level, new BlockPos(x, y + h, doorZ));
            }
            if (level.getBlockEntity(new BlockPos(dx0, y, doorZ)) instanceof LandingDoorBlockEntity d) c.linkLandingDoor(d);
            if (!pat) {
                device(level, ModBlocks.HALL_BUTTON.get(), new BlockPos(dx1 + 1, y + 1, doorZ - 1), ctrlPos);
                device(level, ModBlocks.FLOOR_INDICATOR.get(), new BlockPos(dx0, y + doorH, doorZ - 1), ctrlPos);
                if (doorW > 1) device(level, ModBlocks.HALL_LANTERN.get(), new BlockPos(dx1, y + doorH, doorZ - 1), ctrlPos);
                if (i == 0) device(level, ModBlocks.CAPACITY_SIGN.get(), new BlockPos(dx0 - 1, y + 1, doorZ - 1), ctrlPos);
                if (!video && i == 1 && type == ElevatorType.PASSENGER) device(level, ModBlocks.DESTINATION_PANEL.get(), new BlockPos(dx0 - 1, y + 1, doorZ - 1), ctrlPos);
            }
        }
        // machine room / equipment
        int machineY = top + 1;
        int mx = (int) Math.floor(doorCenter);
        int mz = doorZ + 1 + (int) Math.floor(s.carDepth / 2);
        switch (s.drive) {
            case TRACTION_MR -> {
                machine(level, ModBlocks.TRACTION_MACHINE.get(), new BlockPos(mx, machineY, mz), ctrlPos, c);
                machine(level, ModBlocks.GOVERNOR.get(), new BlockPos(maxX, machineY, doorZ + 1), ctrlPos, c);
                machine(level, ModBlocks.MAIN_SWITCH.get(), new BlockPos(minX - 1, machineY, mz), ctrlPos, null);
            }
            case TRACTION_MRL -> {
                machine(level, ModBlocks.GEARLESS_MACHINE.get(), new BlockPos(mx, top - 1, backZ - 1), ctrlPos, c);
                machine(level, ModBlocks.GOVERNOR.get(), new BlockPos(maxX, top - 1, doorZ + 1), ctrlPos, c);
            }
            case HYDRAULIC, HYDRAULIC_ROPED -> machine(level, ModBlocks.HYDRAULIC_UNIT.get(), new BlockPos(maxX + 3, levels[0], doorZ - 3), ctrlPos, c);
            default -> { }
        }
        ElevatorSettings applied = s.copy();
        c.applySettings(applied, null, null);
        usedWidth[0] = maxX - minX + 6;
        return new Site(ctrlPos, floors, levels, doorCenter, doorZ, doorZ + 1.03 + s.carDepth / 2, machineY);
    }

    /**
     * 경사형: a car running along a slope between a lower station (front door, facing north) and an upper
     * station (rear door, facing south). Returns the site; floor 0 is the lower and floor 1 the upper station.
     */
    public static Site buildInclined(ServerLevel level, BlockPos base, int run, int rise) {
        ElevatorSettings s = new ElevatorSettings();
        ElevatorType.INCLINED.applyDefaults(s);
        s.name = "inclined";
        s.carWidth = 2.5f;
        s.carDepth = 2.5f;
        s.carHeight = 2.75f;
        s.speedMpm = 60;
        int bx = base.getX(), by = base.getY(), bz = base.getZ();
        int doorW = 2, doorH = 2;
        int dx0 = bx + 1, dx1 = bx + doorW;
        int lowDoorZ = bz + 2;
        int topDoorZ = lowDoorZ + 1 + (int) Math.ceil(s.carDepth + 0.1) + run;
        int topY = by + rise;
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        for (int x = bx - 2; x <= bx + doorW + 3; x++) {
            for (int z = bz - 4; z < lowDoorZ; z++) set(level, x, by - 1, z, floor);
            for (int z = topDoorZ + 1; z <= topDoorZ + 5; z++) set(level, x, topY - 1, z, floor);
            for (int h = -1; h <= doorH; h++) {
                set(level, x, by + h, lowDoorZ, brick);
                set(level, x, topY + h, topDoorZ, brick);
            }
        }
        BlockPos ctrlPos = new BlockPos(bx + doorW + 2, by, lowDoorZ - 1);
        level.setBlock(ctrlPos, ModBlocks.CONTROLLER.get().defaultBlockState().setValue(com.qwerty.elevator.block.ControllerBlock.FACING, Direction.NORTH), 3);
        ControllerBlockEntity c = (ControllerBlockEntity) level.getBlockEntity(ctrlPos);
        if (c == null) throw new IllegalStateException("controller missing");
        for (int x = dx0; x <= dx1; x++) for (int h = 0; h < doorH; h++) {
            set(level, x, by + h, lowDoorZ, ModBlocks.LANDING_DOOR.get().defaultBlockState().setValue(LandingDoorBlock.FACING, Direction.NORTH));
            set(level, x, topY + h, topDoorZ, ModBlocks.LANDING_DOOR.get().defaultBlockState().setValue(LandingDoorBlock.FACING, Direction.SOUTH));
        }
        for (int x = dx0; x <= dx1; x++) for (int h = 0; h < doorH; h++) {
            LandingDoorBlockEntity.rebuildGroupAround(level, new BlockPos(x, by + h, lowDoorZ));
            LandingDoorBlockEntity.rebuildGroupAround(level, new BlockPos(x, topY + h, topDoorZ));
        }
        c.applySettings(s.copy(), null, null);
        if (level.getBlockEntity(new BlockPos(dx0, by, lowDoorZ)) instanceof LandingDoorBlockEntity d) c.linkLandingDoor(d);
        if (level.getBlockEntity(new BlockPos(dx1, topY, topDoorZ)) instanceof LandingDoorBlockEntity d) c.linkLandingDoor(d);
        device(level, ModBlocks.HALL_BUTTON.get(), new BlockPos(dx1 + 1, by + 1, lowDoorZ - 1), ctrlPos);
        double doorCenter = bx + 1 + doorW / 2.0;
        return new Site(ctrlPos, 2, new int[]{by, topY}, doorCenter, lowDoorZ, lowDoorZ + 1.03 + s.carDepth / 2, topY + 4);
    }

    private static void device(ServerLevel level, Block block, BlockPos pos, BlockPos ctrl) {
        level.setBlock(pos, block.defaultBlockState().setValue(DeviceBlock.FACING, Direction.NORTH), 3);
        if (level.getBlockEntity(pos) instanceof DeviceBlockEntity d) d.link(ctrl);
    }

    private static void machine(ServerLevel level, Block block, BlockPos pos, BlockPos ctrl, ControllerBlockEntity c) {
        level.setBlock(pos, block.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH), 3);
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity m) {
            if (c != null) c.linkMachine(m);
            else m.setController(ctrl);
        }
    }
}
