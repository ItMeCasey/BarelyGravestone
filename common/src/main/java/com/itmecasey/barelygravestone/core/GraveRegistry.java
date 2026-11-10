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
 * The index of every grave in a world: who owns it, where it stands, when it was made. The graves themselves keep their
 * contents in their block entities; the index lets the commands list graves without loading chunks, and lets an Obituary
 * learn that its grave is gone. Kept in a plain text file in the world folder.
 */
public final class GraveRegistry {
    /** One grave in the index. */
    public record Entry(UUID id, UUID owner, String ownerName, String dimension, int x, int y, int z, long createdMillis) {
        public boolean at(String dim, int px, int py, int pz) {
            return dimension.equals(dim) && x == px && y == py && z == pz;
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private Path file;

    /** An empty index that is never written to disk. */
    public GraveRegistry() {
    }

    /** Loads the index from {@code file} (missing means empty) and saves back to it after every change. */
    public static GraveRegistry open(Path file) {
        GraveRegistry registry = new GraveRegistry();
        registry.file = file;
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    Entry entry = parse(line);
                    if (entry != null) {
                        registry.entries.put(entry.id(), entry);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + file, e);
            }
        }
        return registry;
    }

    public synchronized void add(Entry entry) {
        entries.put(entry.id(), entry);
        save();
    }

    /** Removes a grave; returns whether it was in the index. */
    public synchronized boolean remove(UUID id) {
        boolean removed = entries.remove(id) != null;
        if (removed) {
            save();
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

    static String encode(Entry e) {
        String name = Base64.getEncoder().encodeToString(e.ownerName().getBytes(StandardCharsets.UTF_8));
        return String.join("\t", e.id().toString(), e.owner().toString(), name, e.dimension(),
                Integer.toString(e.x()), Integer.toString(e.y()), Integer.toString(e.z()), Long.toString(e.createdMillis()));
    }

    static Entry parse(String line) {
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

    private void save() {
        if (file == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (Entry entry : entries.values()) {
            lines.add(encode(entry));
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
