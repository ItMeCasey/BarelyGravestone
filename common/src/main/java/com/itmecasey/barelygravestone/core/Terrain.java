package com.itmecasey.barelygravestone.core;

/** What the grave placement needs to know about the blocks around a death. Implemented over a level by the game code. */
public interface Terrain {
    /** The lowest block y of the world. */
    int minY();

    /** One above the highest block y of the world. */
    int maxY();

    /** The cell holds nothing solid and no fluid, so a grave may be put into it (air, tall grass, snow layers). */
    boolean replaceable(int x, int y, int z);

    /** The cell holds water, lava or another fluid. */
    boolean fluid(int x, int y, int z);

    /** The cell is a solid block with a flat top that can carry a grave. */
    boolean solidTop(int x, int y, int z);
}
