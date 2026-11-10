package com.itmecasey.barelygravestone.compat;

import com.itmecasey.barelygravestone.ModContent;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import com.itmecasey.barelygravestone.core.Settings;
import com.itmecasey.barelygravestone.item.ObituaryData;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps map-mod death markers in step with the Obituaries the local player carries. It reads the player's inventory (the server
 * marks an Obituary as gone when its grave is emptied), so no network code is needed. A map-mod integration installs a
 * {@link Sink}; with none installed this does nothing. Client side only.
 */
public final class MarkerSync {
    /** What a map mod can do with a marker. */
    public interface Sink {
        void show(ObituaryRecord record);

        void remove(UUID graveId);
    }

    private static final Map<UUID, ObituaryRecord> SHOWN = new HashMap<>();
    private static Sink sink;
    private static int counter;

    private MarkerSync() {
    }

    /** Called by a map-mod plugin once its API is ready. */
    public static void install(Sink newSink) {
        sink = newSink;
        SHOWN.clear();
    }

    /** Called every client tick by the loader layer. */
    public static void tick() {
        if (sink == null || ++counter % 20 != 0) {
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
        if (wanted.size() != SHOWN.size()) {
            com.itmecasey.barelygravestone.GraveMod.LOGGER.info("Marker sync: {} wanted, {} shown", wanted.size(), SHOWN.size());
        }
        for (UUID id : new java.util.ArrayList<>(SHOWN.keySet())) {
            if (!wanted.containsKey(id)) {
                SHOWN.remove(id);
                sink.remove(id);
            }
        }
        for (ObituaryRecord record : wanted.values()) {
            if (SHOWN.put(record.graveId(), record) == null) {
                sink.show(record);
            }
        }
    }

    /** The markers currently shown (for the dev tour's check). */
    public static int shownCount() {
        return SHOWN.size();
    }
}
