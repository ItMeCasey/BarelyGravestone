package com.itmecasey.barelygravestone.neoforge;

import com.itmecasey.barelygravestone.GraveMod;
import com.itmecasey.barelygravestone.ModContent;
import com.itmecasey.barelygravestone.client.ClientHooks;
import com.itmecasey.barelygravestone.client.ObituaryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only setup: the head renderer and the Obituary screen. Never loaded on a dedicated server. */
@EventBusSubscriber(modid = GraveMod.MODID, value = Dist.CLIENT)
public final class NeoForgeClient {
    private NeoForgeClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ClientHooks.graveType(), ClientHooks.rendererProvider());
    }

    /** Keeps map-mod death markers in step with the Obituaries the player carries. */
    @SubscribeEvent
    public static void clientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        com.itmecasey.barelygravestone.compat.MarkerSync.tick();
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
        event.register(ModContent.obituaryMenu, ObituaryScreen::new);
    }
}
