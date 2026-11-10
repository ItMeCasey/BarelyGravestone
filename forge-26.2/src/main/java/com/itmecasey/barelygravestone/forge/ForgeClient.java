package com.itmecasey.barelygravestone.forge;

import com.itmecasey.barelygravestone.client.ClientHooks;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Client-only setup. The events only fire on a client, so a dedicated server never touches the client classes. */
final class ForgeClient {
    private ForgeClient() {
    }

    static void install(FMLJavaModLoadingContext context) {
        FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(event -> event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(com.itmecasey.barelygravestone.ModContent.obituaryMenu, com.itmecasey.barelygravestone.client.ObituaryScreen::new)));
        // Keeps map-mod death markers in step with the Obituaries the player carries.
        net.minecraftforge.event.TickEvent.ClientTickEvent.Post.BUS.addListener(tick -> com.itmecasey.barelygravestone.compat.MarkerSync.tick());
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(event ->
                event.registerBlockEntityRenderer(ClientHooks.graveType(), ClientHooks.rendererProvider()));
    }
}
