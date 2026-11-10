package com.itmecasey.barelygravestone;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/** Names shared by every part of Barely Gravestone. */
public final class GraveMod {
    /** Mod id; must match {@code mod_id} in gradle.properties and the mod metadata file. */
    public static final String MODID = "barelygravestone";

    public static final Logger LOGGER = LogUtils.getLogger();

    /** True in dev runs (the Gradle run tasks set the property when -PdevTools is given). */
    public static final boolean DEV = System.getProperty("barelygravestone.dev") != null;

    private GraveMod() {
    }

    /** An identifier in this mod's namespace. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
