package com.itmecasey.barelygravestone.server;

import com.itmecasey.barelygravestone.core.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Lets the placement planner look at a real level. */
public final class LevelTerrain implements Terrain {
    private final ServerLevel level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public LevelTerrain(ServerLevel level) {
        this.level = level;
    }

    private BlockState at(int x, int y, int z) {
        return level.getBlockState(cursor.set(x, y, z));
    }

    @Override
    public int minY() {
        return level.getMinBuildHeight();
    }

    @Override
    public int maxY() {
        return level.getMaxBuildHeight();
    }

    @Override
    public boolean fluid(int x, int y, int z) {
        BlockState state = at(x, y, z);
        return state.getBlock() instanceof LiquidBlock || state.is(Blocks.BUBBLE_COLUMN);
    }

    @Override
    public boolean replaceable(int x, int y, int z) {
        BlockState state = at(x, y, z);
        if (state.isAir()) {
            return true;
        }
        return state.canBeReplaced() && state.getFluidState().isEmpty() && !state.hasBlockEntity();
    }

    @Override
    public boolean solidTop(int x, int y, int z) {
        BlockState state = at(x, y, z);
        if (state.isAir() || fluid(x, y, z) || state.hasBlockEntity()) {
            return false;
        }
        return state.isFaceSturdy(level, cursor.set(x, y, z), Direction.UP);
    }
}
