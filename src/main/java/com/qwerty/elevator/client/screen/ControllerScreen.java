package com.qwerty.elevator.client.screen;

import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.elevator.*;
import com.qwerty.elevator.network.ControllerActionPacket;
import com.qwerty.elevator.network.ControllerSettingsPacket;
import com.qwerty.elevator.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** 제어반 설정 화면 – all customisation lives here. */
public class ControllerScreen extends BaseScreen {
    private static final String[] TABS = {"basic", "size", "design", "performance", "floors", "operation", "status"};

    private final BlockPos pos;
    private ElevatorSettings s;
    private final List<String> names = new ArrayList<>();
    private final List<Boolean> service = new ArrayList<>();
    private int tab = 0;
    private int floorPage = 0;
    private int materialSlot = 0;
    private DesignPreset designPreset = DesignPreset.APARTMENT;
    private String customMaterial = "";
    private boolean dirty;
    private int jogDir;
    private int jogTicks;

    public ControllerScreen(BlockPos pos) {
        super(Component.translatable("gui.qwertys_elevator.controller"));
        this.pos = pos;
        this.panelH = 254;
        ControllerBlockEntity c = controller();
        this.s = c != null ? c.getSettings().copy() : new ElevatorSettings();
        if (s.lgInterior) designPreset = DesignPreset.LG_MGP;
        else if (s.videoInterior) designPreset = DesignPreset.VIDEO_CLASSIC;
        if (c != null) {
            for (Floor f : c.getFloors()) {
                names.add(f.name);
                service.add(f.service);
            }
        }
    }

    private ControllerBlockEntity controller() {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        return level.getBlockEntity(pos) instanceof ControllerBlockEntity c ? c : null;
    }

    private void mark() {
        dirty = true;
    }

