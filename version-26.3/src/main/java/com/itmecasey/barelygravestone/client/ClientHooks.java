package com.itmecasey.barelygravestone.client;

import com.itmecasey.barelygravestone.ModContent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Client-only registrations the loader layers call from their client setup. */
public final class ClientHooks {
    private ClientHooks() {
    }

    /** Pairs the Obituary menu with its screen. */
    public static void registerScreens() {
        MenuScreens.register(ModContent.obituaryMenu, ObituaryScreen::new);
    }

    /** The block entity type that gets the head renderer. */
    public static BlockEntityType<com.itmecasey.barelygravestone.block.GraveBlockEntity> graveType() {
        return ModContent.GRAVE_BE;
    }

    /** The head renderer's provider. */
    public static BlockEntityRendererProvider<com.itmecasey.barelygravestone.block.GraveBlockEntity, GraveRenderer.State> rendererProvider() {
        return GraveRenderer::new;
    }
}
