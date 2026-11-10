package com.itmecasey.barelygravestone.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formats the times shown on the Obituary. */
public final class TimeText {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US);

    private TimeText() {
    }

    /** Real date and time in the given zone, such as {@code 2026-11-09 14:03}. */
    public static String realTime(long epochMillis, ZoneId zone) {
        return FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    /** The in-game day, counting from day 1, for a world time in ticks. */
    public static long gameDay(long dayTimeTicks) {
        return Math.max(0L, dayTimeTicks) / 24000L + 1L;
    }
}