    @Override
    protected void build() {
        int x = left, y = top;
        // tabs
        int tw = panelW / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            Button b = button(Component.translatable("gui.qwertys_elevator.tab." + TABS[i]), x + i * tw, y + 14, tw - 2, btn -> {
                tab = idx;
                rebuild();
            });
            b.active = i != tab;
        }
        int cy = y + 40;
        switch (tab) {
            case 0 -> basic(x + 8, cy);
            case 1 -> size(x + 8, cy);
            case 2 -> design(x + 8, cy);
            case 3 -> performance(x + 8, cy);
            case 4 -> floors(x + 8, cy);
            case 5 -> operation(x + 8, cy);
            case 6 -> status(x + 8, cy);
        }
        int by = top + panelH - 20;
        button(Component.translatable("gui.qwertys_elevator.apply"), left + panelW - 168, by, 80, b -> apply());
        button(Component.translatable("gui.done"), left + panelW - 84, by, 80, b -> {
            if (dirty) apply();
            onClose();
        });
    }

    /** Used by the screenshot test. */
    public void showTab(int t) {
        tab = Math.max(0, Math.min(TABS.length - 1, t));
        rebuild();
    }

    private void apply() {
        s.clamp();
        ModNetwork.sendToServer(new ControllerSettingsPacket(pos, s.save(), new ArrayList<>(names), new ArrayList<>(service)));
        dirty = false;
    }

    // ------------------------------------------------------------------ tabs

    private void basic(int x, int y) {
        int c2 = x + 186;
        label(Component.translatable("gui.qwertys_elevator.name"), x, y + 4);
        edit(x + 60, y, 110, s.name, 24, v -> { s.name = v; mark(); });
        cycle(Component.translatable("gui.qwertys_elevator.type"), x, y + 22, 170, () -> s.type.label(), d -> {
            s.type = Choice.cycle(s.type, d);
            mark();
        });
        button(Component.translatable("gui.qwertys_elevator.apply_type"), x, y + 44, 170, b -> {
            s.type.applyDefaults(s);
            mark();
            rebuild();
        });
        cycle(Component.translatable("gui.qwertys_elevator.drive"), x, y + 66, 170, () -> s.drive.label(), d -> { s.drive = Choice.cycle(s.drive, d); mark(); });
        label(Component.translatable("gui.qwertys_elevator.type_desc." + s.type.id()).withStyle(ChatFormatting.GRAY), x, y + 92, 0xA0A8B0);

        stepper(Component.translatable("gui.qwertys_elevator.capacity_kg"), c2, y, 80, () -> s.capacityKg + " kg", d -> { s.capacityKg += d * 25; mark(); });
        stepper(Component.translatable("gui.qwertys_elevator.capacity_persons"), c2, y + 20, 80, () -> s.capacityPersons + "", d -> { s.capacityPersons += d; mark(); });
        button(Component.translatable("gui.qwertys_elevator.auto_capacity"), c2, y + 42, 170, b -> { s.autoCapacity(); mark(); rebuild(); });
        label(Component.translatable("gui.qwertys_elevator.area", String.format("%.2f", s.floorArea())), c2, y + 66, 0xA0A8B0);
        toggle(Component.translatable("gui.qwertys_elevator.chime"), c2, y + 80, 84, s.chime, v -> { s.chime = v; mark(); });
        toggle(Component.translatable("gui.qwertys_elevator.voice"), c2 + 86, y + 80, 84, s.voice, v -> { s.voice = v; mark(); });
        toggle(Component.translatable("gui.qwertys_elevator.riders"), c2, y + 102, 84, s.riders, v -> { s.riders = v; mark(); });
        toggle(Component.translatable("gui.qwertys_elevator.storage"), c2 + 86, y + 102, 84, s.storage, v -> { s.storage = v; mark(); });
        toggle(Component.translatable("gui.qwertys_elevator.vehicles"), c2, y + 124, 84, s.carriesVehicles, v -> { s.carriesVehicles = v; mark(); });
        toggle(Component.translatable("gui.qwertys_elevator.fire_service"), c2 + 86, y + 124, 84, s.fireService, v -> { s.fireService = v; mark(); });
    }

    private void size(int x, int y) {
        int c2 = x + 186;
        stepper(Component.translatable("gui.qwertys_elevator.width"), x, y, 80, () -> fmt(s.carWidth) + " m", d -> { s.carWidth += d * 0.125f; s.clamp(); mark(); });
        stepper(Component.translatable("gui.qwertys_elevator.depth"), x, y + 20, 80, () -> fmt(s.carDepth) + " m", d -> { s.carDepth += d * 0.125f; s.clamp(); mark(); });
        stepper(Component.translatable("gui.qwertys_elevator.height"), x, y + 40, 80, () -> fmt(s.carHeight) + " m", d -> { s.carHeight += d * 0.125f; s.clamp(); mark(); });
        stepper(Component.translatable("gui.qwertys_elevator.offset"), x, y + 60, 80, () -> fmt(s.carOffset) + " m", d -> { s.carOffset += d * 0.0625f; s.clamp(); mark(); });
        stepper(Component.translatable("gui.qwertys_elevator.door_width"), x, y + 80, 80, () -> s.doorWidth <= 0 ? Component.translatable("gui.qwertys_elevator.auto").getString() : fmt(s.doorWidth) + " m",
                d -> { s.doorWidth = Math.max(0, s.doorWidth + d * 0.125f); mark(); });
        stepper(Component.translatable("gui.qwertys_elevator.door_height"), x, y + 100, 80, () -> s.doorHeight <= 0 ? Component.translatable("gui.qwertys_elevator.auto").getString() : fmt(s.doorHeight) + " m",
                d -> { s.doorHeight = Math.max(0, s.doorHeight + d * 0.125f); mark(); });
        cycle(Component.translatable("gui.qwertys_elevator.shape"), x, y + 122, 170, () -> s.shape.label(), d -> { s.shape = Choice.cycle(s.shape, d); mark(); });
        cycle(Component.translatable("gui.qwertys_elevator.door_style"), x, y + 144, 170, () -> s.doorStyle.label(), d -> { s.doorStyle = Choice.cycle(s.doorStyle, d); mark(); });

        stepper(Component.translatable("gui.qwertys_elevator.decks"), c2, y, 80, () -> s.decks + "", d -> { s.decks = Math.max(1, Math.min(2, s.decks + Integer.signum(d))); mark(); });
        stepper(Component.translatable("gui.qwertys_elevator.deck_spacing"), c2, y + 20, 80, () -> s.deckSpacing <= 0 ? Component.translatable("gui.qwertys_elevator.auto").getString() : fmt(s.deckSpacing),
                d -> { s.deckSpacing = Math.max(0, s.deckSpacing + d * 0.5f); mark(); });
        stepper(Component.translatable("gui.qwertys_elevator.cabins"), c2, y + 40, 80, () -> s.paternosterCabins + "", d -> { s.paternosterCabins += Integer.signum(d) * (Math.abs(d) > 1 ? 4 : 1); s.clamp(); mark(); });
        stepper(Component.translatable("gui.qwertys_elevator.pit"), c2, y + 60, 80, () -> fmt(s.pitDepth) + " m", d -> { s.pitDepth += d * 0.25f; s.clamp(); mark(); });
        stepper(Component.translatable("gui.qwertys_elevator.overhead"), c2, y + 80, 80, () -> fmt(s.overhead) + " m", d -> { s.overhead += d * 0.25f; s.clamp(); mark(); });
        label(Component.translatable("gui.qwertys_elevator.size_hint").withStyle(ChatFormatting.GRAY), c2, y + 106, 0x9AA3AA);
        label(Component.translatable("gui.qwertys_elevator.size_hint2").withStyle(ChatFormatting.GRAY), c2, y + 118, 0x9AA3AA);
    }

    private static final String[] SLOTS = {"wall", "floor", "ceiling", "door", "trim"};

    private String getSlot(int i) {
        return switch (i) {
            case 0 -> s.matWall;
            case 1 -> s.matFloor;
            case 2 -> s.matCeiling;
            case 3 -> s.matDoor;
            default -> s.matTrim;
        };
    }

    private void setSlot(int i, String v) {
        switch (i) {
            case 0 -> s.matWall = v;
            case 1 -> s.matFloor = v;
            case 2 -> s.matCeiling = v;
            case 3 -> s.matDoor = v;
            default -> s.matTrim = v;
        }
        mark();
    }

    private void design(int x, int y) {
        int c2 = x + 186;
        for (int i = 0; i < SLOTS.length; i++) {
            final int idx = i;
            Component name = Component.translatable("gui.qwertys_elevator.mat." + SLOTS[i]);
            Button b = addRenderableWidget(Button.builder(Component.empty().append(name).append(": ").append(Presets.materialName(getSlot(i))), btn -> {
                materialSlot = idx;
                setSlot(idx, Presets.cycleMaterial(getSlot(idx), hasShiftDown() ? -1 : 1));
                rebuild();
            }).bounds(x, y + i * 20, 170, 18).build());
            if (i == materialSlot) b.setMessage(Component.literal("▶ ").append(b.getMessage()));
        }
        edit(x, y + 104, 120, customMaterial, 64, v -> customMaterial = v);
        button(Component.translatable("gui.qwertys_elevator.set_custom"), x + 122, y + 103, 48, b -> {
            if (Presets.validBlock(customMaterial)) {
                setSlot(materialSlot, customMaterial);
                rebuild();
            }
        });
        label(Component.translatable("gui.qwertys_elevator.custom_hint").withStyle(ChatFormatting.GRAY), x, y + 124, 0x9AA3AA);
        cycle(Component.translatable("gui.qwertys_elevator.light_color"), x, y + 138, 170, () -> Presets.lightName(s.lightColor), d -> { s.lightColor = Presets.cycleLight(s.lightColor, d); mark(); });
        button(Component.translatable("gui.qwertys_elevator.apply_video_design"), x, y + 180, 83, b -> {
            designPreset = DesignPreset.VIDEO_CLASSIC;
            designPreset.apply(s);
            mark();
            rebuild();
        });
        button(Component.translatable("gui.qwertys_elevator.apply_lg_design"), x + 87, y + 180, 83, b -> {
            designPreset = DesignPreset.LG_MGP;
            designPreset.apply(s);
            mark();
            rebuild();
        });
        cycle(Component.translatable("gui.qwertys_elevator.design_preset"), x, y + 160, 170, () -> designPreset.label(), d -> {
            designPreset = Choice.cycle(designPreset, d);
            designPreset.apply(s);
            mark();
        });

        cycle(Component.translatable("gui.qwertys_elevator.wall_back"), c2, y, 170, () -> s.wallBack.label(), d -> { s.wallBack = Choice.cycle(s.wallBack, d); mark(); });
        cycle(Component.translatable("gui.qwertys_elevator.wall_left"), c2, y + 20, 170, () -> s.wallLeft.label(), d -> { s.wallLeft = Choice.cycle(s.wallLeft, d); mark(); });
        cycle(Component.translatable("gui.qwertys_elevator.wall_right"), c2, y + 40, 170, () -> s.wallRight.label(), d -> { s.wallRight = Choice.cycle(s.wallRight, d); mark(); });
        cycle(Component.translatable("gui.qwertys_elevator.panel_style"), c2, y + 60, 170, () -> s.panelStyle.label(), d -> { s.panelStyle = Choice.cycle(s.panelStyle, d); mark(); });
        cycle(Component.translatable("gui.qwertys_elevator.cop_position"), c2, y + 80, 170, () -> s.copPosition.label(), d -> { s.copPosition = Choice.cycle(s.copPosition, d); mark(); });
        cycle(Component.translatable("gui.qwertys_elevator.indicator"), c2, y + 100, 170, () -> s.indicatorStyle.label(), d -> { s.indicatorStyle = Choice.cycle(s.indicatorStyle, d); mark(); });
        cycle(Component.translatable("gui.qwertys_elevator.button_light"), c2, y + 120, 170, () -> s.buttonLight.label(), d -> { s.buttonLight = Choice.cycle(s.buttonLight, d); mark(); });
        toggle(Component.translatable("gui.qwertys_elevator.ceiling"), c2, y + 140, 84, s.ceiling, v -> { s.ceiling = v; mark(); });
        toggle(Component.translatable("gui.qwertys_elevator.handrail"), c2 + 86, y + 140, 84, s.handrail, v -> { s.handrail = v; mark(); });
        toggle(Component.translatable("gui.qwertys_elevator.mirror"), c2, y + 160, 84, s.mirror, v -> { s.mirror = v; mark(); });
        toggle(Component.translatable("gui.qwertys_elevator.cctv"), c2 + 86, y + 160, 84, s.cctv, v -> { s.cctv = v; mark(); });
    }

    private void performance(int x, int y) {
        int c2 = x + 186;
        stepper(Component.translatable("gui.qwertys_elevator.speed"), x, y, 80, () -> s.speedMpm + " m/min", d -> {
            int[] steps = ElevatorSettings.SPEED_STEPS;
            int idx = 0;
            for (int i = 0; i < steps.length; i++) if (steps[i] <= s.speedMpm) idx = i;
            idx = Math.max(0, Math.min(steps.length - 1, idx + (Math.abs(d) > 1 ? Integer.signum(d) * 5 : d)));
            s.speedMpm = steps[idx];
            mark();
        });
        label(Component.literal(String.format("= %.2f m/s", s.speedMpm / 60.0)), x + 80, y + 20, 0x9AA3AA);
        stepper(Component.translatable("gui.qwertys_elevator.accel"), x, y + 32, 80, () -> String.format("%.1f m/s²", s.accel), d -> { s.accel = Math.round((s.accel + d * 0.1f) * 10) / 10f; s.clamp(); mark(); });
        stepper(Component.translatable("gui.qwertys_elevator.door_open_time"), x, y + 52, 80, () -> fmt(s.doorOpenSec) + " s", d -> { s.doorOpenSec += d * 0.5f; s.clamp(); mark(); });
        stepper(Component.translatable("gui.qwertys_elevator.door_move_time"), x, y + 72, 80, () -> fmt(s.doorMoveSec) + " s", d -> { s.doorMoveSec += d * 0.25f; s.clamp(); mark(); });
        cycle(Component.translatable("gui.qwertys_elevator.counterweight"), x, y + 94, 170, () -> s.counterweight.label(), d -> { s.counterweight = Choice.cycle(s.counterweight, d); mark(); });
        toggle(Component.translatable("gui.qwertys_elevator.group"), x, y + 116, 170, s.groupControl, v -> { s.groupControl = v; mark(); });

        stepper(Component.translatable("gui.qwertys_elevator.auto_return"), c2, y, 80, () -> s.autoReturnSec <= 0 ? Component.translatable("gui.qwertys_elevator.off").getString() : s.autoReturnSec + " s",
                d -> { s.autoReturnSec = Math.max(0, s.autoReturnSec + d * 10); mark(); });
        stepper(Component.translatable("gui.qwertys_elevator.light_off"), c2, y + 20, 80, () -> s.lightOffSec <= 0 ? Component.translatable("gui.qwertys_elevator.off").getString() : s.lightOffSec + " s",
                d -> { s.lightOffSec = Math.max(0, s.lightOffSec + d * 10); mark(); });
        int n = Math.max(1, names.size());
        stepper(Component.translatable("gui.qwertys_elevator.main_floor"), c2, y + 40, 80, () -> floorLabel(s.mainFloor), d -> { s.mainFloor = Math.floorMod(s.mainFloor + Integer.signum(d), n); mark(); });
        stepper(Component.translatable("gui.qwertys_elevator.park_floor"), c2, y + 60, 80, () -> floorLabel(s.parkingFloor), d -> { s.parkingFloor = Math.floorMod(s.parkingFloor + Integer.signum(d), n); mark(); });
        stepper(Component.translatable("gui.qwertys_elevator.fire_floor"), c2, y + 80, 80, () -> floorLabel(s.fireFloor), d -> { s.fireFloor = Math.floorMod(s.fireFloor + Integer.signum(d), n); mark(); });
        double travelTime = estimateTravel();
        label(Component.translatable("gui.qwertys_elevator.travel_time", String.format("%.1f", travelTime)), c2, y + 106, 0x9AA3AA);
    }

    private double estimateTravel() {
        ControllerBlockEntity c = controller();
        if (c == null || c.getGeometry().stopCount() < 2) return 0;
        double dist = c.getGeometry().maxS();
        double v = s.speedMpm / 60.0, a = s.accel;
        double accDist = v * v / a;
        if (dist <= accDist) return 2 * Math.sqrt(dist / a);
        return 2 * v / a + (dist - accDist) / v;
    }

    private String floorLabel(int i) {
        if (i < 0 || i >= names.size()) return "-";
        return names.get(i);
    }

    private void floors(int x, int y) {
        ControllerBlockEntity c = controller();
        int perPage = 7;
        int pages = Math.max(1, (names.size() + perPage - 1) / perPage);
        floorPage = Math.max(0, Math.min(pages - 1, floorPage));
        if (names.isEmpty()) {
            label(Component.translatable("gui.qwertys_elevator.no_floors").withStyle(ChatFormatting.YELLOW), x, y + 4);
            label(Component.translatable("gui.qwertys_elevator.no_floors_hint").withStyle(ChatFormatting.GRAY), x, y + 18, 0x9AA3AA);
        }
        for (int row = 0; row < perPage; row++) {
            int i = floorPage * perPage + (perPage - 1 - row);
            if (i >= names.size()) continue;
            int ry = y + row * 20;
            final int idx = i;
            String y0 = c != null && i < c.getFloors().size() ? "Y " + c.getFloors().get(i).y : "";
            label(Component.literal("#" + (i + 1)), x, ry + 5, 0x9AA3AA);
            edit(x + 28, ry, 50, names.get(i), 6, v -> {
                names.set(idx, v);
                s.autoNames = false;
                mark();
            });
            toggle(Component.translatable("gui.qwertys_elevator.service"), x + 84, ry, 90, service.get(i), v -> { service.set(idx, v); mark(); });
            small(Component.translatable("gui.qwertys_elevator.call"), x + 178, ry + 1, 40, b -> ModNetwork.sendToServer(new ControllerActionPacket(pos, "call", idx)));
            small(Component.literal("▲"), x + 222, ry + 1, 18, b -> ModNetwork.sendToServer(new ControllerActionPacket(pos, "hall_up", idx)));
            small(Component.literal("▼"), x + 242, ry + 1, 18, b -> ModNetwork.sendToServer(new ControllerActionPacket(pos, "hall_down", idx)));
            label(Component.literal(y0), x + 266, ry + 5, 0x9AA3AA);
        }
        int by = y + perPage * 20 + 2;
        small(Component.literal("◀"), x, by, 20, b -> { floorPage--; rebuild(); });
        label(Component.literal((floorPage + 1) + "/" + pages), x + 26, by + 4, 0xFFFFFF);
        small(Component.literal("▶"), x + 50, by, 20, b -> { floorPage++; rebuild(); });
        toggle(Component.translatable("gui.qwertys_elevator.auto_names"), x + 76, by - 1, 100, s.autoNames, v -> {
            s.autoNames = v;
            if (v) renumber();
            mark();
        });
        stepper(Component.translatable("gui.qwertys_elevator.lowest"), x + 182, by - 1, 60, () -> Floor.autoName(Floor.floorNumber(s.lowestFloorNumber, 0), false),
                d -> { s.lowestFloorNumber = Math.max(-20, Math.min(200, s.lowestFloorNumber + Integer.signum(d))); if (s.lowestFloorNumber == 0) s.lowestFloorNumber = Integer.signum(d); renumber(); mark(); });
        toggle(Component.literal("4→F"), x + 336 - 4, by - 1, 40, s.useF4, v -> { s.useF4 = v; renumber(); mark(); });
    }

    private void renumber() {
        if (!s.autoNames) return;
        for (int i = 0; i < names.size(); i++) names.set(i, Floor.autoName(Floor.floorNumber(s.lowestFloorNumber, i), s.useF4));
    }

    private void operation(int x, int y) {
        ControllerBlockEntity c = controller();
        ElevatorMode current = c != null ? c.getMode() : ElevatorMode.NORMAL;
        ElevatorMode[] modes = {ElevatorMode.NORMAL, ElevatorMode.INDEPENDENT, ElevatorMode.INSPECTION, ElevatorMode.FIRE_RECALL, ElevatorMode.OUT_OF_SERVICE};
        for (int i = 0; i < modes.length; i++) {
            ElevatorMode m = modes[i];
            Button b = button(m.label(), x, y + i * 20, 170, btn -> {
                ModNetwork.sendToServer(new ControllerActionPacket(pos, "mode", m.ordinal()));
            });
            if (m == current) b.setMessage(Component.literal("● ").append(m.label()).withStyle(ChatFormatting.GREEN));
        }
        int c2 = x + 186;
        label(Component.translatable("gui.qwertys_elevator.inspection_jog"), c2, y + 4);
        addRenderableWidget(new HoldButton(c2, y + 18, 80, Component.literal("▲ UP"), () -> jogDir = 1, () -> jogDir = 0));
        addRenderableWidget(new HoldButton(c2 + 86, y + 18, 80, Component.literal("▼ DOWN"), () -> jogDir = -1, () -> jogDir = 0));
        button(Component.translatable("gui.qwertys_elevator.door_open"), c2, y + 44, 80, b -> ModNetwork.sendToServer(new ControllerActionPacket(pos, "open", 0)));
        button(Component.translatable("gui.qwertys_elevator.door_close"), c2 + 86, y + 44, 80, b -> ModNetwork.sendToServer(new ControllerActionPacket(pos, "close", 0)));
        boolean power = c == null || c.isMainPower();
        button(Component.translatable(power ? "gui.qwertys_elevator.power_off" : "gui.qwertys_elevator.power_on"), c2, y + 70, 166,
                b -> ModNetwork.sendToServer(new ControllerActionPacket(pos, "power", power ? 0 : 1)));
        button(Component.translatable("gui.qwertys_elevator.respawn"), c2, y + 92, 166, b -> ModNetwork.sendToServer(new ControllerActionPacket(pos, "respawn", 0)));
        label(Component.translatable("gui.qwertys_elevator.redstone_hint").withStyle(ChatFormatting.GRAY), x, y + 108, 0x9AA3AA);
    }

    private void status(int x, int y) {
        ControllerBlockEntity c = controller();
        if (c == null) return;
        int line = 0;
        label(Component.translatable("gui.qwertys_elevator.st.mode", c.getMode().label()), x, y + 12 * line++);
        label(Component.translatable("gui.qwertys_elevator.st.floor", c.displayName(), c.getFloors().size()), x, y + 12 * line++);
        label(Component.translatable("gui.qwertys_elevator.st.speed", String.format("%.2f", Math.abs(c.getVelocity()) * 20)), x, y + 12 * line++);
        label(Component.translatable("gui.qwertys_elevator.st.load", c.getLoadKg(), c.getSettings().capacityKg), x, y + 12 * line++,
                c.isOverload() ? 0xFF5050 : 0xD8DEE3);
        label(Component.translatable("gui.qwertys_elevator.st.power", Component.translatable(c.isMainPower() ? "gui.qwertys_elevator.on" : "gui.qwertys_elevator.off")), x, y + 12 * line++);
        label(Component.translatable("gui.qwertys_elevator.st.facing", c.getFacing() == null ? "-" : c.getFacing().getName()), x, y + 12 * line++);
        line++;
        label(Component.translatable("gui.qwertys_elevator.st.warnings").withStyle(ChatFormatting.YELLOW), x, y + 12 * line++);
        if (c.getWarnings().isEmpty()) {
            label(Component.translatable("gui.qwertys_elevator.st.ok").withStyle(ChatFormatting.GREEN), x + 6, y + 12 * line++);
        }
        for (String w : c.getWarnings()) {
            label(Component.literal("• ").append(Component.translatable(w)), x + 6, y + 12 * line++, 0xFFC060);
            if (line > 14) break;
        }
    }

    // ------------------------------------------------------------------ tick / render

    @Override
    public void tick() {
        super.tick();
        if (jogDir != 0 && !Minecraft.getInstance().mouseHandler.isLeftPressed()) jogDir = 0;
        if (jogDir != 0 && ++jogTicks % 2 == 0) {
            ModNetwork.sendToServer(new ControllerActionPacket(pos, "jog", jogDir));
        }
        if (tab == 6 && Minecraft.getInstance().level != null && Minecraft.getInstance().level.getGameTime() % 10 == 0) rebuild();
        if (controller() == null) onClose();
    }

    @Override
    protected void drawExtra(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Component title = Component.translatable("gui.qwertys_elevator.controller_title", s.name);
        g.drawString(font, title, left + 4, top + 2, 0xFFFFFF, false);
        if (dirty) g.drawString(font, Component.translatable("gui.qwertys_elevator.unsaved"), left + 4, top + panelH - 15, 0xFFC060, false);
    }

    @Override
    public void removed() {
        super.removed();
        jogDir = 0;
    }

    /** Button that runs an action while held down (inspection jog). */
    private static class HoldButton extends Button {
        private final Runnable onDown, onUp;

        HoldButton(int x, int y, int w, Component text, Runnable onDown, Runnable onUp) {
            super(x, y, w, 18, text, b -> {}, DEFAULT_NARRATION);
            this.onDown = onDown;
            this.onUp = onUp;
        }

        @Override
        public void onClick(double mx, double my) {
            onDown.run();
        }

        @Override
        public void onRelease(double mx, double my) {
            onUp.run();
        }
    }
}
