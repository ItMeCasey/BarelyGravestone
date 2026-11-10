package com.itmecasey.barelygravestone.dev;

import com.itmecasey.barelygravestone.ModContent;
import com.itmecasey.barelygravestone.block.GraveBlockEntity;
import com.itmecasey.barelygravestone.core.GraveRegistry;
import com.itmecasey.barelygravestone.server.GraveService;
import com.itmecasey.barelygravestone.server.GraveStores;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A development-only camera tour on a real client. It opens a fresh survival superflat world, gives the player a kit and kills
 * them with the game's own {@code /kill}, so the real drop event makes the grave. It then respawns, looks at the pike and the
 * head, holds the Obituary (particles), opens it, takes an item out while it is open, collects with an empty hand and saves
 * screenshots into {@code run/screenshots} as {@code barelygravestone_<name>.png} before quitting. It does nothing unless the
 * environment variable {@code BARELYGRAVESTONE_SHOTS} is set; progress goes to the log as {@code GRAVE-SHOTS} lines.
 *
 * <p>It never touches the real cursor or keyboard: it calls the game's own handlers (the game mode's use methods) and lets go
 * of the mouse every tick.
 */
public final class ShotTour {
    private static final Logger LOGGER = LoggerFactory.getLogger("BarelyGravestone");
    private static final boolean ENABLED = System.getenv("BARELYGRAVESTONE_SHOTS") != null;
    private static final String WORLD = "barelygravestone_shots";

    private enum Stage { WAITING_FOR_MENU, LOADING_WORLD, TOURING, DONE }

    private record Step(int delay, Runnable action, BooleanSupplier ready, int timeout) {
    }

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static Stage stage = Stage.WAITING_FOR_MENU;
    private static int idle;
    private static int wait;
    private static int held;
    private static int ground;
    private static boolean pauseOnLostFocus;
    private static GraveRegistry.Entry grave;

    private ShotTour() {
    }

    public static void tick() {
        if (!ENABLED || stage == Stage.DONE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (minecraft.mouseHandler.isMouseGrabbed()) {
                minecraft.mouseHandler.releaseMouse();
            }
            switch (stage) {
                case WAITING_FOR_MENU -> waitForMenu(minecraft);
                case LOADING_WORLD -> waitForWorld(minecraft);
                case TOURING -> runSteps(minecraft);
                default -> {
                }
            }
        } catch (RuntimeException | Error e) {
            LOGGER.error("GRAVE-SHOTS the tour failed", e);
            stage = Stage.DONE;
            minecraft.stop();
        }
    }

