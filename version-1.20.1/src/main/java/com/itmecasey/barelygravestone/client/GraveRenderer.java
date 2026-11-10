package com.itmecasey.barelygravestone.client;

import com.itmecasey.barelygravestone.block.GraveBlock;
import com.itmecasey.barelygravestone.block.GraveBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.RenderType;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.level.block.SkullBlock;

/**
 * Draws the dead player's head on top of the pike, with the skin from the game profile stored in the grave. A profile with
 * no skin data (an offline name) gets the default skin for its id, exactly like a player head item.
 */
public class GraveRenderer implements BlockEntityRenderer<GraveBlockEntity> {
    private final SkullModelBase model;

    public GraveRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new SkullModel(context.bakeLayer(ModelLayers.PLAYER_HEAD));
    }

    @Override
    public void render(GraveBlockEntity grave, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        GameProfile profile = grave.profile();
        RenderType type = SkullBlockRenderer.getRenderType(SkullBlock.Types.PLAYER, profile);
        // The head looks the way the player did.
        float yRot = (grave.getBlockState().getValue(GraveBlock.FACING).get2DDataValue() * 4 + 8 & 15) * 22.5F;
        pose.pushPose();
        // The head sits on top of the pike: half a block up.
        pose.translate(0.0F, 0.5F, 0.0F);
        SkullBlockRenderer.renderSkull(null, yRot, 0.0F, pose, buffers, light, model, type);
        pose.popPose();
    }
}
