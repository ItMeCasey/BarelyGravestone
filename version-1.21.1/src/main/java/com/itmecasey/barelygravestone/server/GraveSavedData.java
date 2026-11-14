package com.itmecasey.barelygravestone.server;

import com.itmecasey.barelygravestone.core.WorldState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** The mod's world data in the standard saved-data store of the overworld: it saves, backs up and copies with the world. */
public final class GraveSavedData extends SavedData {
    public static final String NAME = "barelygravestone_graves";

    private final WorldState state;

    public GraveSavedData() {
        this(List.of(), List.of(), false);
    }

    public GraveSavedData(List<String> graves, List<String> pending, boolean legacyImported) {
        this.state = new WorldState(graves, pending, legacyImported);
        this.state.setOnChange(this::setDirty);
    }

    public WorldState state() {
        return state;
    }

    public static GraveSavedData of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(GraveSavedData::new, GraveSavedData::load, null), NAME);
    }

    public static GraveSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new GraveSavedData(read(tag, "graves"), read(tag, "pending"), tag.getBoolean("legacy_imported"));
    }

    private static List<String> read(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getString(i));
        }
        return result;
    }

    private static ListTag write(List<String> lines) {
        ListTag list = new ListTag();
        for (String line : lines) {
            list.add(StringTag.valueOf(line));
        }
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("graves", write(state.graveLines()));
        tag.put("pending", write(state.pendingLines()));
        tag.putBoolean("legacy_imported", state.legacyImported());
        return tag;
    }
}
