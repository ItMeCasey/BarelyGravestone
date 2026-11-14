package com.itmecasey.barelygravestone.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Obituaries waiting for their player: made at death, handed over at respawn, or later when the inventory was full. Each
 * waiting item is kept as one opaque line of text (the game code serializes the stack), kept in the world store
 * ({@link WorldState}) so a restart does not lose it.
 */
public final class PendingStore {
    private final Map<UUID, List<String>> pending = new LinkedHashMap<>();
    private Runnable onChange = () -> { };

    /** An empty store. */
    public PendingStore() {
    }

    /** Called after every change, so the world store can mark itself for saving. */
    public synchronized void setOnChange(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

    /** Builds a store from stored lines ({@code uuid TAB base64}); a damaged line is skipped. */
    public static PendingStore fromLines(List<String> lines) {
        PendingStore store = new PendingStore();
        for (String line : lines) {
            Map.Entry<UUID, String> parsed = parse(line);
            if (parsed != null) {
                store.pending.computeIfAbsent(parsed.getKey(), k -> new ArrayList<>()).add(parsed.getValue());
            }
        }
        return store;
    }

    /** One stored line as player and text, or {@code null} when it is damaged. */
    public static Map.Entry<UUID, String> parse(String line) {
        if (line == null) {
            return null;
        }
        int tab = line.indexOf('	');
        if (tab <= 0) {
            return null;
        }
        try {
            UUID player = UUID.fromString(line.substring(0, tab));
            String text = new String(Base64.getDecoder().decode(line.substring(tab + 1).trim()), StandardCharsets.UTF_8);
            return Map.entry(player, text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The store as stored lines (one per waiting item). */
    public synchronized List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<UUID, List<String>> e : pending.entrySet()) {
            for (String item : e.getValue()) {
                lines.add(e.getKey() + "	" + Base64.getEncoder().encodeToString(item.getBytes(StandardCharsets.UTF_8)));
            }
        }
        return lines;
    }

    public synchronized void add(UUID player, String item) {
        pending.computeIfAbsent(player, k -> new ArrayList<>()).add(item);
        onChange.run();
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
        onChange.run();
        return first;
    }

    /** Puts an item back at the front (a hand-over that did not fit). */
    public synchronized void pushFront(UUID player, String item) {
        pending.computeIfAbsent(player, k -> new ArrayList<>()).add(0, item);
        onChange.run();
    }

    /** The players that have something waiting. */
    public synchronized List<UUID> players() {
        return new ArrayList<>(pending.keySet());
    }
}
