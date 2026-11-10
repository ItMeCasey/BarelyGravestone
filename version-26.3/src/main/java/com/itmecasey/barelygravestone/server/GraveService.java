package com.itmecasey.barelygravestone.server;

import com.itmecasey.barelygravestone.GraveMod;
import com.itmecasey.barelygravestone.ModContent;
import com.itmecasey.barelygravestone.block.GraveBlock;
import com.itmecasey.barelygravestone.block.GraveBlockEntity;
import com.itmecasey.barelygravestone.core.Access;
import com.itmecasey.barelygravestone.core.GraveRegistry;
import com.itmecasey.barelygravestone.core.GravePlan;
import com.itmecasey.barelygravestone.core.GravePlanner;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import com.itmecasey.barelygravestone.core.Settings;
import com.itmecasey.barelygravestone.core.TimeText;
import com.itmecasey.barelygravestone.core.XpMath;
import com.itmecasey.barelygravestone.item.ObituaryData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/** The server-side rules: making a grave at death, collecting it, removing it, handing out Obituaries. */
public final class GraveService {
    private GraveService() {
    }

    // ---- death ----

    /**
     * Makes a grave for a dying player holding these stacks (copies; the caller clears the inventory when this succeeds).
     * Returns the grave, or {@code null} when there is nothing to keep or no place for it, in which case nothing was changed.
     */
    public static GraveBlockEntity createGrave(ServerPlayer player, List<ItemStack> stacks) {
        if (stacks.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        BlockPos death = player.blockPosition();
        GravePlan plan = GravePlanner.plan(new LevelTerrain(level), death.getX(), death.getY(), death.getZ());
        if (plan == null) {
            GraveMod.LOGGER.warn("No place for a grave near {} in {}; {}'s items drop normally", death, level.dimension(), player.getGameProfile().name());
            return null;
        }
        BlockPos gravePos = new BlockPos(plan.x(), plan.y(), plan.z());
        if (plan.support()) {
            level.setBlock(gravePos.below(), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(gravePos, ModContent.GRAVE.defaultBlockState()
                .setValue(GraveBlock.FACING, Direction.fromYRot(player.getYRot())), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(gravePos) instanceof GraveBlockEntity grave)) {
            GraveMod.LOGGER.error("The grave block at {} has no block entity; items drop normally", gravePos);
            level.removeBlock(gravePos, false);
            return null;
        }

        // The price: a share of the player's XP levels, optionally kept in the grave.
        int levels = player.experienceLevel;
        int cost = XpMath.levelsTaken(levels, Settings.xpCostPercent());
        int points = cost > 0 ? XpMath.pointsTaken(levels, cost) : 0;
        if (cost > 0) {
            player.giveExperienceLevels(-cost);
        }
        int stored = Settings.keepLostXp() ? points : 0;

        UUID graveId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        String dimension = level.dimension().identifier().toString();
        ObituaryRecord record = new ObituaryRecord(graveId, player.getUUID(), player.getGameProfile().name(),
                player.getCombatTracker().getDeathMessage().getString(), now,
                TimeText.gameDay(level.getDefaultClockTime()), dimension, gravePos.getX(), gravePos.getY(), gravePos.getZ(),
                cost, stored);
        grave.fill(graveId, player.getUUID(), player.getGameProfile().name(), player.getGameProfile(), stacks, stored,
                level.getGameTime(), record);
        GraveStores.registry().add(new GraveRegistry.Entry(graveId, player.getUUID(), player.getGameProfile().name(),
                dimension, gravePos.getX(), gravePos.getY(), gravePos.getZ(), now));
        GraveStores.pending().add(player.getUUID(), record.encode());
        GraveMod.LOGGER.info("Grave {} for {} at {} {} {} in {} holds {} stacks", graveId, record.ownerName(), gravePos.getX(),
                gravePos.getY(), gravePos.getZ(), dimension, stacks.size());
        return grave;
    }

    // ---- Obituaries ----

    /** Hands the player every Obituary that is waiting for them; what does not fit stays stored. */
    public static void deliverObituaries(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }
        var pending = GraveStores.pending();
        int given = 0;
        while (pending.has(player.getUUID())) {
            String line = pending.peek(player.getUUID()).get(0);
            ObituaryRecord record = ObituaryRecord.decode(line);
            if (record == null) {
                pending.poll(player.getUUID());
                continue;
            }
            ItemStack stack = ObituaryData.create(record);
            if (!player.getInventory().add(stack)) {
                break;
            }
            pending.poll(player.getUUID());
            given++;
        }
        if (given > 0) {
            tell(player, Component.translatable("message.barelygravestone.obituary_given", given));
        }
    }

    // ---- collecting ----

    /** Sends a chat message; players without a connection (the headless self test's stand-in) just get nothing. */
    public static void tell(ServerPlayer player, Component message) {
        if (player.connection != null) {
            player.sendSystemMessage(message);
        }
    }

    /** Whether the player may collect this grave right now. */
    public static boolean canCollect(ServerPlayer player, GraveBlockEntity grave) {
        boolean owner = player.getUUID().equals(grave.owner());
        boolean privileged = isPrivileged(player);
        boolean team = !owner && !privileged && Settings.teamMembersCanOpen()
                && TeamLinks.sameTeam(player, grave.owner(), grave.ownerName());
        return Access.canCollect(Settings.collectBy(), owner, team, Settings.teamMembersCanOpen(), privileged);
    }

    /** Test hook: when set, decides who counts as an operator (a game test server gives every player all permissions). */
    public static java.util.function.Predicate<ServerPlayer> privilegeProbe;

    /** Operators and creative players can always open a grave. */
    public static boolean isPrivileged(ServerPlayer player) {
        if (privilegeProbe != null) {
            return privilegeProbe.test(player);
        }
        PermissionSet permissions = player.permissions();
        return player.isCreative() || Commands.LEVEL_GAMEMASTERS.check(permissions);
    }

    /** Right-click with an empty hand. */
    public static void collect(ServerPlayer player, ServerLevel level, BlockPos pos, GraveBlockEntity grave) {
        if (!canCollect(player, grave)) {
            tell(player, Component.translatable("message.barelygravestone.not_yours", grave.ownerName())
                    .withStyle(ChatFormatting.RED));
            return;
        }
        giveContents(player, grave);
        if (grave.isEmpty()) {
            level.removeBlock(pos, false);
            tell(player, Component.translatable("message.barelygravestone.collected"));
        } else {
            tell(player, Component.translatable("message.barelygravestone.partial", grave.items().size())
                    .withStyle(ChatFormatting.YELLOW));
        }
        GraveEvents.refreshObituaries(player);
    }

    /** Moves as much of the grave as fits into the player's inventory, armor first; the rest stays in the grave. */
    public static int giveContents(ServerPlayer player, GraveBlockEntity grave) {
        int moved = 0;
        for (ItemStack stack : grave.items()) {
            int before = stack.getCount();
            EquipmentSlot slot = player.getEquipmentSlotForItem(stack);
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && player.getItemBySlot(slot).isEmpty()) {
                player.setItemSlot(slot, stack.copyWithCount(1));
                stack.shrink(1);
            }
            if (!stack.isEmpty()) {
                player.getInventory().add(stack);
            }
            moved += before - stack.getCount();
        }
        grave.contentsChanged();
        int xp = grave.takeXp();
        if (xp > 0) {
            player.giveExperiencePoints(xp);
        }
        return moved;
    }

