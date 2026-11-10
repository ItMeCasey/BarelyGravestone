package com.itmecasey.barelygravestone.core;

/**
 * Where a grave goes.
 *
 * @param x       grave x
 * @param y       grave y
 * @param z       grave z
 * @param support {@code true} when a cobblestone block has to be placed under the grave first, at {@code y - 1}
 */
public record GravePlan(int x, int y, int z, boolean support) {
}
