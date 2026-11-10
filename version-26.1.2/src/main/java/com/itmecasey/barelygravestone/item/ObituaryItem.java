package com.itmecasey.barelygravestone.item;

import com.itmecasey.barelygravestone.core.GraveRegistry;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import com.itmecasey.barelygravestone.core.Settings;
import com.itmecasey.barelygravestone.menu.ObituaryMenu;
import com.itmecasey.barelygravestone.server.GraveService;
import com.itmecasey.barelygravestone.server.GraveStores;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Obituary: a record of one death. Right-click opens a book-style screen (the dead player's death message, the time, the
 * place and what is still in the grave). While it is held, particles drift from the player toward the grave.
 */
public class ObituaryItem extends Item {
    /** How far from the player the particle trail reaches. */
    private static final double TRAIL_LENGTH = 6.0;
    /** Small, dim blue-grey dust: easy to follow, never in the way. */
    private static final net.minecraft.core.particles.DustParticleOptions DIM =
            new net.minecraft.core.particles.DustParticleOptions(0x5F7899, 0.5F);

    public ObituaryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ObituaryRecord record = ObituaryData.read(player.getItemInHand(hand));
        if (record == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            ServerLevel graveLevel = GraveService.levelOf(server, record.dimension());
            BlockPos pos = new BlockPos(record.x(), record.y(), record.z());
            if (graveLevel != null) {
                graveLevel.getChunk(pos);
            }
            ServerLevel target = graveLevel;
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new ObituaryMenu(id, inventory, target, pos, record.graveId()),
                    Component.translatable("gui.barelygravestone.obituary")));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        ObituaryRecord record = ObituaryData.read(stack);
        if (record == null) {
            return;
        }
        lines.accept(Component.translatable("tooltip.barelygravestone.obituary.died", record.dimensionName()).withStyle(net.minecraft.ChatFormatting.GRAY));
        if (ObituaryData.gone(stack)) {
            lines.accept(Component.translatable("tooltip.barelygravestone.obituary.gone").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        } else {
            lines.accept(Component.translatable("tooltip.barelygravestone.obituary.use").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }

    /** Server side, in hand: a few particles drift from the player toward the grave, in the grave's own dimension only. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (!(entity instanceof ServerPlayer player) || level.getGameTime() % 8L != 0L || !Settings.obituaryParticles()) {
            return;
        }
        if (player.getMainHandItem() != stack && player.getOffhandItem() != stack) {
            return;
        }
        ObituaryRecord record = ObituaryData.read(stack);
        if (record == null || ObituaryData.gone(stack) || !GraveService.sameDimension(level, record)) {
            return;
        }
        GraveRegistry.Entry entry = GraveStores.registry().get(record.graveId());
        if (entry == null) {
            return;
        }
        trail(level, player, new Vec3(record.x() + 0.5, record.y() + 1.0, record.z() + 0.5));
    }

    /** The particle stream: a short row ahead of the player, each particle already moving toward the grave. */
    public static void trail(ServerLevel level, ServerPlayer player, Vec3 grave) {
        Vec3 from = player.position().add(0.0, 1.0, 0.0);
        Vec3 delta = grave.subtract(from);
        double distance = delta.length();
        if (distance < 1.5) {
            level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, false, false, grave.x, grave.y + 0.5, grave.z, 3, 0.3, 0.3, 0.3, 0.0);
            return;
        }
        Vec3 direction = delta.scale(1.0 / distance);
        double reach = Math.min(TRAIL_LENGTH, distance - 0.5);
        for (double along = 1.5; along <= reach; along += 2.6) {
            Vec3 at = from.add(direction.scale(along));
            // With a count of zero the offsets are a velocity, so the particle drifts slowly along the line.
            level.sendParticles(player, DIM, false, false, at.x, at.y, at.z, 0,
                    direction.x, direction.y, direction.z, 0.035);
        }
    }
}
