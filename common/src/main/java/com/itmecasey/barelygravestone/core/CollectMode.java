package com.itmecasey.barelygravestone.core;

/** Who may collect a gravestone besides operators and creative players. */
public enum CollectMode {
    /** Only the dead player (and team members when {@code teamMembersCanOpen} is on). */
    OWNER_ONLY,
    /** Any player. */
    ANYONE;

    /** Parses a config value, falling back to the default for anything unknown. */
    public static CollectMode parse(String text) {
        if (text != null) {
            for (CollectMode mode : values()) {
                if (mode.name().equalsIgnoreCase(text.trim())) {
                    return mode;
                }
            }
        }
        return OWNER_ONLY;
    }
}
