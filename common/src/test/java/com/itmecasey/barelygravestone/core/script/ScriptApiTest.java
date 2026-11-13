package com.itmecasey.barelygravestone.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itmecasey.barelygravestone.core.CollectMode;
import com.itmecasey.barelygravestone.core.Settings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScriptApiTest {
    private final ScriptApi api = new ScriptApi();

    private static GraveView view(String id, String name, boolean full) {
        return new GraveView(id, "00000000-0000-0000-0000-00000000000" + name.length(), name, "minecraft:overworld", 1, 64, 2, 0L,
                "fell", 30, 2, full ? List.of(new GraveView.GraveItem("minecraft:diamond", 3),
                new GraveView.GraveItem("minecraft:diamond", 2), new GraveView.GraveItem("minecraft:dirt", 64)) : List.of(),
                full, -1);
    }

    @BeforeEach
    @AfterEach
    void reset() {
        ScriptHooks.clear();
        Settings.clearOverride(null);
        ScriptApi.detach();
    }

    @Test
    void viewHelpers() {
        GraveView v = view("a", "Steve", true);
        assertEquals(3, v.itemCount());
        assertEquals(69, v.totalItems());
        assertEquals(5, v.count("minecraft:diamond"));
        assertEquals(0, v.count("minecraft:gold_ingot"));
    }

    @Test
    void noBackendMeansEmptyAnswers() {
        assertFalse(api.isReady());
        assertTrue(api.graves().isEmpty());
        assertNull(api.get("a"));
        assertNull(api.graveAt("minecraft:overworld", 1, 64, 2));
        assertFalse(api.removeGrave("a"));
    }

    @Test
    void backendQueries() {
        List<String> removed = new ArrayList<>();
        ScriptApi.attach(new ScriptApi.Backend() {
            @Override
            public List<GraveView> list() {
                return List.of(view("a", "Steve", false), view("b", "Alex", false), view("c", "steve", false));
            }

            @Override
            public GraveView get(String id) {
                return id.equals("a") ? view("a", "Steve", true) : null;
            }

            @Override
            public GraveView at(String dimension, int x, int y, int z) {
                return x == 1 ? view("a", "Steve", true) : null;
            }

            @Override
            public boolean remove(String id, boolean dropItems) {
                removed.add(id + dropItems);
                return true;
            }
        });
        assertTrue(api.isReady());
        assertEquals(3, api.graves().size());
        assertEquals(2, api.gravesOf("STEVE").size());
        assertEquals(1, api.gravesOf("alex").size());
        assertTrue(api.get("a").full());
        assertNull(api.get("zzz"));
        assertEquals("a", api.graveAt("minecraft:overworld", 1, 64, 2).id());
        assertTrue(api.removeGrave("a"));
        assertEquals(List.of("atrue"), removed);
        assertTrue(api.gravesOf(null).isEmpty());
    }

    @Test
    void optionsReadAndOverride() {
        assertEquals(8, api.optionNames().size());
        assertEquals("OWNER_ONLY", api.option("collectBy"));
        assertNull(api.option("nope"));
        assertTrue(api.setOption("collectBy", "anyone"));
        assertEquals(CollectMode.ANYONE, Settings.collectBy());
        assertFalse(api.setOption("collectBy", "everybody"));
        assertTrue(api.setOption("xpCostPercent", 250.0));
        assertEquals(100, Settings.xpCostPercent());
        assertTrue(api.setOption("graveExpiryMinutes", 30.0));
        assertEquals(30, api.option("graveExpiryMinutes"));
        assertFalse(api.setOption("graveExpiryMinutes", "soon"));
        assertTrue(api.setOption("teamMembersCanOpen", true));
        assertTrue(Settings.teamMembersCanOpen());
        assertFalse(api.setOption("teamMembersCanOpen", "yes"));
        assertFalse(api.setOption("unknown", true));
        assertFalse(api.setOption(null, true));
        assertEquals(30, api.options().get("graveExpiryMinutes"));
        api.resetOption("graveExpiryMinutes");
        assertEquals(0, Settings.graveExpiryMinutes());
        assertEquals(CollectMode.ANYONE, Settings.collectBy());
        api.resetOption("all");
        assertEquals(CollectMode.OWNER_ONLY, Settings.collectBy());
    }

    @Test
    void cancellableEvents() {
        api.beforeCreate(e -> {
            if (e.grave().ownerName().equals("Steve")) {
                e.cancel();
            }
        });
        var steve = new GraveEventData.BeforeCreate(view("", "Steve", true));
        var alex = new GraveEventData.BeforeCreate(view("", "Alex", true));
        ScriptHooks.beforeCreate(steve);
        ScriptHooks.beforeCreate(alex);
        assertTrue(steve.cancelled());
        assertFalse(alex.cancelled());

        api.expiring(GraveEventData.Expiring::cancel);
        var expiring = new GraveEventData.Expiring(view("a", "Steve", true));
        ScriptHooks.expiring(expiring);
        assertTrue(expiring.cancelled());
    }

    @Test
    void collectDecisionCanBeChangedBothWays() {
        api.beforeCollect(e -> {
            if (e.collectorName().equals("Alex")) {
                e.allow();
            } else if (!e.isOwner()) {
                e.deny();
            }
        });
        GraveView v = view("a", "Steve", true);
        var stranger = new GraveEventData.BeforeCollect(v, "u1", "Alex", false);
        var other = new GraveEventData.BeforeCollect(v, "u2", "Bob", true);
        var owner = new GraveEventData.BeforeCollect(v, v.ownerId(), "Steve", true);
        ScriptHooks.beforeCollect(stranger);
        ScriptHooks.beforeCollect(other);
        ScriptHooks.beforeCollect(owner);
        assertTrue(stranger.allowed());
        assertFalse(other.allowed());
        assertTrue(owner.isOwner());
        assertTrue(owner.allowed());
    }

    @Test
    void failingHandlerNeverReachesTheGame() {
        List<String> ran = new ArrayList<>();
        api.created(e -> {
            throw new IllegalStateException("script bug");
        });
        api.created(e -> ran.add(e.grave().ownerName()));
        ScriptHooks.created(new GraveEventData.Created(view("a", "Steve", true)));
        assertEquals(List.of("Steve"), ran);
    }

    @Test
    void handlersClearOnReload() {
        assertFalse(ScriptHooks.any());
        api.collected(e -> {
        });
        api.expired(e -> {
        });
        assertTrue(ScriptHooks.any());
        assertTrue(ScriptHooks.hasCollected());
        assertTrue(ScriptHooks.hasExpired());
        assertFalse(ScriptHooks.hasCreated());
        ScriptHooks.clear();
        assertFalse(ScriptHooks.any());
    }

    @Test
    void collectedEventReportsWhatIsLeft() {
        var full = new GraveEventData.Collected(view("a", "Steve", true), "u", "Steve", 0);
        var partial = new GraveEventData.Collected(view("a", "Steve", true), "u", "Steve", 4);
        assertTrue(full.emptied());
        assertFalse(partial.emptied());
        assertEquals(4, partial.stacksLeft());
    }

    @Test
    void modJsBridgeDoesNothingWithoutModJs() {
        assertFalse(ModJsBridge.install(api));
        assertEquals("SERVER", ModJsBridge.scriptType(ScriptTypeStub.SERVER));
        assertEquals("CLIENT", ModJsBridge.scriptType("client"));
        assertEquals("", ModJsBridge.scriptType(null));
    }

    private enum ScriptTypeStub {
        SERVER
    }
}
