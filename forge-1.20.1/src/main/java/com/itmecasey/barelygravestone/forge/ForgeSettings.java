package com.itmecasey.barelygravestone.forge;

import com.itmecasey.barelygravestone.core.CollectMode;
import com.itmecasey.barelygravestone.core.Settings;
import net.minecraftforge.common.ForgeConfigSpec;

/** The configuration file, {@code barelygravestone-common.toml} in the config folder, read with Forge's config API. */
final class ForgeSettings implements Settings.Source {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.EnumValue<CollectMode> COLLECT_BY = BUILDER
            .comment("Who may collect a gravestone with an empty hand.",
                    "OWNER_ONLY: the player who died (and, with teamMembersCanOpen, their team). ANYONE: every player.",
                    "Operators and players in creative mode can always collect.")
            .defineEnum("collectBy", CollectMode.OWNER_ONLY);

    private static final ForgeConfigSpec.BooleanValue TEAM_MEMBERS_CAN_OPEN = BUILDER
            .comment("When true, members of the dead player's team can collect the gravestone too.",
                    "Counts the vanilla scoreboard team, plus FTB Teams and Open Parties and Claims when they are installed.",
                    "Only matters for OWNER_ONLY; ANYONE already lets everybody in.")
            .define("teamMembersCanOpen", false);

    private static final ForgeConfigSpec.IntValue XP_COST_PERCENT = BUILDER
            .comment("The price of keeping your items: this percent of your experience levels is taken when the grave is made.",
                    "0 keeps all of your levels (vanilla death rules apply to the rest), 100 takes every level.")
            .defineInRange("xpCostPercent", 0, 0, 100);

    private static final ForgeConfigSpec.BooleanValue KEEP_LOST_XP = BUILDER
            .comment("When true, the experience taken by xpCostPercent is stored in the grave and handed to whoever collects it.",
                    "When false (default) it is lost.")
            .define("keepLostXp", false);

    private static final ForgeConfigSpec.IntValue GRAVE_EXPIRY_MINUTES = BUILDER
            .comment("Minutes until a grave crumbles and drops everything it holds on the ground. 0 means graves never expire (default).")
            .defineInRange("graveExpiryMinutes", 0, 0, 525600);

    private static final ForgeConfigSpec.BooleanValue SHOW_COORDINATES = BUILDER
            .comment("Show the grave's coordinates on the Obituary. Turn off to make players find their grave by looking.")
            .define("showCoordinates", true);

    private static final ForgeConfigSpec.BooleanValue DEATH_MARKER = BUILDER
            .comment("Add a death marker at the grave on map mods that are installed (JourneyMap and others).",
                    "The marker is client side and shows while the player carries the Obituary; it goes away when the grave is emptied.")
            .define("deathMarker", true);

    private static final ForgeConfigSpec.BooleanValue OBITUARY_PARTICLES = BUILDER
            .comment("While an Obituary is held, particles drift from the player toward the grave (same dimension only).")
            .define("obituaryParticles", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    @Override
    public CollectMode collectBy() {
        return SPEC.isLoaded() ? COLLECT_BY.get() : CollectMode.OWNER_ONLY;
    }

    @Override
    public int xpCostPercent() {
        return SPEC.isLoaded() ? XP_COST_PERCENT.get() : 0;
    }

    @Override
    public boolean keepLostXp() {
        return SPEC.isLoaded() && KEEP_LOST_XP.get();
    }

    @Override
    public int graveExpiryMinutes() {
        return SPEC.isLoaded() ? GRAVE_EXPIRY_MINUTES.get() : 0;
    }

    @Override
    public boolean showCoordinates() {
        return !SPEC.isLoaded() || SHOW_COORDINATES.get();
    }

    @Override
    public boolean teamMembersCanOpen() {
        return SPEC.isLoaded() && TEAM_MEMBERS_CAN_OPEN.get();
    }

    @Override
    public boolean deathMarker() {
        return !SPEC.isLoaded() || DEATH_MARKER.get();
    }

    @Override
    public boolean obituaryParticles() {
        return !SPEC.isLoaded() || OBITUARY_PARTICLES.get();
    }
}
