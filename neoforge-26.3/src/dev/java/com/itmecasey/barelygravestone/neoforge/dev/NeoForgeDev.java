package com.itmecasey.barelygravestone.neoforge.dev;

import com.itmecasey.barelygravestone.dev.SelfTest;
import com.itmecasey.barelygravestone.dev.ShotTour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Development hooks for NeoForge: the server self test and the client screenshot tour. Built only with -PdevTools. */
public final class NeoForgeDev {
    private NeoForgeDev() {
    }

    public static void install() {
        if (SelfTest.requested()) {
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
                SelfTest.run(e.getServer());
                e.getServer().halt(false);
            });
        }
        if (FMLEnvironment.getDist() == Dist.CLIENT && System.getenv("BARELYGRAVESTONE_SHOTS") != null) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ShotTour.tick());
        }
    }
}
