package com.qwerty.elevator.elevator;

import net.minecraft.network.chat.Component;

/** Small helper for enums that are shown and cycled in GUIs. */
public interface Choice {
    String id();

    default String key() {
        return "qwertys_elevator." + category() + "." + id();
    }

    String category();

    default Component label() {
        return Component.translatable(key());
    }

    static <E extends Enum<E> & Choice> E byId(Class<E> cls, String id, E fallback) {
        if (id != null) {
            for (E e : cls.getEnumConstants()) {
                if (e.id().equals(id)) return e;
            }
        }
        return fallback;
    }

    static <E extends Enum<E>> E cycle(E current, int delta) {
        E[] values = current.getDeclaringClass().getEnumConstants();
        int i = Math.floorMod(current.ordinal() + delta, values.length);
        return values[i];
    }
}
