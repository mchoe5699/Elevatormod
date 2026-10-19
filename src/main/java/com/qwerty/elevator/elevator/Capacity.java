package com.qwerty.elevator.elevator;

/**
 * Rated load vs. available car area (EN 81-20 Table 6, also used by the Korean safety standard).
 * The car area may not exceed the value for its rated load, so for a given area we return
 * the smallest rated load whose permitted area is at least that big.
 */
public final class Capacity {
    private static final int[] LOAD = {100, 180, 225, 300, 375, 400, 450, 525, 600, 630, 675, 750, 800, 825, 900, 975, 1000, 1050, 1125, 1200, 1250, 1275, 1350, 1425, 1500, 1600, 2000, 2500};
    private static final double[] AREA = {0.37, 0.58, 0.70, 0.90, 1.10, 1.17, 1.30, 1.45, 1.60, 1.66, 1.75, 1.90, 2.00, 2.05, 2.20, 2.35, 2.40, 2.50, 2.65, 2.80, 2.90, 2.95, 3.10, 3.25, 3.40, 3.56, 4.20, 5.00};

    private Capacity() {}

    public static int ratedLoadForArea(double area) {
        if (area <= AREA[0]) return LOAD[0];
        for (int i = 1; i < AREA.length; i++) {
            if (area <= AREA[i]) {
                double t = (area - AREA[i - 1]) / (AREA[i] - AREA[i - 1]);
                double load = LOAD[i - 1] + t * (LOAD[i] - LOAD[i - 1]);
                return roundUp(load);
            }
        }
        // above 2500 kg: +0.16 m² per 100 kg
        double extra = (area - AREA[AREA.length - 1]) / 0.16 * 100.0;
        return roundUp(2500 + extra);
    }

    private static int roundUp(double kg) {
        return (int) (Math.ceil(kg / 25.0) * 25);
    }

    /** Weight used for overload detection, in kg. */
    public static double weightOf(net.minecraft.world.entity.Entity e) {
        if (e instanceof net.minecraft.world.entity.player.Player) return 65;
        if (e instanceof net.minecraft.world.entity.item.ItemEntity item) return Math.max(1, item.getItem().getCount() * 0.5);
        if (e instanceof net.minecraft.world.entity.vehicle.AbstractMinecart) return 200;
        if (e instanceof net.minecraft.world.entity.vehicle.Boat) return 120;
        if (e instanceof net.minecraft.world.entity.animal.horse.AbstractHorse) return 500;
        if (e instanceof net.minecraft.world.entity.animal.Cow || e instanceof net.minecraft.world.entity.animal.Pig) return 300;
        if (e instanceof net.minecraft.world.entity.monster.Ravager || e instanceof net.minecraft.world.entity.animal.IronGolem) return 1000;
        if (e instanceof net.minecraft.world.entity.LivingEntity living) {
            double v = living.getBbWidth() * living.getBbWidth() * living.getBbHeight();
            return Math.max(2, Math.min(800, v * 180));
        }
        return 10;
    }
}
