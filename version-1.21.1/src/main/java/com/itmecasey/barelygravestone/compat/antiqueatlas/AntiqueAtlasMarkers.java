package com.itmecasey.barelygravestone.compat.antiqueatlas;

import com.itmecasey.barelygravestone.GraveMod;
import com.itmecasey.barelygravestone.compat.MarkerSync;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import hunternif.mc.api.AtlasAPI;
import hunternif.mc.api.client.AtlasClientAPI;
import hunternif.mc.impl.atlas.AntiqueAtlas;
import hunternif.mc.impl.atlas.marker.Marker;
import hunternif.mc.impl.atlas.marker.MarkersData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The Antique Atlas integration: a skull marker at the grave on every atlas the player carries, in the grave's own dimension,
 * removed when the grave is emptied. Antique Atlas keeps markers per atlas, and the atlas you died with is in the grave, so the
 * marker shows on a spare or new atlas. Loaded only when Antique Atlas is installed.
 *
 * <p>The client API only sends a request to the server and returns nothing, so the marker is found again (by position, type and
 * label) in the atlas's synced marker data once the server has created it; that gives the id needed to delete it later.
 */
public final class AntiqueAtlasMarkers implements MarkerSync.Sink {
    private static final ResourceLocation SKULL = ResourceLocation.tryParse("antiqueatlas:skull");

    /** Per grave: atlas id to marker id, once the marker is known. */
    private final Map<UUID, Map<Integer, Integer>> placed = new HashMap<>();
    /** Requests already sent, as grave id plus atlas id, so a marker is never asked for twice. */
    private final Set<String> requested = new HashSet<>();

    private AntiqueAtlasMarkers() {
    }

    /** Called by {@link MarkerSync} when Antique Atlas is found. */
    public static void install() {
        MarkerSync.install(new AntiqueAtlasMarkers());
        GraveMod.LOGGER.info("Antique Atlas found: grave markers are on");
    }

    private static String label(ObituaryRecord record) {
        return "Grave of " + record.ownerName();
    }

    @Override
    public boolean show(ObituaryRecord record) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || minecraft.player == null || SKULL == null
                || !level.dimension().location().toString().equals(record.dimension())) {
            return false;
        }
        List<Integer> atlases = AtlasAPI.getPlayerAtlases(minecraft.player);
        if (atlases.isEmpty()) {
            return false;
        }
        Map<Integer, Integer> found = placed.computeIfAbsent(record.graveId(), id -> new HashMap<>());
        boolean all = true;
        for (int atlas : atlases) {
            if (found.containsKey(atlas)) {
                continue;
            }
            Marker existing = find(atlas, level, record);
            if (existing != null) {
                found.put(atlas, existing.getId());
                GraveMod.LOGGER.info("Antique Atlas marker {} is on atlas {} for grave {}", existing.getId(), atlas, record.graveId());
            } else {
                all = false;
                if (requested.add(record.graveId() + "/" + atlas)) {
                    AtlasClientAPI.getMarkerAPI().putMarker(level, true, atlas, SKULL, Component.literal(label(record)),
                            record.x(), record.z());
                }
            }
        }
        return all;
    }

    private static Marker find(int atlas, Level level, ObituaryRecord record) {
        MarkersData data = AntiqueAtlas.markersData.getMarkersData(atlas, level);
        if (data == null) {
            return null;
        }
        for (Marker marker : data.getMarkersInWorld(level.dimension())) {
            if (marker.getX() == record.x() && marker.getZ() == record.z() && SKULL.equals(marker.getType())
                    && marker.getLabel() != null && label(record).equals(marker.getLabel().getString())) {
                return marker;
            }
        }
        return null;
    }

    @Override
    public void remove(UUID graveId) {
        Map<Integer, Integer> found = placed.remove(graveId);
        requested.removeIf(key -> key.startsWith(graveId.toString()));
        Level level = Minecraft.getInstance().level;
        if (found == null || level == null) {
            return;
        }
        for (Map.Entry<Integer, Integer> pair : new ArrayList<>(found.entrySet())) {
            AtlasClientAPI.getMarkerAPI().deleteMarker(level, pair.getKey(), pair.getValue());
        }
        GraveMod.LOGGER.info("Antique Atlas marker removed for grave {}", graveId);
    }
}
