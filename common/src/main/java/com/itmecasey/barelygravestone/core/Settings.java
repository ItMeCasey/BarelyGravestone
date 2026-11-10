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

    public static CollectMode collectBy() {
        CollectMode mode = source.collectBy();
        return mode == null ? CollectMode.OWNER_ONLY : mode;
    }

    /** The share of the player's XP levels taken when the grave is made, 0 to 100. */
    public static int xpCostPercent() {
        return Math.max(0, Math.min(100, source.xpCostPercent()));
    }

    public static boolean keepLostXp() {
        return source.keepLostXp();
    }

    /** Minutes until a grave crumbles and drops its items; 0 means never. */
    public static int graveExpiryMinutes() {
        return Math.max(0, source.graveExpiryMinutes());
    }

    public static boolean showCoordinates() {
        return source.showCoordinates();
    }

    public static boolean teamMembersCanOpen() {
        return source.teamMembersCanOpen();
    }

    public static boolean deathMarker() {
        return source.deathMarker();
    }

    public static boolean obituaryParticles() {
        return source.obituaryParticles();
    }
}
