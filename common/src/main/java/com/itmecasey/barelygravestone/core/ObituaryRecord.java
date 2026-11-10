package com.itmecasey.barelygravestone.core;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Everything an Obituary says about a death. It travels as one line of text so the same record can sit in an item stack, in a
 * grave's block entity and in the pending store of every Minecraft version without any version-specific serialization.
 *
 * @param graveId     the grave this obituary points to
 * @param owner       who died
 * @param ownerName   their name at the time
 * @param message     how they died (the death message as plain text)
 * @param realMillis  real time of death, milliseconds since 1970
 * @param gameDay     in-game day of the death, counting from 1
 * @param dimension   dimension id such as {@code minecraft:overworld}
 * @param x           grave position
 * @param y           grave position
 * @param z           grave position
 * @param xpLevels    experience levels the grave cost
 * @param xpPoints    experience points kept in the grave when it was made (0 unless {@code keepLostXp})
 */
public record ObituaryRecord(UUID graveId, UUID owner, String ownerName, String message, long realMillis, long gameDay,
                             String dimension, int x, int y, int z, int xpLevels, int xpPoints) {
    private static final String SEPARATOR = "|";

    public String encode() {
        return String.join(SEPARATOR, graveId.toString(), owner.toString(), b64(ownerName), b64(message),
                Long.toString(realMillis), Long.toString(gameDay), b64(dimension),
                Integer.toString(x), Integer.toString(y), Integer.toString(z),
                Integer.toString(xpLevels), Integer.toString(xpPoints));
    }

    /** Parses a line made by {@link #encode()}; returns {@code null} for anything else. */
    public static ObituaryRecord decode(String line) {
        if (line == null) {
            return null;
        }
        String[] f = line.split("\\" + SEPARATOR, -1);
        if (f.length != 12) {
            return null;
        }
        try {
            return new ObituaryRecord(UUID.fromString(f[0]), UUID.fromString(f[1]), unb64(f[2]), unb64(f[3]),
                    Long.parseLong(f[4]), Long.parseLong(f[5]), unb64(f[6]),
                    Integer.parseInt(f[7]), Integer.parseInt(f[8]), Integer.parseInt(f[9]),
                    Integer.parseInt(f[10]), Integer.parseInt(f[11]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** {@code minecraft:the_nether} as {@code The Nether}; another mod's {@code mod:deep_dark} as {@code Deep Dark (mod)}. */
    public String dimensionName() {
        return prettyDimension(dimension);
    }

    public static String prettyDimension(String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        StringBuilder out = new StringBuilder();
        for (String word : path.replace('/', ' ').split("[_ ]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return namespace.equals("minecraft") ? out.toString() : out + " (" + namespace + ")";
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String text) {
        return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }
}
