package com.itmecasey.barelygravestone.compat;

import com.itmecasey.barelygravestone.GraveMod;
import com.itmecasey.barelygravestone.ModContent;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import com.itmecasey.barelygravestone.core.Settings;
import com.itmecasey.barelygravestone.item.ObituaryData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps map-mod death markers in step with the Obituaries the local player carries. It reads the player's inventory (the server
 * marks an Obituary as gone when its grave is emptied), so no network code is needed. Each map-mod integration installs a
 * {@link Sink}; with none installed this does nothing. Client side only.
 */
public final class MarkerSync {
    /** What a map mod can do with a marker. */
    public interface Sink {
        /** Shows a marker; returns {@code false} when it cannot be shown right now (it is then tried again a second later). */
        boolean show(ObituaryRecord record);

        void remove(UUID graveId);
    }

    private static final Map<Sink, Map<UUID, ObituaryRecord>> SHOWN = new HashMap<>();
    private static final List<Sink> SINKS = new ArrayList<>();
    private static final java.util.Set<UUID> KNOWN = new java.util.HashSet<>();
    private static boolean bootstrapped;
    private static int counter;

    private MarkerSync() {
    }

    /** Called by a map-mod integration once its API is ready. */
    public static void install(Sink sink) {
        SINKS.add(sink);
        SHOWN.put(sink, new HashMap<>());
    }

    /** Called every client tick by the loader layer. */
    public static void tick() {
        if (!bootstrapped) {
            bootstrapped = true;
            bootstrap();
        }
        if (SINKS.isEmpty() || ++counter % 20 != 0) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        Map<UUID, ObituaryRecord> wanted = new LinkedHashMap<>();
        if (player != null && Settings.deathMarker()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(ModContent.OBITUARY) && !ObituaryData.gone(stack)) {
                    ObituaryRecord record = ObituaryData.read(stack);
                    if (record != null) {
                        wanted.put(record.graveId(), record);
                    }
                }
            }
        }
        // Graves that were wanted a moment ago and are not any more: every sink drops them, even one that only got part way.
        for (UUID id : new ArrayList<>(KNOWN)) {
            if (!wanted.containsKey(id)) {
                for (Sink sink : SINKS) {
                    SHOWN.get(sink).remove(id);
                    sink.remove(id);
                }
                KNOWN.remove(id);
            }
        }
        KNOWN.addAll(wanted.keySet());
        for (Sink sink : SINKS) {
            Map<UUID, ObituaryRecord> shown = SHOWN.get(sink);
            for (ObituaryRecord record : wanted.values()) {
                if (!shown.containsKey(record.graveId()) && sink.show(record)) {
                    shown.put(record.graveId(), record);
                }
            }
        }
    }

    /** Map mods that are found by class name rather than announcing themselves (JourneyMap announces itself). */
    private static void bootstrap() {
        try {
            Class.forName("hunternif.mc.api.AtlasAPI", false, MarkerSync.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return;
        }
        try {
            Class.forName("com.itmecasey.barelygravestone.compat.antiqueatlas.AntiqueAtlasMarkers").getMethod("install").invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            // A build without the integration class (there is no Antique Atlas for 26.x) lands here.
            GraveMod.LOGGER.info("Antique Atlas found, but this build has no marker integration for it ({})", e.toString());
        } catch (RuntimeException e) {
            GraveMod.LOGGER.warn("Antique Atlas was found but its markers could not be set up", e);
        }
    }

    /** The markers currently shown across all map mods (for the dev tour's check). */
    public static int shownCount() {
        int total = 0;
        for (Map<UUID, ObituaryRecord> shown : SHOWN.values()) {
            total += shown.size();
        }
        return total;
    }
}