    private static void waitForMenu(Minecraft minecraft) {
        if (minecraft.getOverlay() != null || minecraft.level != null || minecraft.screen == null || ++idle < 20) {
            return;
        }
        pauseOnLostFocus = minecraft.options.pauseOnLostFocus;
        minecraft.options.pauseOnLostFocus = false;
        LevelStorageSource source = minecraft.getLevelSource();
        if (source.levelExists(WORLD)) {
            try (LevelStorageSource.LevelStorageAccess access = source.createAccess(WORLD)) {
                access.deleteLevel();
            } catch (IOException e) {
                LOGGER.warn("GRAVE-SHOTS could not delete the old world", e);
            }
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                new net.minecraft.world.level.GameRules(), WorldDataConfiguration.DEFAULT);
        stage = Stage.LOADING_WORLD;
        idle = 0;
        LOGGER.info("GRAVE-SHOTS creating world");
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0L, false, false),
                access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private static void waitForWorld(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null
                || minecraft.getSingleplayerServer() == null || ++idle < 40) {
            return;
        }
        ground = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        LOGGER.info("GRAVE-SHOTS in the world, ground at y={}", ground);
        plan();
        stage = Stage.TOURING;
    }

    private static void runSteps(Minecraft minecraft) {
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = STEPS.peek();
        if (step == null) {
            return;
        }
        if (step.ready() != null && !step.ready().getAsBoolean()) {
            if (++held > step.timeout()) {
                throw new IllegalStateException("a step timed out waiting");
            }
            return;
        }
        held = 0;
        STEPS.poll();
        step.action().run();
        Step next = STEPS.peek();
        wait = next == null ? 0 : next.delay();
    }

    // ---- the tour ----

    private static void plan() {
        then(0, () -> {
            server("time set noon", "weather clear", "kill @e[type=!player]", "clear @p");
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        });
        then(5, () -> server("tp @p 0.5 " + ground + " 0.5 0 0", "give @p minecraft:cobblestone 64",
                "give @p minecraft:diamond 7", "give @p minecraft:iron_sword", "give @p minecraft:bread 16",
                "item replace entity @p armor.head with minecraft:diamond_helmet",
                "item replace entity @p armor.chest with minecraft:iron_chestplate",
                "item replace entity @p weapon.offhand with minecraft:shield", "experience set @p 30 levels"));
        shoot("01_before_death", 20);
        // A real death through the game's own kill command: the real drop event makes the grave.
        then(5, () -> server("kill @p"));
        until(() -> Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.DeathScreen, 200);
        shoot("02_death_screen", 10);
        then(5, () -> Minecraft.getInstance().player.respawn());
        until(() -> Minecraft.getInstance().screen == null && Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.isAlive() && !GraveStores.registry().all().isEmpty(), 200);
        then(30, () -> {
            grave = GraveStores.registry().all().get(0);
            LOGGER.info("GRAVE-SHOTS grave {} at {} {} {} in {}", grave.id(), grave.x(), grave.y(), grave.z(), grave.dimension());
            server("gamemode creative @p");
        });
        then(10, () -> Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player)));
        shoot("03_inventory_with_obituary", 10);
        then(2, () -> Minecraft.getInstance().setScreen(null));

        // The pike with the head, from the front and close up.
        then(5, () -> look(new Vec3(grave.x() + 0.5, grave.y() + 1.6, grave.z() + 4.0), new Vec3(grave.x() + 0.5, grave.y() + 0.8, grave.z() + 0.5)));
        shoot("04_pike_and_head", 30);
        then(5, () -> look(new Vec3(grave.x() + 2.2, grave.y() + 1.3, grave.z() + 2.2), new Vec3(grave.x() + 0.5, grave.y() + 0.75, grave.z() + 0.5)));
        shoot("05_pike_close", 15);
        then(5, () -> look(new Vec3(grave.x() - 3.0, grave.y() + 2.2, grave.z() - 3.0), new Vec3(grave.x() + 0.5, grave.y() + 0.75, grave.z() + 0.5)));
        shoot("06_pike_from_behind", 15);

        // The particles: stand a few blocks away holding the Obituary, third person so the trail is in view.
        then(5, () -> {
            LocalPlayer player = Minecraft.getInstance().player;
            for (int slot = 0; slot < 9; slot++) {
                if (player.getInventory().getItem(slot).is(ModContent.OBITUARY)) {
                    player.getInventory().selected = slot;
                }
            }
            server("gamemode survival @p", "tp @p " + (grave.x() + 0.5) + " " + grave.y() + " " + (grave.z() + 9.5) + " 180 5");
            Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        shoot("07_particles_third_person", 60);
        then(2, () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON));
        shoot("08_particles_first_person", 25);

        // Open the Obituary, then take one stack out of the grave while it is open: the list updates by itself.
        then(5, () -> Minecraft.getInstance().gameMode.useItem(Minecraft.getInstance().player, InteractionHand.MAIN_HAND));
        until(() -> Minecraft.getInstance().screen instanceof com.itmecasey.barelygravestone.client.ObituaryScreen, 100);
        shoot("09_obituary_screen", 25);
        then(2, () -> onServer(server -> {
            var level = GraveService.levelOf(server, grave.dimension());
            if (level.getBlockEntity(new BlockPos(grave.x(), grave.y(), grave.z())) instanceof GraveBlockEntity be && !be.items().isEmpty()) {
                be.items().remove(0);
                be.contentsChanged();
            }
        }));
        shoot("10_obituary_after_one_taken", 25);
        then(2, () -> Minecraft.getInstance().setScreen(null));

        // Collect with an empty hand: the real right-click path.
        then(5, () -> {
            LocalPlayer player = Minecraft.getInstance().player;
            int empty = 8;
            for (int slot = 8; slot >= 0; slot--) {
                if (player.getInventory().getItem(slot).isEmpty()) {
                    empty = slot;
                    break;
                }
            }
            player.getInventory().selected = empty;
            server("tp @p " + (grave.x() + 0.5) + " " + grave.y() + " " + (grave.z() + 2.5) + " 180 20");
        });
        then(15, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            BlockPos pos = new BlockPos(grave.x(), grave.y(), grave.z());
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
            LOGGER.info("GRAVE-SHOTS right-click with an empty hand, main hand empty={}", minecraft.player.getMainHandItem().isEmpty());
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        });
        until(() -> GraveStores.registry().all().isEmpty(), 100);
        then(20, () -> Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player)));
        shoot("11_inventory_after_collect", 15);
        then(2, () -> Minecraft.getInstance().setScreen(null));
        shoot("12_grave_gone", 25);
        then(5, () -> Minecraft.getInstance().gameMode.useItem(Minecraft.getInstance().player, InteractionHand.MAIN_HAND));
        then(10, () -> {
            LocalPlayer player = Minecraft.getInstance().player;
            for (int slot = 0; slot < 9; slot++) {
                if (player.getInventory().getItem(slot).is(ModContent.OBITUARY)) {
                    player.getInventory().selected = slot;
                }
            }
            Minecraft.getInstance().gameMode.useItem(player, InteractionHand.MAIN_HAND);
        });
        until(() -> Minecraft.getInstance().screen instanceof com.itmecasey.barelygravestone.client.ObituaryScreen, 100);
        shoot("13_obituary_after_collect", 25);
        then(2, () -> Minecraft.getInstance().setScreen(null));

        then(20, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.options.pauseOnLostFocus = pauseOnLostFocus;
            LOGGER.info("GRAVE-SHOTS DONE");
            stage = Stage.DONE;
            minecraft.stop();
        });
    }

    // ---- helpers ----

    private static void then(int delay, Runnable action) {
        STEPS.add(new Step(delay, action, null, 0));
    }

    private static void until(BooleanSupplier ready, int timeoutTicks) {
        STEPS.add(new Step(1, () -> {
        }, ready, timeoutTicks));
    }

    private static void look(Vec3 eye, Vec3 target) {
        Vec3 d = target.subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        server(String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.2f %.2f", eye.x, eye.y - 1.62, eye.z, yaw, pitch));
        LocalPlayer player = Minecraft.getInstance().player;
        player.moveTo(eye.x, eye.y - 1.62, eye.z, yaw, pitch);
        player.setYHeadRot(yaw);
        player.setDeltaMovement(Vec3.ZERO);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    private static void shoot(String name, int delay) {
        then(delay, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gui.getChat().clearMessages(false);
            minecraft.getToasts().clear();
        });
        then(2, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            LOGGER.info("GRAVE-SHOTS {}", name);
            Screenshot.grab(minecraft.gameDirectory, "barelygravestone_" + name + ".png", minecraft.getMainRenderTarget(),
                    message -> LOGGER.info("GRAVE-SHOTS saved {}", name));
        });
    }

    private static void onServer(java.util.function.Consumer<MinecraftServer> action) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> action.accept(server));
        }
    }

    private static void server(String... commands) {
        onServer(server -> {
            for (String command : commands) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
            }
        });
    }

    /** Keeps the compiler honest about the stack import used for the held-item checks. */
    @SuppressWarnings("unused")
    private static boolean isEmpty(ItemStack stack) {
        return stack.isEmpty();
    }
}
