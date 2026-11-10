package com.itmecasey.barelygravestone.server;

import com.itmecasey.barelygravestone.core.GraveRegistry;
import com.itmecasey.barelygravestone.core.PendingStore;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** The two small files kept in the world folder: the index of graves and the Obituaries waiting for their players. */
public final class GraveStores {
    private static GraveRegistry registry = new GraveRegistry();
    private static PendingStore pending = new PendingStore();

    private GraveStores() {
    }

    /** Opens both files when a server starts. */
    public static void open(MinecraftServer server) {
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve("barelygravestone");
        registry = GraveRegistry.open(folder.resolve("graves.txt"));
        pending = PendingStore.open(folder.resolve("pending_obituaries.txt"));
    }

    /** Forgets the files when the server stops, so a single-player session does not leak into the next world. */
    public static void close() {
        registry = new GraveRegistry();
        pending = new PendingStore();
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
