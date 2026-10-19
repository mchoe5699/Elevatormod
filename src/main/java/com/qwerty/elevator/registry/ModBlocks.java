package com.qwerty.elevator.registry;

import com.qwerty.elevator.ElevatorMod;
import com.qwerty.elevator.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ElevatorMod.MODID);

    /** Blocks in creative-tab order. */
    public static final List<RegistryObject<? extends Block>> ORDER = new ArrayList<>();
    /** Interior / building material blocks (also offered as car materials). */
    public static final List<RegistryObject<? extends Block>> MATERIALS = new ArrayList<>();

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties device() {
        return metal().strength(1.5f, 6.0f).noOcclusion();
    }

    // ------------------------------------------------------------ 제어/기계실
    public static final RegistryObject<ControllerBlock> CONTROLLER = reg("elevator_controller", () -> new ControllerBlock(metal().noOcclusion()));
    public static final RegistryObject<MachineBlock> TRACTION_MACHINE = reg("traction_machine", () -> new MachineBlock(metal().noOcclusion(), MachineBlock.Kind.GEARED));
    public static final RegistryObject<MachineBlock> GEARLESS_MACHINE = reg("gearless_machine", () -> new MachineBlock(metal().noOcclusion(), MachineBlock.Kind.GEARLESS));
    public static final RegistryObject<MachineBlock> GOVERNOR = reg("governor", () -> new MachineBlock(metal().noOcclusion(), MachineBlock.Kind.GOVERNOR));
    public static final RegistryObject<MachineBlock> HYDRAULIC_UNIT = reg("hydraulic_power_unit", () -> new MachineBlock(metal().noOcclusion(), MachineBlock.Kind.HYDRAULIC));
    public static final RegistryObject<MachineBlock> DEFLECTOR_SHEAVE = reg("deflector_sheave", () -> new MachineBlock(metal().noOcclusion(), MachineBlock.Kind.SHEAVE));
    public static final RegistryObject<MachineBlock> MAIN_SWITCH = reg("main_switch", () -> new MachineBlock(device(), MachineBlock.Kind.MAIN_SWITCH));

    // ------------------------------------------------------------ 승강로/피트
    public static final RegistryObject<PartBlock> GUIDE_RAIL = reg("guide_rail", () -> new PartBlock(metal().noOcclusion(), PartBlock.Kind.GUIDE_RAIL));
    public static final RegistryObject<PartBlock> RAIL_BRACKET = reg("rail_bracket", () -> new PartBlock(metal().noOcclusion(), PartBlock.Kind.RAIL_BRACKET));
    public static final RegistryObject<PartBlock> SPRING_BUFFER = reg("spring_buffer", () -> new PartBlock(metal().noOcclusion(), PartBlock.Kind.SPRING_BUFFER));
    public static final RegistryObject<PartBlock> OIL_BUFFER = reg("oil_buffer", () -> new PartBlock(metal().noOcclusion(), PartBlock.Kind.OIL_BUFFER));
    public static final RegistryObject<PartBlock> LIMIT_SWITCH = reg("limit_switch", () -> new PartBlock(device(), PartBlock.Kind.LIMIT_SWITCH));
    public static final RegistryObject<PartBlock> LEVELING_VANE = reg("leveling_vane", () -> new PartBlock(device(), PartBlock.Kind.LEVELING_VANE));
    public static final RegistryObject<PartBlock> HOISTWAY_LIGHT = reg("hoistway_light", () -> new PartBlock(device().lightLevel(s -> 12), PartBlock.Kind.HOISTWAY_LIGHT));
    public static final RegistryObject<PitLadderBlock> PIT_LADDER = reg("pit_ladder", () -> new PitLadderBlock(metal().noOcclusion().strength(1.0f)));
    public static final RegistryObject<DeviceBlock> PIT_STOP_SWITCH = reg("pit_stop_switch", () -> new DeviceBlock(device(), DeviceBlock.Kind.PIT_STOP));

    // ------------------------------------------------------------ 승강장
    public static final RegistryObject<LandingDoorBlock> LANDING_DOOR = reg("landing_door", () -> new LandingDoorBlock(metal().noOcclusion().dynamicShape()));
    public static final RegistryObject<DeviceBlock> HALL_BUTTON = reg("hall_button", () -> new DeviceBlock(device(), DeviceBlock.Kind.HALL_BUTTON));
    public static final RegistryObject<DeviceBlock> HALL_LANTERN = reg("hall_lantern", () -> new DeviceBlock(device(), DeviceBlock.Kind.HALL_LANTERN));
    public static final RegistryObject<DeviceBlock> FLOOR_INDICATOR = reg("floor_indicator", () -> new DeviceBlock(device(), DeviceBlock.Kind.INDICATOR));
    public static final RegistryObject<DeviceBlock> WIDE_INDICATOR = reg("wide_indicator", () -> new DeviceBlock(device(), DeviceBlock.Kind.WIDE_INDICATOR));
    public static final RegistryObject<DeviceBlock> DESTINATION_PANEL = reg("destination_panel", () -> new DeviceBlock(device(), DeviceBlock.Kind.DESTINATION));
    public static final RegistryObject<DeviceBlock> FIRE_SWITCH = reg("fire_switch", () -> new DeviceBlock(device(), DeviceBlock.Kind.FIRE_SWITCH));
    public static final RegistryObject<DeviceBlock> CAPACITY_SIGN = reg("capacity_sign", () -> new DeviceBlock(device(), DeviceBlock.Kind.SIGN));

    // ------------------------------------------------------------ 내장재
    public static final RegistryObject<Block> STAINLESS_HAIRLINE = mat("stainless_hairline", MapColor.METAL);
    public static final RegistryObject<Block> STAINLESS_MIRROR = mat("stainless_mirror", MapColor.METAL);
    public static final RegistryObject<Block> STAINLESS_ETCHING = mat("stainless_etching", MapColor.METAL);
    public static final RegistryObject<Block> GOLD_MIRROR = mat("gold_mirror", MapColor.GOLD);
    public static final RegistryObject<Block> BLACK_MIRROR = mat("black_mirror", MapColor.COLOR_BLACK);
    public static final RegistryObject<Block> WOOD_PANEL_OAK = matWood("wood_panel_oak", MapColor.WOOD);
    public static final RegistryObject<Block> WOOD_PANEL_WALNUT = matWood("wood_panel_walnut", MapColor.COLOR_BROWN);
    public static final RegistryObject<Block> WHITE_PANEL = mat("white_panel", MapColor.SNOW);
    public static final RegistryObject<Block> PAINTED_STEEL = mat("painted_steel", MapColor.COLOR_LIGHT_GRAY);
    public static final RegistryObject<Block> CHECKER_PLATE = mat("checker_plate", MapColor.METAL);
    public static final RegistryObject<Block> HAZARD_STRIPE = mat("hazard_stripe", MapColor.COLOR_YELLOW);
    public static final RegistryObject<Block> TERRAZZO = matStone("terrazzo", MapColor.QUARTZ);
    public static final RegistryObject<Block> GRANITE_TILE = matStone("granite_tile", MapColor.COLOR_GRAY);
    public static final RegistryObject<Block> MARBLE_TILE = matStone("marble_tile", MapColor.QUARTZ);
    public static final RegistryObject<Block> RUBBER_FLOOR = matStone("rubber_floor", MapColor.COLOR_GRAY);
    public static final RegistryObject<Block> CEILING_LIGHT = regMat("ceiling_light_panel",
            () -> new Block(metal().strength(1.0f).lightLevel(s -> 15).sound(SoundType.GLASS)));
    public static final RegistryObject<Block> CAR_GLASS = regMat("car_glass",
            () -> new net.minecraft.world.level.block.GlassBlock(BlockBehaviour.Properties.of().strength(0.6f).sound(SoundType.GLASS)
                    .noOcclusion().isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final RegistryObject<Block> MESH_PANEL = regMat("mesh_panel",
            () -> new MeshPanelBlock(metal().noOcclusion().strength(2.0f)));

    private ModBlocks() {}

    private static <T extends Block> RegistryObject<T> reg(String name, Supplier<T> sup) {
        RegistryObject<T> ro = BLOCKS.register(name, sup);
        ORDER.add(ro);
        return ro;
    }

    private static <T extends Block> RegistryObject<T> regMat(String name, Supplier<T> sup) {
        RegistryObject<T> ro = reg(name, sup);
        MATERIALS.add(ro);
        return ro;
    }

    private static RegistryObject<Block> mat(String name, MapColor color) {
        return regMat(name, () -> new Block(metal().mapColor(color)));
    }

    private static RegistryObject<Block> matWood(String name, MapColor color) {
        return regMat(name, () -> new Block(BlockBehaviour.Properties.of().mapColor(color).strength(2.0f, 3.0f).sound(SoundType.WOOD)));
    }

    private static RegistryObject<Block> matStone(String name, MapColor color) {
        return regMat(name, () -> new Block(BlockBehaviour.Properties.of().mapColor(color).strength(1.5f, 6.0f).sound(SoundType.STONE)));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
