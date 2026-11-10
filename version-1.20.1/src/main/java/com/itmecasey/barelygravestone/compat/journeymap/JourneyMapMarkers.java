package com.itmecasey.barelygravestone.compat.journeymap;

import com.itmecasey.barelygravestone.GraveMod;
import com.itmecasey.barelygravestone.compat.MarkerSync;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.display.Waypoint;
import journeymap.client.api.event.ClientEvent;
import net.minecraft.core.BlockPos;

/**
 * The JourneyMap integration (API 1.x, used by JourneyMap on Minecraft 1.20.1): a waypoint at every grave whose Obituary the
 * player carries, removed when the grave is emptied. JourneyMap finds this class itself and only loads it when JourneyMap is
 * installed.
 */
@ClientPlugin
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
            public boolean show(ObituaryRecord record) {
                try {
                    Waypoint waypoint = new Waypoint(GraveMod.MODID, "Grave of " + record.ownerName(), record.dimension(),
                            new BlockPos(record.x(), record.y(), record.z()));
                    waypoint.setColor(GOLD);
                    waypoint.setPersistent(false);
                    api.show(waypoint);
                    waypoints.put(record.graveId(), waypoint);
                    return true;
                } catch (Exception e) {
                    GraveMod.LOGGER.warn("Could not add the JourneyMap marker for grave {}", record.graveId(), e);
                    return false;
                }
            }

            @Override
            public void remove(UUID graveId) {
                Waypoint waypoint = waypoints.remove(graveId);
                if (waypoint != null) {
                    api.remove(waypoint);
                }
            }
        });
        GraveMod.LOGGER.info("JourneyMap found: grave markers are on");
    }

    @Override
    public void onEvent(ClientEvent event) {
    }
}
