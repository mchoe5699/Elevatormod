package com.qwerty.elevator.client;

import com.qwerty.elevator.ElevatorMod;
import com.qwerty.elevator.block.entity.ControllerBlockEntity;
import com.qwerty.elevator.elevator.ElevatorType;
import com.qwerty.elevator.test.ShowcaseBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Development helper: with {@code -Dqwertys_elevator.autotest=true} the client creates a world, builds a
 * showcase of every elevator type and saves screenshots, then quits. Used by CI to check rendering.
 */
@Mod.EventBusSubscriber(modid = ElevatorMod.MODID, value = Dist.CLIENT)
public final class ClientAutoTest {
    private static final boolean ENABLED = Boolean.getBoolean("qwertys_elevator.autotest");
    private static boolean started;
    private static int ticks;
    private static int wait;
    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static final List<ShowcaseBuilder.Site> SITES = new ArrayList<>();
    private static BlockPos origin;

    private record Step(String name, int delay, Consumer<MinecraftServer> server, boolean shot) {}

    private ClientAutoTest() {}

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Init.Post event) {
        if (!ENABLED || started || !(event.getScreen() instanceof TitleScreen)) return;
        started = true;
        Minecraft mc = Minecraft.getInstance();
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings("elevator_autotest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("elevator_autotest", settings, new WorldOptions(20261019L, false, false), WorldPresets::createNormalWorldDimensions);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        ticks++;
        if (ticks > 20 * 60 * 12) {
            ElevatorMod.LOGGER.error("AUTOTEST timeout");
            mc.stop();
            return;
        }
        if (ticks == 60) {
            mc.options.hideGui = true;
            planSteps();
        }
        if (ticks < 80) return;
        if (wait > 0) { wait--; return; }
        Step step = STEPS.poll();
        if (step == null) {
            ElevatorMod.LOGGER.info("AUTOTEST finished");
            mc.stop();
            return;
        }
        if (step.server() != null) {
            MinecraftServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                try {
                    step.server().accept(server);
                } catch (Throwable t) {
                    ElevatorMod.LOGGER.error("AUTOTEST step {} failed", step.name(), t);
                }
            });
        }
        if (step.shot()) {
            Screenshot.grab(mc.gameDirectory, step.name() + ".png", mc.getMainRenderTarget(), msg -> ElevatorMod.LOGGER.info("AUTOTEST screenshot {}", step.name()));
        }
        wait = step.delay();
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static void look(MinecraftServer s, double x, double y, double z, float yaw, float pitch) {
        ServerPlayer p = player(s);
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.connection.teleport(x, y, z, yaw, pitch);
    }

    private static void server(String name, int delay, Consumer<MinecraftServer> action) {
        STEPS.add(new Step(name, delay, action, false));
    }

    private static void shot(String name, int delayAfter) {
        STEPS.add(new Step(name, delayAfter, null, true));
    }

    private static ControllerBlockEntity ctrl(MinecraftServer s, ShowcaseBuilder.Site site) {
        ServerLevel level = s.overworld();
        return level.getBlockEntity(site.controller()) instanceof ControllerBlockEntity c ? c : null;
    }

    private static void planSteps() {
        server("build", 200, s -> {
            ServerLevel level = s.overworld();
            level.setDayTime(6000);
            ServerPlayer p = player(s);
            origin = new BlockPos(p.getBlockX() + 6, 180, p.getBlockZ() + 6);
            SITES.clear();
            SITES.addAll(ShowcaseBuilder.buildAll(level, origin));
            for (ShowcaseBuilder.Site site : SITES) {
                ControllerBlockEntity c = ctrl(s, site);
                if (c != null) c.registerCarCall(site.floors() - 1, null);
            }
        });
        // all cars to the top floor first, then the first one back to floor 1 for the landing shots
        server("tp_landing", 160, s -> {
            ShowcaseBuilder.Site a = SITES.get(0);
            look(s, a.doorCenterX(), a.floorY(1) + 1.6, a.landingZ() - 3.5, 0, 8);
        });
        shot("01_landing_closed", 10);
        server("call_floor1", 200, s -> {
            for (ShowcaseBuilder.Site site : SITES) {
                ControllerBlockEntity c = ctrl(s, site);
                if (c != null) c.registerHallCall(1, 1);
            }
        });
        shot("02_landing_open", 20);
        server("tp_car_front", 60, s -> {
            ShowcaseBuilder.Site a = SITES.get(0);
            look(s, a.doorCenterX() - 0.3, a.floorY(1) + 0.05, a.carCenterZ() + 0.5, 180, 10);
        });
        shot("03_car_front", 10);
        server("tp_car_back", 60, s -> {
            ShowcaseBuilder.Site a = SITES.get(0);
            look(s, a.doorCenterX(), a.floorY(1) + 0.05, a.carCenterZ() - 0.6, 0, 5);
        });
        shot("04_car_back", 10);
        server("tp_cop", 60, s -> {
            ShowcaseBuilder.Site a = SITES.get(0);
            look(s, a.doorCenterX() + 0.6, a.floorY(1) + 0.1, a.carCenterZ() - 0.2, 160, 25);
        });
        shot("05_car_cop", 10);
        server("tp_hoistway", 60, s -> {
            ShowcaseBuilder.Site a = SITES.get(0);
            look(s, a.doorCenterX() - 4.5, a.floorY(1) - 1.5, a.carCenterZ() + 0.5, -90, -25);
        });
        shot("06_hoistway_side", 10);
        server("tp_pit", 60, s -> {
            ShowcaseBuilder.Site a = SITES.get(0);
            look(s, a.doorCenterX() - 4.5, a.floorY(0) - 1.0, a.carCenterZ() + 0.5, -90, -10);
        });
        shot("07_pit", 10);
        server("tp_machine", 60, s -> {
            ShowcaseBuilder.Site a = SITES.get(0);
            look(s, a.doorCenterX() - 3.5, a.machineY() + 1.5, a.carCenterZ() - 3, -45, 25);
        });
        shot("08_machine_room", 10);
        server("send_car_up", 30, s -> {
            ControllerBlockEntity c = ctrl(s, SITES.get(0));
            if (c != null) c.registerCarCall(2, null);
        });
        server("tp_moving", 30, s -> {
            ShowcaseBuilder.Site a = SITES.get(0);
            look(s, a.doorCenterX() - 4.5, a.floorY(1) + 1.5, a.carCenterZ() + 0.5, -90, -5);
        });
        shot("09_car_moving", 10);
        for (int i = 1; i < 11; i++) {
            final int idx = i;
            server("tp_type_" + i, 70, s -> {
                if (idx >= SITES.size()) return;
                ShowcaseBuilder.Site site = SITES.get(idx);
                look(s, site.doorCenterX() + 0.8, site.floorY(1) + 1.6, site.landingZ() - 3.8, 12, 12);
            });
            shot(String.format("%02d_type_%s", 10 + i, i < ShowcaseBuilder.TYPES.length ? ShowcaseBuilder.TYPES[i].id() : "x"), 10);
        }
        server("tp_overview", 80, s -> {
            ShowcaseBuilder.Site first = SITES.get(0);
            ShowcaseBuilder.Site last = SITES.get(SITES.size() - 1);
            double cx = (first.doorCenterX() + last.doorCenterX()) / 2;
            look(s, cx, first.floorY(1) + 8, first.landingZ() - 40, 0, 12);
        });
        shot("30_overview", 10);
        server("tp_overview_back", 80, s -> {
            ShowcaseBuilder.Site first = SITES.get(0);
            ShowcaseBuilder.Site last = SITES.get(SITES.size() - 1);
            double cx = (first.doorCenterX() + last.doorCenterX()) / 2;
            look(s, cx, first.floorY(1) + 6, first.carCenterZ() + 30, 180, 10);
        });
        shot("31_overview_back", 10);
    }

    @SuppressWarnings("unused")
    private static ElevatorType unused() { return ElevatorType.PASSENGER; }
}
