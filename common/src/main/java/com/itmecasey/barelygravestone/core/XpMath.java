package com.itmecasey.barelygravestone.core;

/** Experience arithmetic with the vanilla level curve. */
public final class XpMath {
    private XpMath() {
    }

    /** Total experience points needed to reach {@code level} from zero. */
    public static long totalForLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        if (level <= 16) {
            return (long) level * level + 6L * level;
        }
        if (level <= 31) {
            return Math.round(2.5 * level * level - 40.5 * level + 360.0);
        }
        return Math.round(4.5 * level * level - 162.5 * level + 2220.0);
    }

    /** How many whole levels the price takes: {@code percent} of {@code level}, rounded down. */
    public static int levelsTaken(int level, int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        return (int) ((long) Math.max(0, level) * clamped / 100L);
    }

    /** The experience points those levels are worth (what a grave stores when {@code keepLostXp} is on). */
    public static int pointsTaken(int level, int levelsTaken) {
        int taken = Math.max(0, Math.min(levelsTaken, level));
        return (int) (totalForLevel(level) - totalForLevel(level - taken));
    }
}
