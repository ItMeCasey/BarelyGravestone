package com.itmecasey.barelygravestone.core;

/** Who may collect a grave. Pure rules, so the self test and the unit tests share one definition. */
public final class Access {
    private Access() {
    }

    /**
     * @param mode        the {@code collectBy} setting
     * @param owner       the player is the one who died
     * @param teamMember  the player shares a team (scoreboard team, FTB Teams, Open Parties and Claims) with the owner
     * @param teamEnabled the {@code teamMembersCanOpen} setting
     * @param privileged  the player is an operator or in creative mode
     */
    public static boolean canCollect(CollectMode mode, boolean owner, boolean teamMember, boolean teamEnabled, boolean privileged) {
        if (privileged || mode == CollectMode.ANYONE || owner) {
            return true;
        }
        return teamEnabled && teamMember;
    }
}
