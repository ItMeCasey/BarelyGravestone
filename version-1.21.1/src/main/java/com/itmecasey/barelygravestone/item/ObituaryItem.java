package com.itmecasey.barelygravestone.item;

import com.itmecasey.barelygravestone.core.GraveRegistry;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import com.itmecasey.barelygravestone.core.Settings;
import com.itmecasey.barelygravestone.menu.ObituaryMenu;
import com.itmecasey.barelygravestone.server.GraveService;
import com.itmecasey.barelygravestone.server.GraveStores;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The Obituary: a record of one death. Right-click opens a book-style screen (the dead player's death message, the time, the
 * place and what is still in the grave). While it is held, particles drift from the player toward the grave.
 */
public class ObituaryItem extends Item {
    /** How far from the player the particle trail reaches. */
    private static final double TRAIL_LENGTH = 6.0;
    /** Small, dim blue-grey dust: easy to follow, never in the way. */
    private static final DustParticleOptions DIM = new DustParticleOptions(new Vector3f(0.37F, 0.47F, 0.60F), 0.5F);

    public ObituaryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        ObituaryRecord record = ObituaryData.read(held);
        if (record == null) {
            return InteractionResultHolder.pass(held);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.success(held);
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
        return InteractionResultHolder.success(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        ObituaryRecord record = ObituaryData.read(stack);
        if (record == null) {
            return;
        }
        lines.add(Component.translatable("tooltip.barelygravestone.obituary.died", record.dimensionName()).withStyle(ChatFormatting.GRAY));
        if (ObituaryData.gone(stack)) {
            lines.add(Component.translatable("tooltip.barelygravestone.obituary.gone").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.translatable("tooltip.barelygravestone.obituary.use").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Server side, in hand: a few particles drift from the player toward the grave, in the grave's own dimension only. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)
                || level.getGameTime() % 8L != 0L || !Settings.obituaryParticles()) {
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
        trail(serverLevel, player, new Vec3(record.x() + 0.5, record.y() + 1.0, record.z() + 0.5));
    }

    /** The particle stream: a short row ahead of the player, each particle drifting slowly toward the grave. */
    public static void trail(ServerLevel level, ServerPlayer player, Vec3 grave) {
        Vec3 from = player.position().add(0.0, 1.0, 0.0);
        Vec3 delta = grave.subtract(from);
        double distance = delta.length();
        if (distance < 1.5) {
            level.sendParticles(player, net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, false, grave.x, grave.y + 0.5, grave.z,
                    3, 0.3, 0.3, 0.3, 0.0);
            return;
        }
        Vec3 direction = delta.scale(1.0 / distance);
        double reach = Math.min(TRAIL_LENGTH, distance - 0.5);
        for (double along = 1.5; along <= reach; along += 2.6) {
            Vec3 at = from.add(direction.scale(along));
            // With a count of zero the offsets are a velocity, so the particle drifts slowly along the line.
            level.sendParticles(player, DIM, false, at.x, at.y, at.z, 0, direction.x, direction.y, direction.z, 0.035);
        }
    }
}
