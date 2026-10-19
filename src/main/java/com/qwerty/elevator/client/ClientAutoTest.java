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
    private static int videoHoldCloseTicks;
    private static int videoButtonCarId = -1;
    private static int videoButtonDeadline;

    private record Step(String name, int delay, Consumer<MinecraftServer> server, boolean shot, Runnable client) {}

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
        if (videoHoldCloseTicks > 0) {
            videoHoldCloseTicks--;
            if (videoButtonCarId >= 0)
                com.qwerty.elevator.network.ModNetwork.sendToServer(new com.qwerty.elevator.network.CarButtonPacket(
                        videoButtonCarId, com.qwerty.elevator.elevator.CopLayout.ButtonType.CLOSE.ordinal(), -1));
        }
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
        if (step.client() != null) {
            try {
                step.client().run();
            } catch (Throwable t) {
                ElevatorMod.LOGGER.error("AUTOTEST client step {} failed", step.name(), t);
            }
        }
        if (step.shot()) {
            Screenshot.grab(mc.gameDirectory, (Boolean.getBoolean("qwertys_elevator.lgtest") ? step.name().replace("video_", "lg_") : step.name()) + ".png", mc.getMainRenderTarget(), msg -> ElevatorMod.LOGGER.info("AUTOTEST screenshot {}", step.name()));
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
        STEPS.add(new Step(name, delay, action, false, null));
    }

    private static void shot(String name, int delayAfter) {
        STEPS.add(new Step(name, delayAfter, null, true, null));
    }

    private static void client(String name, int delay, Runnable action) {
        STEPS.add(new Step(name, delay, null, false, action));
    }

    private static ControllerBlockEntity ctrl(MinecraftServer s, ShowcaseBuilder.Site site) {
        ServerLevel level = s.overworld();
        return level.getBlockEntity(site.controller()) instanceof ControllerBlockEntity c ? c : null;
    }

    private static void forAll(MinecraftServer s, java.util.function.Consumer<ControllerBlockEntity> action) {
        for (ShowcaseBuilder.Site site : SITES) {
            ControllerBlockEntity c = ctrl(s, site);
            if (c != null) action.accept(c);
        }
    }

    private static void logStates(String when) {
        server("log_" + when, 2, s -> forAll(s, c -> ElevatorMod.LOGGER.info("AUTOTEST [{}] {}", when, c.debugState())));
    }

    private static void inside(String name, int site, int floor, float yaw, float pitch, double dz, double dx) {
        server("tp_" + name, 50, s -> {
            ShowcaseBuilder.Site a = SITES.get(Math.min(site, SITES.size() - 1));
            look(s, a.doorCenterX() + dx, a.floorY(floor) + 0.02, a.carCenterZ() + dz, yaw, pitch);
        });
        shot(name, 5);
    }

    private static void planSteps() {
        if (Boolean.getBoolean("qwertys_elevator.videotest")) {
            planVideoSteps();
            return;
        }
        server("build", 160, s -> {
            ServerLevel level = s.overworld();
            level.setDayTime(6000);
            ServerPlayer p = player(s);
            origin = new BlockPos(p.getBlockX() + 6, 180, p.getBlockZ() + 6);
            SITES.clear();
            SITES.addAll(ShowcaseBuilder.buildAll(level, origin));
        });
        logStates("built");
        server("park_top", 40, s -> forAll(s, c -> c.debugPlaceAt(c.getGeometry().stopCount() - 1, false)));
        server("tp_landing", 60, s -> {
            ShowcaseBuilder.Site a = SITES.get(0);
            look(s, a.doorCenterX(), a.floorY(1) + 0.02, a.landingZ() - 3.2, 0, 8);
        });
        shot("01_landing_closed", 5);
        server("park_floor1_open", 60, s -> forAll(s, c -> c.debugPlaceAt(c.getGeometry().decks > 1 ? 0 : Math.min(1, c.getGeometry().stopCount() - 1), true)));
        logStates("parked");
        shot("02_landing_open", 5);
        inside("03_car_front", 0, 1, 180, 8, 0.5, -0.2);
        inside("04_car_back", 0, 1, 0, 5, -0.6, 0);
        inside("05_car_cop", 0, 1, 155, 30, -0.1, 0.5);
        // press the "3" button on the car operating panel through the real network path
        client("press_cop", 60, () -> {
            Minecraft mc = Minecraft.getInstance();
            for (com.qwerty.elevator.entity.ElevatorCarEntity car : com.qwerty.elevator.entity.CarRegistry.near(mc.level, mc.player.getBoundingBox().inflate(6))) {
                com.qwerty.elevator.network.ModNetwork.sendToServer(new com.qwerty.elevator.network.CarButtonPacket(car.getId(),
                        com.qwerty.elevator.elevator.CopLayout.ButtonType.FLOOR.ordinal(), 2));
                ElevatorMod.LOGGER.info("AUTOTEST pressed COP button in car {}", car.getId());
                break;
            }
        });
        server("log_after_cop", 2, s -> {
            ControllerBlockEntity c = ctrl(s, SITES.get(0));
            if (c != null) ElevatorMod.LOGGER.info("AUTOTEST [after COP press] {}", c.debugState());
        });
        server("park_again", 40, s -> {
            ControllerBlockEntity c = ctrl(s, SITES.get(0));
            if (c != null) c.debugPlaceAt(1, true);
        });
        // controller GUI tabs
        for (int t = 0; t < 7; t++) {
            final int tab = t;
            client("gui_tab_" + t, 15, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (!(mc.screen instanceof com.qwerty.elevator.client.screen.ControllerScreen)) {
                    mc.setScreen(new com.qwerty.elevator.client.screen.ControllerScreen(SITES.get(0).controller()));
                }
                if (mc.screen instanceof com.qwerty.elevator.client.screen.ControllerScreen cs) cs.showTab(tab);
            });
            shot("40_gui_tab_" + t, 5);
        }
        client("gui_close", 10, () -> Minecraft.getInstance().setScreen(null));
        // ride test: stand in the car, press 3, check the player arrives with the car
        server("ride_prepare", 30, s -> {
            ControllerBlockEntity c = ctrl(s, SITES.get(0));
            if (c != null) c.debugPlaceAt(0, true, false);
        });
        server("ride_enter", 40, s -> {
            ShowcaseBuilder.Site a = SITES.get(0);
            ServerPlayer p = player(s);
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            p.connection.teleport(a.doorCenterX(), a.floorY(0) + 0.05, a.carCenterZ() + 0.3, 180, 10);
        });
        client("ride_press", 360, () -> {
            Minecraft mc = Minecraft.getInstance();
            for (com.qwerty.elevator.entity.ElevatorCarEntity car : com.qwerty.elevator.entity.CarRegistry.near(mc.level, mc.player.getBoundingBox().inflate(6))) {
                com.qwerty.elevator.network.ModNetwork.sendToServer(new com.qwerty.elevator.network.CarButtonPacket(car.getId(),
                        com.qwerty.elevator.elevator.CopLayout.ButtonType.FLOOR.ordinal(), 2));
                break;
            }
        });
        client("ride_check_client", 2, () -> {
            Minecraft mc = Minecraft.getInstance();
            ElevatorMod.LOGGER.info("AUTOTEST [ride] client player y={} expected floor y={}", mc.player.getY(), SITES.get(0).floorY(2));
        });
        server("ride_check_server", 2, s -> {
            ControllerBlockEntity c = ctrl(s, SITES.get(0));
            ElevatorMod.LOGGER.info("AUTOTEST [ride] server player y={} {}", player(s).getY(), c == null ? "" : c.debugState());
        });
        shot("10_ride_arrived", 5);
        server("tp_hoistway", 50, s -> {
            ShowcaseBuilder.Site a = SITES.get(0);
            look(s, a.doorCenterX() - 4.6, a.floorY(1) - 0.5, a.carCenterZ() + 0.4, -90, 0);
        });
        shot("06_hoistway_side", 5);
        server("tp_pit", 50, s -> {
            ShowcaseBuilder.Site a = SITES.get(0);
            look(s, a.doorCenterX() - 4.6, a.floorY(0) - 2.0, a.carCenterZ() + 0.4, -90, -15);
        });
        shot("07_pit", 5);
        server("tp_machine", 50, s -> {
            ShowcaseBuilder.Site a = SITES.get(0);
            look(s, a.doorCenterX() - 3.5, a.machineY() + 0.5, a.carCenterZ() - 3, -45, 20);
        });
        shot("08_machine_room", 5);
        server("send_car_up", 2, s -> {
            ControllerBlockEntity c = ctrl(s, SITES.get(0));
            if (c != null) { c.debugPlaceAt(0, false); c.registerCarCall(2, null); }
        });
        server("tp_moving", 90, s -> {
            ShowcaseBuilder.Site a = SITES.get(0);
            look(s, a.doorCenterX() - 4.6, a.floorY(0) + 2.5, a.carCenterZ() + 0.4, -90, 0);
        });
        logStates("moving");
        shot("09_car_moving", 5);
        for (int i = 1; i < ShowcaseBuilder.TYPES.length; i++) {
            final int idx = i;
            String id = ShowcaseBuilder.TYPES[i].id();
            server("tp_type_" + i, 50, s -> {
                ShowcaseBuilder.Site site = SITES.get(idx);
                look(s, site.doorCenterX() + 0.5, site.floorY(1) + 0.02, site.landingZ() - 3.4, 10, 10);
            });
            shot(String.format("%02d_%s_landing", 10 + i, id), 5);
            if (ShowcaseBuilder.TYPES[i] != ElevatorType.PATERNOSTER) {
                inside(String.format("%02d_%s_inside", 10 + i, id), idx, 1, 180, 10, 0.35, 0);
                inside(String.format("%02d_%s_back", 10 + i, id), idx, 1, 0, 5, -0.3, 0);
            }
        }
        // inclined elevator on its own slope
        final ShowcaseBuilder.Site[] incl = new ShowcaseBuilder.Site[1];
        server("build_inclined", 80, s -> {
            ShowcaseBuilder.Site first = SITES.get(0);
            BlockPos at = new BlockPos((int) first.doorCenterX() - 30, first.floorY(0), first.landingZ() + 40);
            incl[0] = ShowcaseBuilder.buildInclined(s.overworld(), at, 10, 7);
        });
        server("inclined_place", 40, s -> {
            ControllerBlockEntity c = (ControllerBlockEntity) s.overworld().getBlockEntity(incl[0].controller());
            if (c != null) { c.debugPlaceAt(0, true); ElevatorMod.LOGGER.info("AUTOTEST [inclined] {}", c.debugState()); }
        });
        server("tp_inclined_side", 60, s -> {
            ShowcaseBuilder.Site a = incl[0];
            look(s, a.doorCenterX() - 12, a.floorY(0) + 5, a.landingZ() + 9, -90, 15);
        });
        shot("25_inclined_side", 5);
        server("tp_inclined_landing", 60, s -> {
            ShowcaseBuilder.Site a = incl[0];
            look(s, a.doorCenterX() + 0.5, a.floorY(0) + 0.02, a.landingZ() - 3.4, 10, 5);
        });
        shot("26_inclined_landing", 5);
        server("inclined_go", 2, s -> {
            ControllerBlockEntity c = (ControllerBlockEntity) s.overworld().getBlockEntity(incl[0].controller());
            if (c != null) { c.debugPlaceAt(0, false); c.registerCarCall(1, null); }
        });
        server("tp_inclined_moving", 120, s -> {
            ShowcaseBuilder.Site a = incl[0];
            look(s, a.doorCenterX() - 12, a.floorY(0) + 5, a.landingZ() + 9, -90, 15);
        });
        server("inclined_log", 2, s -> {
            ControllerBlockEntity c = (ControllerBlockEntity) s.overworld().getBlockEntity(incl[0].controller());
            if (c != null) ElevatorMod.LOGGER.info("AUTOTEST [inclined moving] {}", c.debugState());
        });
        shot("27_inclined_moving", 5);
        server("tp_overview", 70, s -> {
            ShowcaseBuilder.Site first = SITES.get(0);
            ShowcaseBuilder.Site last = SITES.get(SITES.size() - 1);
            double cx = (first.doorCenterX() + last.doorCenterX()) / 2;
            look(s, cx, first.floorY(1) + 8, first.landingZ() - 45, 0, 12);
        });
        shot("30_overview", 5);
        server("tp_overview_back", 70, s -> {
            ShowcaseBuilder.Site first = SITES.get(0);
            ShowcaseBuilder.Site last = SITES.get(SITES.size() - 1);
            double cx = (first.doorCenterX() + last.doorCenterX()) / 2;
            look(s, cx, first.floorY(1) + 6, first.carCenterZ() + 35, 180, 10);
        });
        shot("31_overview_back", 5);
        logStates("end");
    }

    private static void videoCheckButtonState(boolean pressed) {
        var entity=Minecraft.getInstance().level.getEntity(videoButtonCarId);
        boolean received=entity instanceof com.qwerty.elevator.entity.ElevatorCarEntity car
                && (pressed?car.getStatus().pressed(com.qwerty.elevator.elevator.CopLayout.ButtonType.CLOSE.ordinal(),-1)
                           :car.getStatus().pressedType==-1);
        if(received) return;
        if(ticks>=videoButtonDeadline) {
            Minecraft mc=Minecraft.getInstance();
            ElevatorMod.LOGGER.error("AUTOTEST button diagnostic client entity={} player={} pressed={}",videoButtonCarId,mc.player.position(),
                    entity instanceof com.qwerty.elevator.entity.ElevatorCarEntity car?car.getStatus().pressedType:-999);
            mc.getSingleplayerServer().execute(() -> {
                var server=mc.getSingleplayerServer();
                var target=server.overworld().getEntity(videoButtonCarId);
                ElevatorMod.LOGGER.error("AUTOTEST button diagnostic server entity={} player={} box={} inRange={} pressed={}",
                        videoButtonCarId,player(server).position(),target==null?null:target.getBoundingBox(),
                        target!=null && target.getBoundingBox().inflate(6).contains(player(server).position()),
                        target instanceof com.qwerty.elevator.entity.ElevatorCarEntity car?car.getStatus().pressedType:-999);
            });
            throw new IllegalStateException("Timed out waiting for reference button " + (pressed?"press":"release") + " acknowledgment");
        }
        // Wait for the actual synchronized state rather than assuming a fixed network/frame latency.
        STEPS.addFirst(new Step(pressed?"video_check_pressed":"video_check_released",1,null,false,
                () -> videoCheckButtonState(pressed)));
    }

    private static void videoPanelCamera(MinecraftServer s, double panelY) {
        ControllerBlockEntity c = ctrl(s, SITES.get(0));
        var g = c.getGeometry();
        var m = com.qwerty.elevator.elevator.CopLayout.mounts(c.getSettings(), g, 0).get(0);
        var local = m.point(m.width() / 2, panelY, .35);
        var aim = m.point(m.width() / 2, panelY, 0);
        var anchor = g.anchorAt(c.getPosition());
        var eye = g.toWorld(anchor, local.x, local.y, local.z);
        var target = g.toWorld(anchor, aim.x, aim.y, aim.z);
        var delta = target.subtract(eye);
        var p = player(s);
        p.setGameMode(GameType.SPECTATOR);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        look(s, eye.x, eye.y - p.getEyeHeight(), eye.z, yaw, pitch);
    }

    /** Focused rendering regression for the supplied reference, without running the unrelated showcase. */
    private static void planVideoSteps() {
        server("video_build", 120, s -> {
            ServerPlayer p = player(s);
            origin = new BlockPos(p.getBlockX() + 6, 110, p.getBlockZ() + 6);
            SITES.clear();
            SITES.add(ShowcaseBuilder.buildVideoReference(s.overworld(), origin));
            ControllerBlockEntity c = ctrl(s, SITES.get(0));
            var settings = c.getSettings().copy();
            (Boolean.getBoolean("qwertys_elevator.lgtest") ? com.qwerty.elevator.elevator.DesignPreset.LG_MGP : com.qwerty.elevator.elevator.DesignPreset.VIDEO_CLASSIC).apply(settings);
            if (Boolean.getBoolean("qwertys_elevator.lgtest")) {
                settings.lowestFloorNumber = 1;
            }
            settings.doorHeight = 2.125f;
            settings.doorOpenSec = 8;
            c.applySettings(settings, null, null);
            c.debugPlaceAt(1, false, true);
            s.overworld().setDayTime(6000);
        });
        inside("video_01_front_indicator", 0, 1, 180, 4, 0.55, -0.15);
        inside("video_02_mirror_rails", 0, 1, 0, 8, -0.65, 0);
        inside("video_03_panel", 0, 1, 214, 16, -0.05, 0.2);
        server("video_hardware_closeup", 18, s -> videoPanelCamera(s, .90));
        shot("video_10_curved_buttons_closeup", 5);
        if (!Boolean.getBoolean("qwertys_elevator.lgtest")) server("video_capacity_plate_view", 18, s -> {
            ControllerBlockEntity c=ctrl(s,SITES.get(0));
            var g=c.getGeometry();
            var m=com.qwerty.elevator.elevator.CopLayout.mounts(c.getSettings(),g,0).get(0);
            var plaque=com.qwerty.elevator.elevator.CopLayout.specificationPlate(c.getSettings(),g,m);
            videoPanelCamera(s,plaque.bottom()+plaque.width()*.33);
        });
        if (!Boolean.getBoolean("qwertys_elevator.lgtest")) shot("video_16_capacity_nameplate", 5);
        server("video_close_button_view", 18, s -> {
            videoPanelCamera(s, Boolean.getBoolean("qwertys_elevator.lgtest") ? .10 : .54);
            // Exercise player input in normal play; spectator closeups are for geometry only.
            player(s).setGameMode(GameType.CREATIVE);
            player(s).getAbilities().flying = true;
            player(s).onUpdateAbilities();
        });
        client("video_close_button_hold", 12, () -> {
            Minecraft mc = Minecraft.getInstance();
            for (var car : com.qwerty.elevator.entity.CarRegistry.near(mc.level, mc.player.getBoundingBox().inflate(6))) {
                if (car.getControllerPos().equals(SITES.get(0).controller())) { videoButtonCarId = car.getId(); break; }
            }
            if (videoButtonCarId < 0) throw new IllegalStateException("No reference car for button input");
            videoHoldCloseTicks = 80;
            videoButtonDeadline = ticks + 60;
        });
        client("video_check_pressed", 1, () -> videoCheckButtonState(true));
        shot("video_11_close_button_pressed", 2);
        client("video_stop_button_hold", 18, () -> {
            videoHoldCloseTicks=0;
            videoButtonDeadline=ticks+60;
        });
        client("video_check_released", 1, () -> videoCheckButtonState(false));
        shot("video_12_close_button_released", 3);
        server("video_hardware_done", 2, s -> player(s).setGameMode(GameType.CREATIVE));
        inside("video_04_floor", 0, 1, 0, 65, -0.45, 0);
        inside("video_05_ceiling", 0, 1, 180, -65, 0.1, 0);
        server("video_call", 12, s -> {
            ControllerBlockEntity c = ctrl(s, SITES.get(0));
            c.debugPlaceAt(1, false, false);
            c.registerCarCall(2, null);
        });
        server("video_selected_view", 12, s -> {
            ControllerBlockEntity c = ctrl(s, SITES.get(0));
            var site = SITES.get(0);
            double y = c.getGeometry().anchorAt(c.getPosition()).y;
            look(s, site.doorCenterX() + 0.20, y + 0.02, site.carCenterZ() - 0.05, 214, 16);
        });
        shot("video_06_selected_button", 2);
        server("video_up_view", 12, s -> {
            ControllerBlockEntity c = ctrl(s, SITES.get(0));
            var site = SITES.get(0);
            double y = c.getGeometry().anchorAt(c.getPosition()).y;
            look(s, site.doorCenterX(), y + 0.02, site.carCenterZ() + 0.55, 180, 4);
            if (c.displayDir() != 1) throw new IllegalStateException("Expected ascending indicator");
        });
        shot("video_08_up_indicator", 3);
        server("video_down", 12, s -> {
            ControllerBlockEntity c = ctrl(s, SITES.get(0));
            c.debugPlaceAt(2, false, false);
            c.registerCarCall(0, null);
        });
        server("video_down_view", 12, s -> {
            ControllerBlockEntity c = ctrl(s, SITES.get(0));
            var site = SITES.get(0);
            double y = c.getGeometry().anchorAt(c.getPosition()).y;
            look(s, site.doorCenterX(), y + 0.02, site.carCenterZ() + 0.55, 180, 4);
            if (c.displayDir() != -1) throw new IllegalStateException("Expected descending indicator");
        });
        shot("video_09_down_indicator", 3);
        server("video_landing", 45, s -> {
            ControllerBlockEntity c = ctrl(s, SITES.get(0));
            c.debugPlaceAt(1, true);
            var site = SITES.get(0);
            look(s, site.doorCenterX(), site.floorY(1) + 0.02, site.landingZ() - 2.8, 0, 5);
        });
        shot("video_07_landing", 5);
        server("video_hall_closeup", 12, s -> {
            var site = SITES.get(0);
            look(s, site.doorCenterX() + 1.5, site.floorY(1) + .06, site.landingZ() - 1.0, 0, -5);
        });
        shot("video_13_hall_fixture_closeup", 5);
        server("video_hall_oblique_view", 14, s -> {
            var site=SITES.get(0);
            look(s,site.doorCenterX()+.90,site.floorY(1)+.06,site.landingZ()-1.40,-24,-4);
        });
        shot("video_17_hall_curved_profile", 5);
        server("video_hall_floor_change", 5, s -> ctrl(s, SITES.get(0)).debugPlaceAt(0, false));
        shot("video_14_hall_number_transition", 16);
        shot("video_15_hall_B1", 3);
        logStates("video_end");
    }

    @SuppressWarnings("unused")
    private static ElevatorType unused() { return ElevatorType.PASSENGER; }
}
