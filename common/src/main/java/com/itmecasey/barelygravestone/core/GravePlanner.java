package com.itmecasey.barelygravestone.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the spot for a grave. In order:
 * <ol>
 *   <li>A death in a fluid puts a cobblestone block at the surface and the grave on top of it.</li>
 *   <li>The nearest valid spot within {@value #RADIUS} blocks: a free cell standing on a solid block.</li>
 *   <li>With no such spot, the grave goes on the ground below the death (or on the surface of a lake there).</li>
 *   <li>With no ground at all (the void), the grave goes where the player died, on a new cobblestone block.</li>
 * </ol>
 * Returns {@code null} when nothing fits (for instance a death at the very top of the world); the items then drop normally.
 */
public final class GravePlanner {
    /** How far from the death the nearest-spot search looks. */
    public static final int RADIUS = 3;
    /** How far below the death the search for ground goes. */
    public static final int FALL_LIMIT = 96;
    /** How far above a death inside solid rock the search for open space goes. */
    public static final int CLIMB_LIMIT = 24;

    private GravePlanner() {
    }

    public static GravePlan plan(Terrain world, int x, int y, int z) {
        int minY = world.minY();
        int maxY = world.maxY();
        int startY = Math.max(minY, Math.min(maxY - 1, y));

        if (world.fluid(x, startY, z)) {
            GravePlan onSurface = surfaceOf(world, x, startY, z);
            if (onSurface != null) {
                return onSurface;
            }
        }

        GravePlan near = nearest(world, x, startY, z);
        if (near != null) {
            return near;
        }

        // Below the death: ground, a lake, or nothing.
        for (int yy = startY - 1, steps = 0; yy >= minY && steps < FALL_LIMIT; yy--, steps++) {
            if (world.fluid(x, yy, z)) {
                GravePlan lake = surfaceOf(world, x, yy, z);
                if (lake != null) {
                    return lake;
                }
                break;
            }
            if (world.solidTop(x, yy, z)) {
                if (world.replaceable(x, yy + 1, z)) {
                    return new GravePlan(x, yy + 1, z, false);
                }
                break;
            }
            if (!world.replaceable(x, yy, z)) {
                break;
            }
        }

        // No ground to land on: the grave stays where the player died, on a cobblestone block.
        return supported(world, x, startY, z);
    }

    /** From any fluid cell, climbs to the top of the column and puts the support in its last fluid cell. */
    private static GravePlan surfaceOf(Terrain world, int x, int y, int z) {
        int top = y;
        while (top + 1 < world.maxY() && world.fluid(x, top + 1, z)) {
            top++;
        }
        if (top + 1 < world.maxY() && world.replaceable(x, top + 1, z)) {
            return new GravePlan(x, top + 1, z, true);
        }
        return null;
    }

    private static GravePlan nearest(Terrain world, int x, int y, int z) {
        List<int[]> cells = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    cells.add(new int[] {dx, dy, dz});
                }
            }
        }
        // Closest first; at equal distance prefer staying level, then going down over going up.
        cells.sort(Comparator
                .comparingInt((int[] c) -> c[0] * c[0] + c[1] * c[1] + c[2] * c[2])
                .thenComparingInt(c -> Math.abs(c[1]))
                .thenComparingInt(c -> c[1]));
        for (int[] c : cells) {
            int cx = x + c[0];
            int cy = y + c[1];
            int cz = z + c[2];
            if (cy - 1 < world.minY() || cy >= world.maxY()) {
                continue;
            }
            if (world.replaceable(cx, cy, cz) && world.solidTop(cx, cy - 1, cz)) {
                return new GravePlan(cx, cy, cz, false);
            }
        }
        return null;
    }

    /** The grave at the death cell (or the first free cell above it) with a cobblestone support under it. */
    private static GravePlan supported(Terrain world, int x, int y, int z) {
        for (int yy = y, steps = 0; yy < world.maxY() && steps <= CLIMB_LIMIT; yy++, steps++) {
            if (!world.replaceable(x, yy, z)) {
                continue;
            }
            if (world.solidTop(x, yy - 1, z)) {
                return new GravePlan(x, yy, z, false);
            }
            if (yy - 1 >= world.minY() && (world.replaceable(x, yy - 1, z) || world.fluid(x, yy - 1, z))) {
                return new GravePlan(x, yy, z, true);
            }
        }
        return null;
    }
}
