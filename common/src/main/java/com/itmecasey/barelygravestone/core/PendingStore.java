package com.itmecasey.barelygravestone.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Obituaries waiting for their player: made at death, handed over at respawn, or later when the inventory was full. Each
 * waiting item is kept as one opaque line of text (the game code serializes the stack), saved in the world folder so a
 * restart does not lose it.
 */
public final class PendingStore {
    private final Map<UUID, List<String>> pending = new LinkedHashMap<>();
    private Path file;

    /** An empty store that is never written to disk. */
    public PendingStore() {
    }

    /** Loads the store from {@code file} (missing means empty) and saves back to it after every change. */
    public static PendingStore open(Path file) {
        PendingStore store = new PendingStore();
        store.file = file;
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int tab = line.indexOf('\t');
                    if (tab > 0) {
                        try {
                            UUID player = UUID.fromString(line.substring(0, tab));
                            String text = new String(Base64.getDecoder().decode(line.substring(tab + 1)), StandardCharsets.UTF_8);
                            store.pending.computeIfAbsent(player, k -> new ArrayList<>()).add(text);
                        } catch (IllegalArgumentException e) {
                            // A damaged line is skipped.
                        }
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + file, e);
            }
        }
        return store;
    }

    public synchronized void add(UUID player, String item) {
        pending.computeIfAbsent(player, k -> new ArrayList<>()).add(item);
        save();
    }

    public synchronized boolean has(UUID player) {
        List<String> list = pending.get(player);
        return list != null && !list.isEmpty();
    }

    /** What is waiting for the player, without removing it. */
    public synchronized List<String> peek(UUID player) {
        List<String> list = pending.get(player);
        return list == null ? List.of() : new ArrayList<>(list);
    }

    /** Takes the first waiting item, or {@code null} when none waits. */
    public synchronized String poll(UUID player) {
        List<String> list = pending.get(player);
        if (list == null || list.isEmpty()) {
            return null;
        }
        String first = list.remove(0);
        if (list.isEmpty()) {
            pending.remove(player);
        }
        save();
        return first;
    }

    /** Puts an item back at the front (a hand-over that did not fit). */
    public synchronized void pushFront(UUID player, String item) {
        pending.computeIfAbsent(player, k -> new ArrayList<>()).add(0, item);
        save();
    }

    /** The players that have something waiting. */
    public synchronized List<UUID> players() {
        return new ArrayList<>(pending.keySet());
    }

    private void save() {
        if (file == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<UUID, List<String>> e : pending.entrySet()) {
            for (String item : e.getValue()) {
                lines.add(e.getKey() + "\t" + Base64.getEncoder().encodeToString(item.getBytes(StandardCharsets.UTF_8)));
            }
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write " + file, e);
        }
    }
}
