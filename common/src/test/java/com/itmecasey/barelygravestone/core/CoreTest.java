package com.itmecasey.barelygravestone.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreTest {
    @Test
    void accessRules() {
        // owner
        assertTrue(Access.canCollect(CollectMode.OWNER_ONLY, true, false, false, false));
        // stranger
        assertFalse(Access.canCollect(CollectMode.OWNER_ONLY, false, false, false, false));
        // teammate only with the option
        assertFalse(Access.canCollect(CollectMode.OWNER_ONLY, false, true, false, false));
        assertTrue(Access.canCollect(CollectMode.OWNER_ONLY, false, true, true, false));
        // a non-teammate is not helped by the option
        assertFalse(Access.canCollect(CollectMode.OWNER_ONLY, false, false, true, false));
        // ANYONE overrides everything
        assertTrue(Access.canCollect(CollectMode.ANYONE, false, false, false, false));
        // operators and creative players always
        assertTrue(Access.canCollect(CollectMode.OWNER_ONLY, false, false, false, true));
    }

    @Test
    void collectModeParsing() {
        assertEquals(CollectMode.ANYONE, CollectMode.parse("anyone"));
        assertEquals(CollectMode.OWNER_ONLY, CollectMode.parse("nonsense"));
        assertEquals(CollectMode.OWNER_ONLY, CollectMode.parse(null));
    }

    @Test
    void xpCurve() {
        assertEquals(0, XpMath.totalForLevel(0));
        assertEquals(7, XpMath.totalForLevel(1));
        assertEquals(352, XpMath.totalForLevel(16));
        assertEquals(1395, XpMath.totalForLevel(30));
        assertEquals(2920, XpMath.totalForLevel(40));
    }

    @Test
    void xpPrice() {
        assertEquals(0, XpMath.levelsTaken(30, 0));
        assertEquals(15, XpMath.levelsTaken(30, 50));
        assertEquals(30, XpMath.levelsTaken(30, 100));
        assertEquals(3, XpMath.levelsTaken(7, 50));
        assertEquals(0, XpMath.levelsTaken(0, 100));
        assertEquals(1395 - XpMath.totalForLevel(15), XpMath.pointsTaken(30, 15));
        assertEquals(0, XpMath.pointsTaken(30, 0));
        assertEquals(1395, XpMath.pointsTaken(30, 99));
    }

    @Test
    void timeText() {
        assertEquals("2026-11-09 12:00", TimeText.realTime(1_794_225_600_000L, ZoneId.of("UTC")));
        assertEquals(1, TimeText.gameDay(0));
        assertEquals(1, TimeText.gameDay(23999));
        assertEquals(3, TimeText.gameDay(48000));
    }

    @Test
    void registryRoundTrip(@TempDir Path dir) {
        Path file = dir.resolve("graves.txt");
        GraveRegistry registry = GraveRegistry.open(file);
        UUID owner = UUID.randomUUID();
        GraveRegistry.Entry a = new GraveRegistry.Entry(UUID.randomUUID(), owner, "Steve", "minecraft:overworld", 1, 64, -3, 5L);
        GraveRegistry.Entry b = new GraveRegistry.Entry(UUID.randomUUID(), UUID.randomUUID(), "Älex", "minecraft:the_nether", 8, 30, 9, 6L);
        registry.add(a);
        registry.add(b);
        GraveRegistry again = GraveRegistry.open(file);
        assertEquals(2, again.size());
        assertEquals(a, again.get(a.id()));
        assertEquals("Älex", again.get(b.id()).ownerName());
        assertEquals(List.of(a), again.ownedBy(owner));
        assertNotNull(again.findAt("minecraft:overworld", 1, 64, -3));
        assertNull(again.findAt("minecraft:overworld", 1, 65, -3));
        assertTrue(again.remove(a.id()));
        assertFalse(again.remove(a.id()));
        assertEquals(1, GraveRegistry.open(file).size());
    }

    @Test
    void pendingRoundTrip(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("pending.txt");
        PendingStore store = PendingStore.open(file);
        UUID player = UUID.randomUUID();
        store.add(player, "{id:\"a\"}\nsecond line");
        store.add(player, "two");
        PendingStore again = PendingStore.open(file);
        assertTrue(again.has(player));
        assertEquals(List.of("{id:\"a\"}\nsecond line", "two"), again.peek(player));
        assertEquals("{id:\"a\"}\nsecond line", again.poll(player));
        again.pushFront(player, "front");
        assertEquals("front", again.poll(player));
        assertEquals("two", again.poll(player));
        assertNull(again.poll(player));
        assertFalse(again.has(player));
        assertEquals(0, Files.readAllLines(file).size());
    }

    @Test
    void obituaryRecordRoundTrip() {
        ObituaryRecord record = new ObituaryRecord(UUID.randomUUID(), UUID.randomUUID(), "Steve",
                "Steve was slain by Zombie | with a \"sword\"\nnew line", 1794225600000L, 12, "minecraft:the_nether", -5, 70, 300, 7, 91);
        assertEquals(record, ObituaryRecord.decode(record.encode()));
        assertNull(ObituaryRecord.decode("garbage"));
        assertNull(ObituaryRecord.decode(null));
        assertEquals("The Nether", record.dimensionName());
        assertEquals("Deep Dark (mod)", ObituaryRecord.prettyDimension("mod:deep_dark"));
        assertEquals("Overworld", ObituaryRecord.prettyDimension("minecraft:overworld"));
    }

    // ---- placement ----

    /** A tiny world: a map of block kinds by column, over a flat y range. */
    private static final class Fake implements Terrain {
        static final char AIR = '.';
        static final char STONE = '#';
        static final char WATER = '~';
        final java.util.Map<String, Character> cells = new java.util.HashMap<>();
        final int min;
        final int max;
        final char fill;

        Fake(int min, int max, char fill) {
            this.min = min;
            this.max = max;
            this.fill = fill;
        }

        Fake set(int x, int y, int z, char c) {
            cells.put(x + "," + y + "," + z, c);
            return this;
        }

        Fake layer(int y, int radius, char c) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    set(x, y, z, c);
                }
            }
            return this;
        }

        char at(int x, int y, int z) {
            return cells.getOrDefault(x + "," + y + "," + z, fill);
        }

        @Override
        public int minY() {
            return min;
        }

        @Override
        public int maxY() {
            return max;
        }

        @Override
        public boolean replaceable(int x, int y, int z) {
            return at(x, y, z) == AIR;
        }

        @Override
        public boolean fluid(int x, int y, int z) {
            return at(x, y, z) == WATER;
        }

        @Override
        public boolean solidTop(int x, int y, int z) {
            return at(x, y, z) == STONE;
        }
    }

    @Test
    void graveOnGroundStaysAtDeath() {
        Fake w = new Fake(0, 100, Fake.AIR).layer(63, 10, Fake.STONE);
        assertEquals(new GravePlan(5, 64, 5, false), GravePlanner.plan(w, 5, 64, 5));
    }

    @Test
    void graveInsideRockMovesToNearestOpenCell() {
        Fake w = new Fake(0, 100, Fake.STONE);
        w.set(2, 50, 0, Fake.AIR); // a cave pocket with floor below
        GravePlan plan = GravePlanner.plan(w, 0, 50, 0);
        assertEquals(new GravePlan(2, 50, 0, false), plan);
    }

    @Test
    void deathInWaterPutsCobblestoneAtTheSurface() {
        Fake w = new Fake(0, 100, Fake.AIR).layer(40, 10, Fake.STONE);
        for (int y = 41; y <= 62; y++) {
            w.layer(y, 10, Fake.WATER); // water from 41 to 62, air from 63
        }
        assertEquals(new GravePlan(0, 63, 0, true), GravePlanner.plan(w, 0, 45, 0));
    }

    @Test
    void deathInLavaLakeSameRule() {
        Fake w = new Fake(0, 100, Fake.AIR).layer(10, 5, Fake.STONE).layer(11, 5, Fake.WATER);
        assertEquals(new GravePlan(0, 12, 0, true), GravePlanner.plan(w, 0, 11, 0));
    }

    @Test
    void fallingToTheGroundBelow() {
        Fake w = new Fake(0, 100, Fake.AIR).layer(20, 10, Fake.STONE);
        assertEquals(new GravePlan(0, 21, 0, false), GravePlanner.plan(w, 0, 60, 0));
    }

    @Test
    void midAirOverAnOceanLandsOnTheWaterSurface() {
        Fake w = new Fake(0, 100, Fake.AIR).layer(5, 10, Fake.STONE);
        for (int y = 6; y <= 30; y++) {
            w.layer(y, 10, Fake.WATER);
        }
        assertEquals(new GravePlan(0, 31, 0, true), GravePlanner.plan(w, 0, 80, 0));
    }

    @Test
    void voidGetsACobblestoneAtTheDeathCell() {
        Fake w = new Fake(0, 100, Fake.AIR);
        assertEquals(new GravePlan(0, 50, 0, true), GravePlanner.plan(w, 0, 50, 0));
    }

    @Test
    void topOfTheWorldWithNothingFreeFails() {
        Fake w = new Fake(0, 100, Fake.STONE);
        assertNull(GravePlanner.plan(w, 0, 99, 0));
    }
}
