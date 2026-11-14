package com.itmecasey.barelygravestone.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldStateTest {
    private static final String GRAVE_A = "bc47d706-137e-4f8f-84e7-bdddac6b535a\ta24ba6bb-6f22-3061-afa3-8ffdabaa3cc2\tU3dpbW1lcg==\tminecraft:overworld\t230\t102\t230\t1794609495792";

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = WorldStateTest.class.getResourceAsStream("/legacy/" + name)) {
            return in.readAllBytes();
        }
    }

    private static List<String> lines(byte[] bytes) {
        List<String> result = new ArrayList<>();
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\r?\n")) {
            if (!line.isBlank()) {
                result.add(line);
            }
        }
        return result;
    }

    private static List<String> sorted(List<String> list) {
        List<String> copy = new ArrayList<>(list);
        Collections.sort(copy);
        return copy;
    }

    /** Files written by the released builds (2026.11.1 and 2026.11.2 share the format) import exactly and stay untouched. */
    @Test
    void releasedFilesImportExactly(@TempDir Path dir) throws IOException {
        byte[] graves = resource("v2026.11.1/graves.txt");
        byte[] pending = resource("v2026.11.1/pending_obituaries.txt");
        Files.write(dir.resolve("graves.txt"), graves);
        Files.write(dir.resolve("pending_obituaries.txt"), pending);

        WorldState state = new WorldState();
        int[] dirty = {0};
        state.setOnChange(() -> dirty[0]++);
        WorldState.Import result = state.importLegacy(dir);

        assertEquals(WorldState.Outcome.IMPORTED, result.outcome());
        assertEquals(lines(graves).size(), result.graves());
        assertEquals(lines(pending).size(), result.pending());
        assertEquals(0, result.skipped());
        assertEquals(lines(graves), state.graveLines());
        assertEquals(sorted(lines(pending)), sorted(state.pendingLines()));
        assertTrue(state.legacyImported());
        assertEquals(1, dirty[0]);
        // The old files are not touched.
        assertEquals(List.copyOf(lines(graves)), lines(Files.readAllBytes(dir.resolve("graves.txt"))));
        assertTrue(java.util.Arrays.equals(graves, Files.readAllBytes(dir.resolve("graves.txt"))));
        assertTrue(java.util.Arrays.equals(pending, Files.readAllBytes(dir.resolve("pending_obituaries.txt"))));
        try (var names = Files.list(dir)) {
            assertEquals(2, names.count());
        }
    }

    @Test
    void importsOnlyOnce(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("graves.txt"), List.of(GRAVE_A));
        WorldState state = new WorldState();
        assertEquals(WorldState.Outcome.IMPORTED, state.importLegacy(dir).outcome());
        // Collect the grave: the store is empty again, but the old file must not bring it back.
        state.registry().remove(UUID.fromString("bc47d706-137e-4f8f-84e7-bdddac6b535a"));
        assertEquals(0, state.registry().size());
        assertEquals(WorldState.Outcome.NOTHING, state.importLegacy(dir).outcome());
        assertEquals(0, state.registry().size());
        // The flag survives a save and load.
        WorldState reloaded = new WorldState(state.graveLines(), state.pendingLines(), state.legacyImported());
        assertEquals(WorldState.Outcome.NOTHING, reloaded.importLegacy(dir).outcome());
        assertEquals(0, reloaded.registry().size());
    }

    @Test
    void neverImportsOverExistingData(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("graves.txt"), List.of(GRAVE_A));
        WorldState state = new WorldState(List.of(GraveRegistry.encode(new GraveRegistry.Entry(UUID.randomUUID(),
                UUID.randomUUID(), "New", "minecraft:overworld", 1, 2, 3, 4L))), List.of(), false);
        WorldState.Import result = state.importLegacy(dir);
        assertEquals(WorldState.Outcome.STORE_NOT_EMPTY, result.outcome());
        assertEquals(1, state.registry().size());
        assertFalse(state.legacyImported());
    }

    @Test
    void missingFolderOrFiles(@TempDir Path dir) {
        WorldState state = new WorldState();
        assertEquals(WorldState.Outcome.NOTHING, state.importLegacy(dir.resolve("absent")).outcome());
        assertEquals(WorldState.Outcome.NOTHING, state.importLegacy(dir).outcome());
        assertFalse(state.legacyImported());
    }

    @Test
    void emptyFiles(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("graves.txt"), new byte[0]);
        Files.write(dir.resolve("pending_obituaries.txt"), new byte[0]);
        WorldState state = new WorldState();
        WorldState.Import result = state.importLegacy(dir);
        assertEquals(WorldState.Outcome.IMPORTED, result.outcome());
        assertEquals(0, result.graves());
        assertEquals(0, result.skipped());
    }

    @Test
    void onlyOneOfTheFiles(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("pending_obituaries.txt"), resource("v2026.11.1/pending_obituaries.txt"));
        WorldState state = new WorldState();
        WorldState.Import result = state.importLegacy(dir);
        assertEquals(WorldState.Outcome.IMPORTED, result.outcome());
        assertEquals(0, result.graves());
        assertTrue(result.pending() > 0);
    }

    @Test
    void corruptAndPartiallyWrittenFiles(@TempDir Path dir) throws IOException {
        String text = "garbage line\n" + GRAVE_A + "\n\n" + GRAVE_A.replace("bc47d706", "cc47d706") + "\nnot-a-uuid\tname\tx\t1\t2\t3\t4\n"
                + GRAVE_A.substring(0, 90); // half-written last line, no line break
        Files.write(dir.resolve("graves.txt"), text.getBytes(StandardCharsets.UTF_8));
        // Invalid UTF-8 inside a damaged line must not stop the import.
        byte[] pending = ("not a line\n" + lines(resource("v2026.11.1/pending_obituaries.txt")).get(0) + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] damaged = new byte[pending.length + 3];
        System.arraycopy(pending, 0, damaged, 0, pending.length);
        damaged[pending.length] = (byte) 0xC3;
        damaged[pending.length + 1] = (byte) 0x28;
        damaged[pending.length + 2] = (byte) 0xFF;
        Files.write(dir.resolve("pending_obituaries.txt"), damaged);

        WorldState state = new WorldState();
        WorldState.Import result = state.importLegacy(dir);
        assertEquals(WorldState.Outcome.IMPORTED, result.outcome());
        assertEquals(2, result.graves());
        assertEquals(1, result.pending());
        assertEquals(5, result.skipped());
        assertEquals(2, state.registry().size());
    }

    @Test
    void aFolderInPlaceOfTheFileIsIgnored(@TempDir Path dir) throws IOException {
        // A directory where the file should be: it exists but cannot be read as a file.
        Files.createDirectory(dir.resolve("graves.txt"));
        WorldState state = new WorldState();
        WorldState.Import result = state.importLegacy(dir);
        assertEquals(WorldState.Outcome.NOTHING, result.outcome()); // not a regular file: treated as absent
        assertFalse(state.legacyImported());
    }

    @Test
    void saveAndLoadRoundTrip(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("graves.txt"), resource("v2026.11.1/graves.txt"));
        Files.write(dir.resolve("pending_obituaries.txt"), resource("v2026.11.1/pending_obituaries.txt"));
        WorldState state = new WorldState();
        state.importLegacy(dir);
        UUID player = UUID.randomUUID();
        state.pending().add(player, "line one\nline two");
        state.registry().add(new GraveRegistry.Entry(UUID.randomUUID(), player, "Älex", "minecraft:the_nether", -5, 30, 9, 77L));

        WorldState loaded = new WorldState(state.graveLines(), state.pendingLines(), state.legacyImported());
        assertEquals(state.graveLines(), loaded.graveLines());
        assertEquals(sorted(state.pendingLines()), sorted(loaded.pendingLines()));
        assertEquals(List.of("line one\nline two"), loaded.pending().peek(player));
        assertEquals("Älex", loaded.registry().ownedBy(player).get(0).ownerName());
        assertTrue(loaded.legacyImported());
    }
}
