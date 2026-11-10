package com.itmecasey.barelygravestone.client;

import com.itmecasey.barelygravestone.ModContent;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Client-only registrations the loader layers call from their client setup. */
public final class ClientHooks {
    private ClientHooks() {
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
