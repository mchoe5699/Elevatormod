package com.qwerty.elevator.client.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/** Material and colour presets offered in the GUIs. Any block id can also be typed in. */
public final class Presets {
    private Presets() {}

    public static final List<String> MATERIALS = List.of(
            "qwertys_elevator:stainless_hairline", "qwertys_elevator:stainless_mirror", "qwertys_elevator:stainless_etching",
            "qwertys_elevator:gold_mirror", "qwertys_elevator:black_mirror", "qwertys_elevator:wood_panel_oak",
            "qwertys_elevator:wood_panel_walnut", "qwertys_elevator:white_panel", "qwertys_elevator:painted_steel",
            "qwertys_elevator:checker_plate", "qwertys_elevator:hazard_stripe", "qwertys_elevator:terrazzo",
            "qwertys_elevator:granite_tile", "qwertys_elevator:marble_tile", "qwertys_elevator:rubber_floor",
            "qwertys_elevator:car_glass", "qwertys_elevator:mesh_panel", "qwertys_elevator:ceiling_light_panel",
            "minecraft:iron_block", "minecraft:quartz_block", "minecraft:smooth_stone", "minecraft:polished_andesite",
            "minecraft:polished_deepslate", "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:dark_oak_planks",
            "minecraft:cherry_planks", "minecraft:white_concrete", "minecraft:light_gray_concrete", "minecraft:black_concrete",
            "minecraft:red_concrete", "minecraft:blue_concrete", "minecraft:glass", "minecraft:light_blue_stained_glass",
            "minecraft:tinted_glass", "minecraft:copper_block", "minecraft:gold_block", "minecraft:white_wool");

    public static final int[] LIGHT_COLORS = {0xFFF4E0, 0xF4FAFF, 0xFFFFFF, 0xFFD9A0, 0xBFD8FF, 0xFFB070, 0xD8FFE0, 0xFFC8E8};

    public static String cycleMaterial(String current, int delta) {
        int i = MATERIALS.indexOf(current);
        if (i < 0) i = 0;
        else i = Math.floorMod(i + delta, MATERIALS.size());
        return MATERIALS.get(i);
    }

    public static Component materialName(String id) {
        if (id == null || id.isEmpty()) return Component.translatable("gui.qwertys_elevator.auto");
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Block b = rl == null ? null : ForgeRegistries.BLOCKS.getValue(rl);
        if (b == null || rl == null || !ForgeRegistries.BLOCKS.containsKey(rl)) return Component.literal(id);
        return b.getName();
    }

    public static boolean validBlock(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl != null && ForgeRegistries.BLOCKS.containsKey(rl);
    }

    public static int cycleLight(int current, int delta) {
        int idx = 0;
        for (int i = 0; i < LIGHT_COLORS.length; i++) if (LIGHT_COLORS[i] == current) idx = i;
        return LIGHT_COLORS[Math.floorMod(idx + delta, LIGHT_COLORS.length)];
    }

    public static Component lightName(int color) {
        for (int i = 0; i < LIGHT_COLORS.length; i++) {
            if (LIGHT_COLORS[i] == color) return Component.translatable("gui.qwertys_elevator.light." + i);
        }
        return Component.literal(String.format("#%06X", color));
    }
}
