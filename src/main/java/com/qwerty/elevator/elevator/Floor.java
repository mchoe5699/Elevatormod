package com.qwerty.elevator.elevator;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import org.jetbrains.annotations.Nullable;

/** One landing (승강장) served by an elevator. */
public class Floor {
    public String name = "1";
    public boolean customName = false;
    /** Floor level = y of the landing floor surface (bottom of the landing door). */
    public int y;
    /** Master block of the front landing door group (may be null for rear-only floors). */
    @Nullable public BlockPos front;
    /** Master block of the rear landing door group (관통형). */
    @Nullable public BlockPos rear;
    /** In service (서비스 층). Floors out of service are skipped. */
    public boolean service = true;

    public Floor() {}

    public Floor(int y) { this.y = y; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putBoolean("custom", customName);
        t.putInt("y", y);
        if (front != null) t.put("front", NbtUtils.writeBlockPos(front));
        if (rear != null) t.put("rear", NbtUtils.writeBlockPos(rear));
        t.putBoolean("service", service);
        return t;
    }

    public static Floor load(CompoundTag t) {
        Floor f = new Floor();
        f.name = t.getString("name");
        f.customName = t.getBoolean("custom");
        f.y = t.getInt("y");
        f.front = t.contains("front") ? NbtUtils.readBlockPos(t.getCompound("front")) : null;
        f.rear = t.contains("rear") ? NbtUtils.readBlockPos(t.getCompound("rear")) : null;
        f.service = !t.contains("service") || t.getBoolean("service");
        return f;
    }

    /** Korean floor naming: B2, B1, 1, 2 … (no floor 0), optionally F for 4. */
    public static String autoName(int number, boolean useF4) {
        if (number < 0) return "B" + (-number);
        if (number == 4 && useF4) return "F";
        return Integer.toString(number);
    }

    public static int floorNumber(int lowest, int index) {
        int n = lowest + index;
        if (lowest < 0 && n >= 0) n += 1;
        if (lowest == 0) n += 1;
        return n;
    }
}
