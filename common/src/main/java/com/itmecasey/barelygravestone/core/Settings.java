package com.itmecasey.barelygravestone.core;

/**
 * The mod's options as the rest of the code sees them. Each loader layer reads its own configuration file with the loader's
 * standard config API and installs a {@link Source} here, so the game logic never depends on a loader.
 */
public final class Settings {
    /** Where the values come from. Every method is called on demand, so edits to the config file apply at once. */
    public interface Source {
        CollectMode collectBy();

        int xpCostPercent();

        boolean keepLostXp();

        int graveExpiryMinutes();

        boolean showCoordinates();

        boolean teamMembersCanOpen();

        boolean deathMarker();

        boolean obituaryParticles();
    }

    /** The defaults, used until a loader installs its config and in unit tests. */
    public static final Source DEFAULTS = new Source() {
        @Override
        public CollectMode collectBy() {
            return CollectMode.OWNER_ONLY;
        }

        @Override
        public int xpCostPercent() {
            return 0;
        }

        @Override
        public boolean keepLostXp() {
            return false;
        }

        @Override
        public int graveExpiryMinutes() {
            return 0;
        }

        @Override
        public boolean showCoordinates() {
            return true;
        }

        @Override
        public boolean teamMembersCanOpen() {
            return false;
        }

        @Override
        public boolean deathMarker() {
            return true;
        }

        @Override
        public boolean obituaryParticles() {
            return true;
        }
    };

    private static volatile Source source = DEFAULTS;

    private Settings() {
    }

    /** Installs the loader's config; {@code null} goes back to the defaults. */
    public static void install(Source newSource) {
        source = newSource == null ? DEFAULTS : newSource;
    }

    /** Option names a script may read and override. */
    public static final java.util.List<String> NAMES = java.util.List.of("collectBy", "teamMembersCanOpen", "xpCostPercent",
            "keepLostXp", "graveExpiryMinutes", "showCoordinates", "deathMarker", "obituaryParticles");

    /** Values set by scripts; they win over the config file until they are reset or the game restarts. */
    private static final java.util.Map<String, Object> OVERRIDES = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Overrides one option from a script. The value is checked and converted (numbers clamped, {@code collectBy} by name);
     * returns {@code false} for an unknown name or a value of the wrong kind.
     */
    public static boolean override(String name, Object value) {
        if (name == null || value == null || !NAMES.contains(name)) {
            return false;
        }
        Object converted;
        switch (name) {
            case "collectBy" -> {
                CollectMode mode = CollectMode.parse(String.valueOf(value));
                if (!mode.name().equalsIgnoreCase(String.valueOf(value).trim())) {
                    return false;
                }
                converted = mode;
            }
            case "xpCostPercent" -> {
                if (!(value instanceof Number number)) {
                    return false;
                }
                converted = Math.max(0, Math.min(100, number.intValue()));
            }
            case "graveExpiryMinutes" -> {
                if (!(value instanceof Number number)) {
                    return false;
                }
                converted = Math.max(0, number.intValue());
            }
            default -> {
                if (!(value instanceof Boolean)) {
                    return false;
                }
                converted = value;
            }
        }
        OVERRIDES.put(name, converted);
        return true;
    }

    /** Removes a script override (all of them when the name is {@code null}). */
    public static void clearOverride(String name) {
        if (name == null) {
            OVERRIDES.clear();
        } else {
            OVERRIDES.remove(name);
        }
    }

    /** The value a script sees: the override if one is set, else the current config value. */
    public static Object value(String name) {
        return switch (name) {
            case "collectBy" -> collectBy().name();
            case "teamMembersCanOpen" -> teamMembersCanOpen();
            case "xpCostPercent" -> xpCostPercent();
            case "keepLostXp" -> keepLostXp();
            case "graveExpiryMinutes" -> graveExpiryMinutes();
            case "showCoordinates" -> showCoordinates();
            case "deathMarker" -> deathMarker();
            case "obituaryParticles" -> obituaryParticles();
            default -> null;
        };
    }

    public static CollectMode collectBy() {
        if (OVERRIDES.get("collectBy") instanceof CollectMode forced) {
            return forced;
        }
        CollectMode mode = source.collectBy();
        return mode == null ? CollectMode.OWNER_ONLY : mode;
    }

    /** The share of the player's XP levels taken when the grave is made, 0 to 100. */
    public static int xpCostPercent() {
        if (OVERRIDES.get("xpCostPercent") instanceof Integer forced) {
            return forced;
        }
        return Math.max(0, Math.min(100, source.xpCostPercent()));
    }

    public static boolean keepLostXp() {
        return flag("keepLostXp", source.keepLostXp());
    }

    /** Minutes until a grave crumbles and drops its items; 0 means never. */
    public static int graveExpiryMinutes() {
        if (OVERRIDES.get("graveExpiryMinutes") instanceof Integer forced) {
            return forced;
        }
        return Math.max(0, source.graveExpiryMinutes());
    }

    public static boolean showCoordinates() {
        return flag("showCoordinates", source.showCoordinates());
    }

    public static boolean teamMembersCanOpen() {
        return flag("teamMembersCanOpen", source.teamMembersCanOpen());
    }

    public static boolean deathMarker() {
        return flag("deathMarker", source.deathMarker());
    }

    public static boolean obituaryParticles() {
        return flag("obituaryParticles", source.obituaryParticles());
    }

    private static boolean flag(String name, boolean configured) {
        return OVERRIDES.get(name) instanceof Boolean forced ? forced : configured;
    }
}
