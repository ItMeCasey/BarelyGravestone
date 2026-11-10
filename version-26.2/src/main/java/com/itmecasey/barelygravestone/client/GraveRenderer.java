package com.itmecasey.barelygravestone.client;

import com.itmecasey.barelygravestone.block.GraveBlock;
import com.itmecasey.barelygravestone.block.GraveBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the dead player's head on top of the pike, with the skin from the game profile stored in the grave. A profile with
 * no skin data (an offline name) gets the default skin for its id, exactly like a player head item.
 */
public class GraveRenderer implements BlockEntityRenderer<GraveBlockEntity, GraveRenderer.State> {
    /** What the renderer needs from the block entity, copied once per frame. */
    public static class State extends BlockEntityRenderState {
        RenderType renderType;
        Transformation transformation;
    }

    private final SkullModelBase model;
    private final PlayerSkinRenderCache skins;

    public GraveRenderer(BlockEntityRendererProvider.Context context) {
        this.model = SkullBlockRenderer.createModel(context.entityModelSet(), SkullBlock.Types.PLAYER);
        this.skins = context.playerSkinRenderCache();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GraveBlockEntity grave, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(grave, state, partialTick, cameraPos, crumbling);
        ResolvableProfile profile = grave.profile();
        state.renderType = profile == null
                ? SkullBlockRenderer.getSkullRenderType(SkullBlock.Types.PLAYER, null)
                : skins.getOrDefault(profile).renderType();
        // Skull rotations count sixteenths of a turn; the head looks the way the player did.
        int rotation = (grave.getBlockState().getValue(GraveBlock.FACING).get2DDataValue() * 4 + 8) & 15;
        state.transformation = SkullBlockRenderer.TRANSFORMATIONS.freeTransformations(rotation);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        // The head sits on top of the pike: half a block up.
        pose.translate(0.0F, 0.5F, 0.0F);
        pose.mulPose(state.transformation);
        SkullBlockRenderer.submitSkull(0.0F, pose, collector, state.lightCoords, model, state.renderType, 0, state.breakProgress);
        pose.popPose();
    }
}
