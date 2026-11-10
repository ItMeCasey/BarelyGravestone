package com.itmecasey.barelygravestone.server;

import com.itmecasey.barelygravestone.GraveMod;
import com.itmecasey.barelygravestone.ModContent;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import com.itmecasey.barelygravestone.item.ObituaryData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.itmecasey.barelygravestone.block.GraveBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** The entry points the loader layers call from their event hooks. The game logic itself is in {@link GraveService}. */
public final class GraveEvents {
    private static int tickCounter;

    private GraveEvents() {
    }

    /** The player who died most recently, with the grave made for them, so late drops (Curios and others) can be added to it. */
    private static final Map<UUID, GraveBlockEntity> RECENT = new HashMap<>();

    /**
     * A player is dying (before the game drops anything). Everything they carry goes into a grave and out of the inventory, so
     * vanilla has nothing left to drop. If there is no room for a grave the inventory is left alone.
     */
    public static void onPlayerDeath(ServerPlayer player) {
        try {
            if (!(player.level() instanceof ServerLevel level) || level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
                return;
            }
            List<ItemStack> stacks = new ArrayList<>();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    stacks.add(stack.copy());
                }
            }
            GraveBlockEntity grave = GraveService.createGrave(player, stacks);
            if (grave != null) {
                player.getInventory().clearContent();
                RECENT.put(player.getUUID(), grave);
            }
        } catch (RuntimeException e) {
            // Never lose a player's items to a bug: leave the inventory alone and let the game drop it.
            GraveMod.LOGGER.error("Making the grave for {} failed; the items drop normally", player.getGameProfile().name(), e);
        }
    }

    /**
     * The game is about to drop the rest of a dead player's loot: what other mods added (Curios slots, for instance). Anything
     * that arrives right after the player's grave was made is put into that grave too.
     */
    public static void onPlayerDrops(ServerPlayer player, Collection<ItemEntity> drops) {
        GraveBlockEntity grave = RECENT.get(player.getUUID());
        if (grave == null || drops.isEmpty() || grave.isRemoved()) {
            return;
        }
        for (ItemEntity entity : drops) {
            if (!entity.getItem().isEmpty()) {
                grave.items().add(entity.getItem().copy());
            }
        }
        grave.contentsChanged();
        GraveMod.LOGGER.info("Added {} more stacks from other mods to the grave of {}", drops.size(), player.getGameProfile().name());
        drops.clear();
    }

    public static void onRespawn(ServerPlayer player) {
        GraveService.deliverObituaries(player);
    }

    public static void onLogin(ServerPlayer player) {
        GraveService.deliverObituaries(player);
        refreshObituaries(player);
    }

    public static void onServerStarting(MinecraftServer server) {
        GraveStores.open(server);
        TeamMods.install();
    }

    public static void onServerStopped() {
        GraveStores.close();
    }

    /** Every tick on the server: once a second, hand over Obituaries that were waiting; every five seconds, refresh them. */
    public static void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 20 != 0) {
            return;
        }
        RECENT.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (GraveStores.pending().has(player.getUUID())) {
                GraveService.deliverObituaries(player);
            }
            if (tickCounter % 100 == 0) {
                refreshObituaries(player);
            }
        }
    }

    /** Marks Obituaries in the player's inventory whose grave no longer exists, so the map markers go away. */
    public static void refreshObituaries(ServerPlayer player) {
        var inventory = player.getInventory();
        boolean changed = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModContent.OBITUARY) && !ObituaryData.gone(stack)) {
                ObituaryRecord record = ObituaryData.read(stack);
                if (record != null && !GraveStores.registry().contains(record.graveId())) {
                    ObituaryData.markGone(stack);
                    changed = true;
                }
            }
        }
        if (changed) {
            // A change made inside a stack is not always noticed by the inventory sync, so send the inventory again.
            player.inventoryMenu.sendAllDataToRemote();
        }
    }
}
