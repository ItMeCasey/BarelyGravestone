package com.itmecasey.barelygravestone.compat.journeymap;

import com.itmecasey.barelygravestone.GraveMod;
import com.itmecasey.barelygravestone.compat.MarkerSync;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.core.BlockPos;

/**
 * The JourneyMap integration: a waypoint at every grave whose Obituary the player carries, removed when the grave is emptied.
 * JourneyMap finds this class itself and only loads it when JourneyMap is installed.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class JourneyMapMarkers implements IClientPlugin {
    private static final int GOLD = 0xF2B33D;

    private IClientAPI api;
    private final Map<UUID, Waypoint> waypoints = new HashMap<>();

    @Override
    public String getModId() {
        return GraveMod.MODID;
    }

    @Override
    public void initialize(IClientAPI jmClientApi) {
        this.api = jmClientApi;
        MarkerSync.install(new MarkerSync.Sink() {
            @Override
            public void show(ObituaryRecord record) {
                try {
                    Waypoint waypoint = WaypointFactory.createClientWaypoint(GraveMod.MODID,
                            new BlockPos(record.x(), record.y(), record.z()), "Grave of " + record.ownerName(), record.dimension(), false);
                    waypoint.setColor(GOLD);
                    api.addWaypoint(GraveMod.MODID, waypoint);
                    waypoints.put(record.graveId(), waypoint);
                } catch (RuntimeException e) {
                    GraveMod.LOGGER.warn("Could not add the JourneyMap marker for grave {}", record.graveId(), e);
                }
            }

            @Override
            public void remove(UUID graveId) {
                Waypoint waypoint = waypoints.remove(graveId);
                if (waypoint != null) {
                    api.removeWaypoint(GraveMod.MODID, waypoint);
                }
            }
        });
        GraveMod.LOGGER.info("JourneyMap found: grave markers are on");
    }
}
