package com.itmecasey.barelygravestone.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the mod keeps per world: the grave index, the Obituaries waiting for their players, and whether the files of
 * versions up to 2026.11.2 were already imported. The game code stores it with the world's saved data, so it saves with the
 * world, follows backups and copies with the save.
 *
 * <p>Versions up to 2026.11.2 kept the same two lists in {@code <world>/barelygravestone/graves.txt} and
 * {@code pending_obituaries.txt}. {@link #importLegacy(Path)} reads them once. It never changes, renames, moves or deletes them,
 * so a player can still go back to an older version.
 */
public final class WorldState {
    public static final String GRAVES_FILE = "graves.txt";
    public static final String PENDING_FILE = "pending_obituaries.txt";

    /** What an import did. */
    public record Import(Outcome outcome, int graves, int pending, int skipped) {
    }

    public enum Outcome {
        /** The old files were read and the data is now in the store. */
        IMPORTED,
        /** No old file exists, or it was imported before. Nothing was done. */
        NOTHING,
        /** The store already holds data, so the old files were left alone. */
        STORE_NOT_EMPTY,
        /** An old file exists but could not be read; the next start tries again. */
        UNREADABLE
    }

    private final GraveRegistry registry;
    private final PendingStore pending;
    private boolean legacyImported;
    private Runnable onChange = () -> { };

    public WorldState(List<String> graveLines, List<String> pendingLines, boolean legacyImported) {
        this.registry = GraveRegistry.fromLines(graveLines);
        this.pending = PendingStore.fromLines(pendingLines);
        this.legacyImported = legacyImported;
        registry.setOnChange(() -> onChange.run());
        pending.setOnChange(() -> onChange.run());
    }

    public WorldState() {
        this(List.of(), List.of(), false);
    }

    /** Called after every change; the saved data uses it to mark itself dirty. */
    public void setOnChange(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

    public GraveRegistry registry() {
        return registry;
    }

    public PendingStore pending() {
        return pending;
    }

    public boolean legacyImported() {
        return legacyImported;
    }

    public List<String> graveLines() {
        return registry.lines();
    }

    public List<String> pendingLines() {
        return pending.lines();
    }

    private boolean empty() {
        return registry.size() == 0 && pending.players().isEmpty();
    }

    /**
     * Imports the files of the older versions from {@code folder} ({@code <world>/barelygravestone}) when the store is empty and
     * they were not imported before. Each grave and each waiting Obituary is copied as it is; lines that cannot be read are
     * counted as skipped (they never described a usable grave). The files stay exactly as they are.
     */
    public Import importLegacy(Path folder) {
        if (legacyImported) {
            return new Import(Outcome.NOTHING, 0, 0, 0);
        }
        Path gravesFile = folder.resolve(GRAVES_FILE);
        Path pendingFile = folder.resolve(PENDING_FILE);
        boolean hasGraves = Files.isRegularFile(gravesFile);
        boolean hasPending = Files.isRegularFile(pendingFile);
        if (!hasGraves && !hasPending) {
            return new Import(Outcome.NOTHING, 0, 0, 0);
        }
        if (!empty()) {
            return new Import(Outcome.STORE_NOT_EMPTY, 0, 0, 0);
        }
        List<String> graveLines;
        List<String> pendingLines;
        try {
            graveLines = hasGraves ? readLines(gravesFile) : List.of();
            pendingLines = hasPending ? readLines(pendingFile) : List.of();
        } catch (IOException e) {
            return new Import(Outcome.UNREADABLE, 0, 0, 0);
        }
        int graves = 0;
        int waiting = 0;
        int skipped = 0;
        Runnable saved = onChange;
        onChange = () -> { };
        for (String line : graveLines) {
            if (line.isBlank()) {
                continue;
            }
            GraveRegistry.Entry entry = GraveRegistry.parse(line);
            if (entry == null) {
                skipped++;
            } else {
                registry.add(entry);
                graves++;
            }
        }
        for (String line : pendingLines) {
            if (line.isBlank()) {
                continue;
            }
            Map.Entry<UUID, String> parsed = PendingStore.parse(line);
            if (parsed == null) {
                skipped++;
            } else {
                pending.add(parsed.getKey(), parsed.getValue());
                waiting++;
            }
        }
        onChange = saved;
        legacyImported = true;
        onChange.run();
        return new Import(Outcome.IMPORTED, graves, waiting, skipped);
    }

    /** Reads a text file as UTF-8 without failing on damaged bytes (a half-written last line just fails to parse). */
    static List<String> readLines(Path file) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\r?\n", -1)) {
            lines.add(line);
        }
        return lines;
    }
}
