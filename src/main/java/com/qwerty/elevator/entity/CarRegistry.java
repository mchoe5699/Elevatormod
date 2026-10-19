package com.qwerty.elevator.entity;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Keeps track of loaded cars. Cars can be much larger than an entity section, so spatial entity queries
 * (which look only at nearby sections) can miss them; this registry is used instead.
 */
public final class CarRegistry {
    private static final Set<ElevatorCarEntity> CLIENT = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ElevatorCarEntity> SERVER = Collections.newSetFromMap(new WeakHashMap<>());

    private CarRegistry() {}

    private static Set<ElevatorCarEntity> set(boolean client) {
        return client ? CLIENT : SERVER;
    }

    static void add(ElevatorCarEntity car) {
        Set<ElevatorCarEntity> s = set(car.level().isClientSide);
        synchronized (s) { s.add(car); }
    }

    static void remove(ElevatorCarEntity car) {
        Set<ElevatorCarEntity> s = set(car.level().isClientSide);
        synchronized (s) { s.remove(car); }
    }

    /** Cars in the given level whose bounding box intersects the area. */
    public static List<ElevatorCarEntity> near(Level level, AABB area) {
        Set<ElevatorCarEntity> s = set(level.isClientSide);
        List<ElevatorCarEntity> out = new ArrayList<>();
        synchronized (s) {
            for (ElevatorCarEntity c : s) {
                if (c.isRemoved() || c.level() != level) continue;
                if (c.getBoundingBox().intersects(area)) out.add(c);
            }
        }
        return out;
    }
}
