package com.itmecasey.barelygravestone.server;

import com.itmecasey.barelygravestone.GraveMod;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Soft links to the team mods that count for {@code teamMembersCanOpen}: Open Parties and Claims (party members) and FTB Teams
 * (the party team a player is in). They are reached by reflection, so nothing is compiled against them and nothing breaks when
 * they are missing. Each check fails closed: any error means "not on the same team".
 */
public final class TeamMods {
    private static boolean installed;
    private static String status = "none";

    private TeamMods() {
    }

    /** What was found, for the log and the self test: {@code none}, {@code opac}, {@code ftbteams} or both. */
    public static String status() {
        return status;
    }

    /** Registers the checks of the team mods that are installed. Safe to call again. */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        StringBuilder found = new StringBuilder();
        if (exists("xaero.pac.common.server.api.OpenPACServerAPI")) {
            TeamLinks.register(TeamMods::sameOpacParty);
            found.append("opac");
        }
        if (exists("dev.ftb.mods.ftbteams.api.FTBTeamsAPI")) {
            TeamLinks.register(TeamMods::sameFtbParty);
            found.append(found.length() > 0 ? "+" : "").append("ftbteams");
        }
        status = found.length() == 0 ? "none" : found.toString();
        if (found.length() > 0) {
            GraveMod.LOGGER.info("Team mods found for teamMembersCanOpen: {}", status);
        }
    }

    private static boolean exists(String className) {
        try {
            Class.forName(className, false, TeamMods.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Open Parties and Claims: the actor's party has the owner as a member. */
    private static boolean sameOpacParty(ServerPlayer actor, UUID owner) {
        try {
            MinecraftServer server = ((net.minecraft.server.level.ServerLevel) actor.level()).getServer();
            Class<?> apiClass = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Object api = apiClass.getMethod("get", MinecraftServer.class).invoke(null, server);
            Object parties = api.getClass().getMethod("getPartyManager").invoke(api);
            Object party = find(parties, "getPartyByMember", UUID.class).invoke(parties, actor.getUUID());
            if (party == null) {
                return false;
            }
            return find(party, "getMemberInfo", UUID.class).invoke(party, owner) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** FTB Teams: the actor is in a party team whose members include the owner. */
    private static boolean sameFtbParty(ServerPlayer actor, UUID owner) {
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            Object api = apiClass.getMethod("api").invoke(null);
            Object manager = api.getClass().getMethod("getManager").invoke(api);
            Object result = find(manager, "getTeamForPlayerID", UUID.class).invoke(manager, actor.getUUID());
            if (!(result instanceof Optional<?> team) || team.isEmpty()) {
                return false;
            }
            Object value = team.get();
            boolean party = (Boolean) value.getClass().getMethod("isPartyTeam").invoke(value);
            Object members = value.getClass().getMethod("getMembers").invoke(value);
            return party && members instanceof Collection<?> ids && ids.contains(owner);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Finds a public method by name on the object's class or one of its interfaces (API types are interfaces). */
    private static Method find(Object target, String name, Class<?>... types) throws NoSuchMethodException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getMethod(name, types);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // try the interfaces below
            }
            for (Class<?> iface : type.getInterfaces()) {
                try {
                    return iface.getMethod(name, types);
                } catch (NoSuchMethodException ignored) {
                    // keep looking
                }
            }
        }
        throw new NoSuchMethodException(name);
    }
}
