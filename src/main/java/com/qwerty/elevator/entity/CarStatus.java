package com.qwerty.elevator.entity;

import com.qwerty.elevator.elevator.ElevatorMode;
import net.minecraft.nbt.CompoundTag;

/** Display/state information shown inside the car (indicator, lit buttons, messages). */
public class CarStatus {
    public String display = "";
    /** Stop the indicator shows (nearest stop while travelling). */
    public int stop = -1;
    /** 1 up, -1 down, 0 none. */
    public int dir;
    public boolean moving;
    public byte[] calls = new byte[0];
    public boolean light = true;
    public boolean overload;
    public boolean alarm;
    public ElevatorMode mode = ElevatorMode.NORMAL;
    /** Translation key of a message on the car display (e.g. 점검중), or empty. */
    public String message = "";
    public int load;     // kg
    public boolean doorHold;
    /** Momentary mechanical depression; floor-call illumination remains independent. */
    public int pressedType = -1;
    public int pressedFloor = -1;
    public boolean pressed(int type, int floor) { return pressedType == type && (type != 0 || pressedFloor == floor); }

    public boolean isCalled(int floor) {
        return floor >= 0 && floor < calls.length && calls[floor] != 0;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("disp", display);
        t.putInt("stop", stop);
        t.putByte("dir", (byte) dir);
        t.putBoolean("mv", moving);
        t.putByteArray("calls", calls);
        t.putBoolean("light", light);
        t.putBoolean("over", overload);
        t.putBoolean("alarm", alarm);
        t.putString("mode", mode.id());
        t.putString("msg", message);
        t.putInt("load", load);
        t.putBoolean("hold", doorHold);
        t.putInt("pressedType", pressedType);
        t.putInt("pressedFloor", pressedFloor);
        return t;
    }

    public static CarStatus load(CompoundTag t) {
        CarStatus s = new CarStatus();
        s.display = t.getString("disp");
        s.stop = t.contains("stop") ? t.getInt("stop") : -1;
        s.dir = t.getByte("dir");
        s.moving = t.getBoolean("mv");
        s.calls = t.getByteArray("calls");
        s.light = !t.contains("light") || t.getBoolean("light");
        s.overload = t.getBoolean("over");
        s.alarm = t.getBoolean("alarm");
        s.mode = com.qwerty.elevator.elevator.Choice.byId(ElevatorMode.class, t.getString("mode"), ElevatorMode.NORMAL);
        s.message = t.getString("msg");
        s.load = t.getInt("load");
        s.doorHold = t.getBoolean("hold");
        s.pressedType = t.contains("pressedType") ? t.getInt("pressedType") : -1;
        s.pressedFloor = t.contains("pressedFloor") ? t.getInt("pressedFloor") : -1;
        return s;
    }
}
