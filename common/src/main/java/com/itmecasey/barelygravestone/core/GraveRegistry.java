package com.itmecasey.barelygravestone.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The index of every grave in a world: who owns it, where it stands, when it was made. The graves themselves keep their
 * contents in their block entities; the index lets the commands list graves without loading chunks, and lets an Obituary
 * learn that its grave is gone. The world store ({@link WorldState}) keeps it with the world save.
 */
public final class GraveRegistry {
    /** One grave in the index. */
    public record Entry(UUID id, UUID owner, String ownerName, String dimension, int x, int y, int z, long createdMillis) {
        public boolean at(String dim, int px, int py, int pz) {
            return dimension.equals(dim) && x == px && y == py && z == pz;
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private Runnable onChange = () -> { };

    /** An empty index. */
    public GraveRegistry() {
    }

    /** Called after every change, so the world store can mark itself for saving. */
    public synchronized void setOnChange(Runnable listener) {
        this.onChange = listener == null ? () -> { } : listener;
    }

    /** Builds an index from stored lines; a damaged line is skipped. */
    public static GraveRegistry fromLines(List<String> lines) {
        GraveRegistry registry = new GraveRegistry();
        for (String line : lines) {
            Entry entry = parse(line);
            if (entry != null) {
                registry.entries.put(entry.id(), entry);
            }
        }
        return registry;
    }

    /** The index as stored lines (one per grave). */
    public synchronized List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (Entry entry : entries.values()) {
            lines.add(encode(entry));
        }
        return lines;
    }

    public synchronized void add(Entry entry) {
        entries.put(entry.id(), entry);
        onChange.run();
    }

    /** Removes a grave; returns whether it was in the index. */
    public synchronized boolean remove(UUID id) {
        boolean removed = entries.remove(id) != null;
        if (removed) {
            onChange.run();
        }
        return removed;
    }

    public synchronized Entry get(UUID id) {
        return entries.get(id);
    }

    public synchronized boolean contains(UUID id) {
        return entries.containsKey(id);
    }

    public synchronized List<Entry> all() {
        return new ArrayList<>(entries.values());
    }

    public synchronized List<Entry> ownedBy(UUID owner) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.owner().equals(owner)) {
                result.add(entry);
            }
        }
        return result;
    }

    public synchronized Entry findAt(String dimension, int x, int y, int z) {
        for (Entry entry : entries.values()) {
            if (entry.at(dimension, x, y, z)) {
                return entry;
            }
        }
        return null;
    }

    public synchronized int size() {
        return entries.size();
    }

    public static String encode(Entry e) {
        String name = Base64.getEncoder().encodeToString(e.ownerName().getBytes(StandardCharsets.UTF_8));
        return String.join("\t", e.id().toString(), e.owner().toString(), name, e.dimension(),
                Integer.toString(e.x()), Integer.toString(e.y()), Integer.toString(e.z()), Long.toString(e.createdMillis()));
    }

    /** Parses one stored line; {@code null} for a damaged one. */
    public static Entry parse(String line) {
        String[] f = line.split("\t");
        if (f.length != 8) {
            return null;
        }
        try {
            String name = new String(Base64.getDecoder().decode(f[2]), StandardCharsets.UTF_8);
            return new Entry(UUID.fromString(f[0]), UUID.fromString(f[1]), name, f[3],
                    Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]), Long.parseLong(f[7]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
