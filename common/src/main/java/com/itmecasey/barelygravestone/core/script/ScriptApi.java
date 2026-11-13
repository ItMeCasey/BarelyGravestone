package com.itmecasey.barelygravestone.core.script;

import com.itmecasey.barelygravestone.core.Settings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The one scripting surface. The ModJS bridge exposes an instance to scripts as {@code BarelyGravestone}, and Java mods can use
 * it directly. Everything takes and returns plain strings, numbers, booleans and the small value objects of this package.
 *
 * <p>Events (call from {@code server_scripts}; handlers run on the server thread):
 * <pre>
 * BarelyGravestone.beforeCreate(e => { if (e.grave().ownerName() == 'Steve') e.cancel(); });
 * BarelyGravestone.created(e => console.log('grave at ' + e.grave().x()));
 * BarelyGravestone.beforeCollect(e => { if (e.collectorName() == 'Alex') e.allow(); });
 * BarelyGravestone.collected(e => {});
 * BarelyGravestone.expiring(e => e.cancel());
 * BarelyGravestone.expired(e => {});
 * </pre>
 */
public final class ScriptApi {
    /** What the game provides: it knows the world. Set by the loader-neutral game code when the server starts. */
    public interface Backend {
        /** Every grave in the index, as light views (no item list). */
        List<GraveView> list();

        /** One grave with its items, by id or the first characters of it; {@code null} when there is none. */
        GraveView get(String id);

        /** The grave at a position with its items, or {@code null}. */
        GraveView at(String dimension, int x, int y, int z);

        /** Removes a grave; its items drop at the grave when {@code dropItems}, else they are gone. */
        boolean remove(String id, boolean dropItems);
    }

    private static volatile Backend backend;

    /** Used by the game code. */
    public static void attach(Backend value) {
        backend = value;
    }

    public static void detach() {
        backend = null;
    }

    // ---- events ----

    /** Before a grave is made: {@code e.grave()}, {@code e.cancel()} (the items drop normally). */
    public void beforeCreate(Consumer<GraveEventData.BeforeCreate> handler) {
        ScriptHooks.addBeforeCreate(handler);
    }

    /** After a grave was made. */
    public void created(Consumer<GraveEventData.Created> handler) {
        ScriptHooks.addCreated(handler);
    }

    /** Before a player collects: {@code e.allowed()}, {@code e.allow()}, {@code e.deny()}, {@code e.collectorName()}. */
    public void beforeCollect(Consumer<GraveEventData.BeforeCollect> handler) {
        ScriptHooks.addBeforeCollect(handler);
    }

    /** After a collect: {@code e.stacksLeft()}, {@code e.emptied()}. */
    public void collected(Consumer<GraveEventData.Collected> handler) {
        ScriptHooks.addCollected(handler);
    }

    /** The expiry timer ran out: {@code e.cancel()} keeps the grave and restarts its timer. */
    public void expiring(Consumer<GraveEventData.Expiring> handler) {
        ScriptHooks.addExpiring(handler);
    }

    /** After a grave expired. */
    public void expired(Consumer<GraveEventData.Expired> handler) {
        ScriptHooks.addExpired(handler);
    }

    // ---- reading graves ----

    /** Whether the server is running and graves can be read. */
    public boolean isReady() {
        return backend != null;
    }

    /** Every grave, without item lists (cheap). Use {@link #get} for the items. */
    public List<GraveView> graves() {
        Backend b = backend;
        return b == null ? List.of() : b.list();
    }

    /** The graves of one player, by name (any case) or UUID, without item lists. */
    public List<GraveView> gravesOf(String player) {
        List<GraveView> result = new ArrayList<>();
        if (player == null) {
            return result;
        }
        for (GraveView grave : graves()) {
            if (grave.ownerName().equalsIgnoreCase(player) || grave.ownerId().equalsIgnoreCase(player)) {
                result.add(grave);
            }
        }
        return result;
    }

    /** One grave with its items, by id or the first characters of the id; {@code null} when none or ambiguous. */
    public GraveView get(String id) {
        Backend b = backend;
        return b == null || id == null ? null : b.get(id);
    }

    /** The grave at a position with its items, or {@code null}. The dimension is an id such as {@code minecraft:overworld}. */
    public GraveView graveAt(String dimension, int x, int y, int z) {
        Backend b = backend;
        return b == null ? null : b.at(dimension, x, y, z);
    }

    /** Removes a grave and drops its items there. Returns whether a grave was removed. */
    public boolean removeGrave(String id) {
        Backend b = backend;
        return b != null && id != null && b.remove(id, true);
    }

    // ---- options ----

    /** The option names scripts can read and set. */
    public List<String> optionNames() {
        return Settings.NAMES;
    }

    /** The value of an option as the game uses it right now (a script override if one is set). {@code null} for an unknown name. */
    public Object option(String name) {
        return name == null ? null : Settings.value(name);
    }

    /** Every option and its current value. */
    public Map<String, Object> options() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : Settings.NAMES) {
            result.put(name, Settings.value(name));
        }
        return result;
    }

    /**
     * Overrides an option until the game restarts or {@link #resetOption} is called; the config file is not changed. Numbers are
     * clamped, {@code collectBy} takes {@code OWNER_ONLY} or {@code ANYONE}. Returns whether the value was accepted.
     */
    public boolean setOption(String name, Object value) {
        return Settings.override(name, value instanceof Number number && !(number instanceof Integer)
                && number.doubleValue() == Math.rint(number.doubleValue()) ? (Object) number.intValue() : value);
    }

    /** Goes back to the config file's value for one option. */
    public void resetOption(String name) {
        Settings.clearOverride(name == null ? "" : name.toLowerCase(Locale.ROOT).equals("all") ? null : name);
    }
}
