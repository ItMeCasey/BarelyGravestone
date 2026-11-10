package com.itmecasey.barelygravestone.server;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Decides whether two players count as one team for {@code teamMembersCanOpen}. The vanilla scoreboard team always counts;
 * optional integrations (FTB Teams, Open Parties and Claims) add their own checks here when they are installed.
 */
public final class TeamLinks {
    /** A check supplied by another mod: is {@code actor} on the same team or party as the player with this id? */
    public interface Check {
        boolean sameTeam(ServerPlayer actor, UUID otherPlayer);
    }

    private static final List<Check> CHECKS = new CopyOnWriteArrayList<>();

    private TeamLinks() {
    }

    public static void register(Check check) {
        CHECKS.add(check);
    }

    public static boolean sameTeam(ServerPlayer actor, UUID owner, String ownerName) {
        PlayerTeam own = actor.getTeam();
        if (own != null && own == actor.level().getScoreboard().getPlayersTeam(ownerName)) {
            return true;
        }
        for (Check check : CHECKS) {
            try {
                if (check.sameTeam(actor, owner)) {
                    return true;
                }
            } catch (RuntimeException | LinkageError e) {
                // A broken integration must never block collecting a grave.
            }
        }
        return false;
    }
}