    // ---- lookup and removal (commands, Obituary screen) ----

    /** The level of a dimension id, or {@code null}. */
    public static @Nullable ServerLevel levelOf(MinecraftServer server, String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** The grave block entity of an index entry; loads its chunk if needed. Returns {@code null} when the block is not there. */
    public static @Nullable GraveBlockEntity find(MinecraftServer server, GraveRegistry.Entry entry) {
        ServerLevel level = levelOf(server, entry.dimension());
        if (level == null) {
            return null;
        }
        BlockPos pos = new BlockPos(entry.x(), entry.y(), entry.z());
        level.getChunk(pos);
        return level.getBlockEntity(pos) instanceof GraveBlockEntity grave && grave.graveId().equals(entry.id()) ? grave : null;
    }

    /** Removes a grave; with {@code dropItems} its contents fall at the grave, otherwise they are gone. */
    public static void remove(MinecraftServer server, GraveRegistry.Entry entry, boolean dropItems) {
        GraveBlockEntity grave = find(server, entry);
        ServerLevel level = levelOf(server, entry.dimension());
        if (grave != null && level != null) {
            if (!dropItems) {
                grave.items().clear();
                grave.takeXp();
            }
            level.removeBlock(new BlockPos(entry.x(), entry.y(), entry.z()), false);
        }
        GraveStores.registry().remove(entry.id());
    }

    /** Gives a grave's contents to a player no matter who owns it (the restore command); returns the stacks moved. */
    public static int restore(MinecraftServer server, GraveRegistry.Entry entry, ServerPlayer target) {
        GraveBlockEntity grave = find(server, entry);
        ServerLevel level = levelOf(server, entry.dimension());
        if (grave == null || level == null) {
            return -1;
        }
        int moved = giveContents(target, grave);
        for (ItemStack rest : new ArrayList<>(grave.items())) {
            net.minecraft.world.level.block.Block.popResource(target.level(), target.blockPosition(), rest.copy());
        }
        grave.items().clear();
        grave.contentsChanged();
        level.removeBlock(new BlockPos(entry.x(), entry.y(), entry.z()), false);
        GraveEvents.refreshObituaries(target);
        return moved;
    }

    /** True if a level is the dimension of an Obituary record (used for the particle trail). */
    public static boolean sameDimension(Level level, ObituaryRecord record) {
        return level.dimension().identifier().toString().equals(record.dimension());
    }
}
