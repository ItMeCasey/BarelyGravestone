package com.itmecasey.barelygravestone.server;

import com.itmecasey.barelygravestone.GraveMod;
import com.itmecasey.barelygravestone.core.GraveRegistry;
import com.itmecasey.barelygravestone.core.PendingStore;
import com.itmecasey.barelygravestone.core.WorldState;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The grave index and the waiting Obituaries, kept in the world data ({@link GraveSavedData}). The files of versions up to
 * 2026.11.2 ({@code <world>/barelygravestone/*.txt}) are imported once and then left alone.
 */
public final class GraveStores {
    private static GraveRegistry registry = new GraveRegistry();
    private static PendingStore pending = new PendingStore();

    private GraveStores() {
    }

    /** Opens the world data when a server starts, importing the older files if this world has not been imported yet. */
    public static void open(MinecraftServer server) {
        WorldState state = GraveSavedData.of(server).state();
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve("barelygravestone");
        WorldState.Import result = state.importLegacy(folder);
        switch (result.outcome()) {
            case IMPORTED -> GraveMod.LOGGER.info("Imported {} graves and {} waiting Obituaries from {} ({} unreadable lines skipped); the old files stay as they are",
                    result.graves(), result.pending(), folder, result.skipped());
            case STORE_NOT_EMPTY -> GraveMod.LOGGER.warn("Found old grave files in {} but the world data already holds graves; the old files were not imported", folder);
            case UNREADABLE -> GraveMod.LOGGER.error("Could not read the old grave files in {}; they will be tried again at the next start", folder);
            default -> { }
        }
        registry = state.registry();
        pending = state.pending();
        ScriptViews.attach(server);
    }

    /** Forgets the world data when the server stops, so a single-player session does not leak into the next world. */
    public static void close() {
        registry = new GraveRegistry();
        pending = new PendingStore();
        ScriptViews.detach();
    }

    public static GraveRegistry registry() {
        return registry;
    }

    public static PendingStore pending() {
        return pending;
    }

    /** A grave block is gone for good. */
    public static void graveRemoved(UUID graveId) {
        registry.remove(graveId);
    }
}
