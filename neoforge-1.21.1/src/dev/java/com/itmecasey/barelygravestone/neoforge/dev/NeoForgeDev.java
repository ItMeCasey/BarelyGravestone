package com.itmecasey.barelygravestone.neoforge.dev;

import com.itmecasey.barelygravestone.dev.SelfTest;
import com.itmecasey.barelygravestone.dev.ShotTour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Development hooks for NeoForge: the server self test and the client screenshot tour. Built only with -PdevTools. */
public final class NeoForgeDev {
    private NeoForgeDev() {
    }

    public static void install(net.neoforged.bus.api.IEventBus modBus) {
        if (SelfTest.requested()) {
            // 1.21.1's game test server refuses to start with no tests, so the self test lives in one game test.
            modBus.addListener((net.neoforged.neoforge.event.RegisterGameTestsEvent e) -> e.register(SelfTestCases.class));
        }
        if (FMLEnvironment.dist == Dist.CLIENT && System.getenv("BARELYGRAVESTONE_SHOTS") != null) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ShotTour.tick());
        }
    }
}
