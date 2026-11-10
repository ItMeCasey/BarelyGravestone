package com.itmecasey.barelygravestone.block;

import com.itmecasey.barelygravestone.ModContent;
import com.itmecasey.barelygravestone.server.GraveService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The gravestone: a low stone base, a thin pike and, on top of the pike, the dead player's head (drawn by the block entity
 * renderer from the stored profile). It cannot be broken in survival; an empty hand collects the items and the grave
 * removes itself when nothing is left.
 */
public class GraveBlock extends BaseEntityBlock {
    /** The way the head looks: the way the player was facing when they died. */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3.0, 0.0, 3.0, 13.0, 2.0, 13.0),
            Block.box(7.0, 2.0, 7.0, 9.0, 9.0, 9.0),
            Block.box(4.0, 8.0, 4.0, 12.0, 16.0, 12.0));

    public GraveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GraveBlockEntity(pos, state);
    }

    /** Right-click with an empty hand collects. A hand that holds something passes, so an Obituary in hand still opens. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof GraveBlockEntity grave) {
            GraveService.collect(serverPlayer, (ServerLevel) level, pos, grave);
        }
        return InteractionResult.SUCCESS;
    }

    /** Expiry is checked once a second on the server. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModContent.GRAVE_BE, GraveBlockEntity::serverTick);
    }
}
