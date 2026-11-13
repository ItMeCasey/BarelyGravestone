package com.itmecasey.barelygravestone.core.script;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * The handlers scripts registered, and the calls the game makes to run them. A script handler that throws is logged as one plain
 * line and the next handler runs; it never reaches the game code. With no scripting mod nothing is registered and every call is
 * a no-op.
 */
public final class ScriptHooks {
    private static final Logger LOG = Logger.getLogger("barelygravestone");

    private static final List<Consumer<GraveEventData.BeforeCreate>> BEFORE_CREATE = new CopyOnWriteArrayList<>();
    private static final List<Consumer<GraveEventData.Created>> CREATED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<GraveEventData.BeforeCollect>> BEFORE_COLLECT = new CopyOnWriteArrayList<>();
    private static final List<Consumer<GraveEventData.Collected>> COLLECTED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<GraveEventData.Expiring>> EXPIRING = new CopyOnWriteArrayList<>();
    private static final List<Consumer<GraveEventData.Expired>> EXPIRED = new CopyOnWriteArrayList<>();

    private ScriptHooks() {
    }

    static void addBeforeCreate(Consumer<GraveEventData.BeforeCreate> handler) {
        BEFORE_CREATE.add(handler);
    }

    static void addCreated(Consumer<GraveEventData.Created> handler) {
        CREATED.add(handler);
    }

    static void addBeforeCollect(Consumer<GraveEventData.BeforeCollect> handler) {
        BEFORE_COLLECT.add(handler);
    }

    static void addCollected(Consumer<GraveEventData.Collected> handler) {
        COLLECTED.add(handler);
    }

    static void addExpiring(Consumer<GraveEventData.Expiring> handler) {
        EXPIRING.add(handler);
    }

    static void addExpired(Consumer<GraveEventData.Expired> handler) {
        EXPIRED.add(handler);
    }

    /** Forgets every handler (the scripts are about to be loaded again). */
    public static void clear() {
        BEFORE_CREATE.clear();
        CREATED.clear();
        BEFORE_COLLECT.clear();
        COLLECTED.clear();
        EXPIRING.clear();
        EXPIRED.clear();
    }

    /** Whether any script listens to anything (the game skips building event data otherwise). */
    public static boolean any() {
        return !(BEFORE_CREATE.isEmpty() && CREATED.isEmpty() && BEFORE_COLLECT.isEmpty() && COLLECTED.isEmpty()
                && EXPIRING.isEmpty() && EXPIRED.isEmpty());
    }

    public static boolean hasBeforeCreate() {
        return !BEFORE_CREATE.isEmpty();
    }

    public static boolean hasCreated() {
        return !CREATED.isEmpty();
    }

    public static boolean hasBeforeCollect() {
        return !BEFORE_COLLECT.isEmpty();
    }

    public static boolean hasCollected() {
        return !COLLECTED.isEmpty();
    }

    public static boolean hasExpiring() {
        return !EXPIRING.isEmpty();
    }

    public static boolean hasExpired() {
        return !EXPIRED.isEmpty();
    }

    public static void beforeCreate(GraveEventData.BeforeCreate event) {
        run(BEFORE_CREATE, event, "beforeCreate");
    }

    public static void created(GraveEventData.Created event) {
        run(CREATED, event, "created");
    }

    public static void beforeCollect(GraveEventData.BeforeCollect event) {
        run(BEFORE_COLLECT, event, "beforeCollect");
    }

    public static void collected(GraveEventData.Collected event) {
        run(COLLECTED, event, "collected");
    }

    public static void expiring(GraveEventData.Expiring event) {
        run(EXPIRING, event, "expiring");
    }

    public static void expired(GraveEventData.Expired event) {
        run(EXPIRED, event, "expired");
    }

    private static <T> void run(List<Consumer<T>> handlers, T event, String name) {
        for (Consumer<T> handler : handlers) {
            try {
                handler.accept(event);
            } catch (RuntimeException | LinkageError e) {
                LOG.warning("A script handler for BarelyGravestone." + name + " failed: " + e);
            }
        }
    }
}
