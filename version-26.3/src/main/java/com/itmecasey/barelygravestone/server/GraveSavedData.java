package com.itmecasey.barelygravestone.server;

import com.itmecasey.barelygravestone.GraveMod;
import com.itmecasey.barelygravestone.core.WorldState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** The mod's world data in the standard saved-data store of the overworld: it saves, backs up and copies with the world. */
public final class GraveSavedData extends SavedData {
    public static final Codec<GraveSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("graves", List.of()).forGetter(d -> d.state.graveLines()),
            Codec.STRING.listOf().optionalFieldOf("pending", List.of()).forGetter(d -> d.state.pendingLines()),
            Codec.BOOL.optionalFieldOf("legacy_imported", false).forGetter(d -> d.state.legacyImported()))
            .apply(i, GraveSavedData::new));

    public static final SavedDataType<GraveSavedData> TYPE = new SavedDataType<>(GraveMod.id("graves"), GraveSavedData::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

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
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }
}
