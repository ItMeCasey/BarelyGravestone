package com.itmecasey.barelygravestone.server;

import com.itmecasey.barelygravestone.block.GraveBlockEntity;
import com.itmecasey.barelygravestone.core.GraveRegistry;
import com.itmecasey.barelygravestone.core.ObituaryRecord;
import com.itmecasey.barelygravestone.core.Settings;
import com.itmecasey.barelygravestone.core.script.GraveView;
import com.itmecasey.barelygravestone.core.script.ScriptApi;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/** Builds the read-only grave snapshots scripts see, and answers the script API's questions from the running server. */
public final class ScriptViews implements ScriptApi.Backend {
    private final MinecraftServer server;

    private ScriptViews(MinecraftServer server) {
        this.server = server;
    }

    /** Connects the script API to a starting server. */
    public static void attach(MinecraftServer server) {
        ScriptApi.attach(new ScriptViews(server));
    }

    /** Disconnects it when the server stops. */
    public static void detach() {
        ScriptApi.detach();
    }

    /** A snapshot of a grave that exists as a block entity (with its items). */
    public static GraveView of(GraveBlockEntity grave, String dimension) {
        ObituaryRecord death = grave.death();
        List<GraveView.GraveItem> items = new ArrayList<>();
        for (ItemStack stack : grave.items()) {
            if (!stack.isEmpty()) {
                items.add(new GraveView.GraveItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
            }
        }
        int left = -1;
        int minutes = Settings.graveExpiryMinutes();
        if (minutes > 0) {
            long ticksLeft = minutes * 1200L - (grave.getLevel() == null ? 0L : grave.getLevel().getGameTime() - grave.createdGameTime());
            left = (int) Math.max(0L, (ticksLeft + 1199L) / 1200L);
        }
        return new GraveView(grave.graveId().toString(), grave.owner().toString(), grave.ownerName(), dimension,
                grave.getBlockPos().getX(), grave.getBlockPos().getY(), grave.getBlockPos().getZ(),
                death == null ? 0L : death.realMillis(), death == null ? "" : death.message(), grave.storedXp(),
                death == null ? 0 : death.xpLevels(), items, true, left);
    }

    /** A snapshot for a grave that is about to be made (before the block exists). */
    public static GraveView preview(String ownerId, String ownerName, String dimension, int x, int y, int z, String deathMessage,
                                    int xp, int xpCostLevels, List<ItemStack> stacks) {
        List<GraveView.GraveItem> items = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                items.add(new GraveView.GraveItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
            }
        }
        int minutes = Settings.graveExpiryMinutes();
        return new GraveView("", ownerId, ownerName, dimension, x, y, z, System.currentTimeMillis(), deathMessage, xp,
                xpCostLevels, items, true, minutes > 0 ? minutes : -1);
    }

    private static GraveView light(GraveRegistry.Entry entry) {
        return new GraveView(entry.id().toString(), entry.owner().toString(), entry.ownerName(), entry.dimension(), entry.x(),
                entry.y(), entry.z(), entry.createdMillis(), "", 0, 0, List.of(), false, -1);
    }

    @Override
    public List<GraveView> list() {
        List<GraveView> result = new ArrayList<>();
        for (GraveRegistry.Entry entry : GraveStores.registry().all()) {
            result.add(light(entry));
        }
        return result;
    }

    private GraveRegistry.Entry entry(String id) {
        String wanted = id.trim().toLowerCase(java.util.Locale.ROOT);
        GraveRegistry.Entry found = null;
        for (GraveRegistry.Entry entry : GraveStores.registry().all()) {
            String text = entry.id().toString();
            if (text.equals(wanted)) {
                return entry;
            }
            if (!wanted.isEmpty() && text.startsWith(wanted)) {
                if (found != null) {
                    return null;
                }
                found = entry;
            }
        }
        return found;
    }

    private GraveView full(GraveRegistry.Entry entry) {
        GraveBlockEntity grave = GraveService.find(server, entry);
        return grave == null ? light(entry) : of(grave, entry.dimension());
    }

    @Override
    public GraveView get(String id) {
        GraveRegistry.Entry entry = entry(id);
        return entry == null ? null : full(entry);
    }

    @Override
    public GraveView at(String dimension, int x, int y, int z) {
        for (GraveRegistry.Entry entry : GraveStores.registry().all()) {
            if (entry.at(dimension, x, y, z)) {
                return full(entry);
            }
        }
        return null;
    }

    @Override
    public boolean remove(String id, boolean dropItems) {
        GraveRegistry.Entry entry = entry(id);
        if (entry == null) {
            return false;
        }
        GraveService.remove(server, entry, dropItems);
        return true;
    }
}
