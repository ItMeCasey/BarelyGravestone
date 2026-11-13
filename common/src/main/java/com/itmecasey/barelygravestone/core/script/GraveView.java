package com.itmecasey.barelygravestone.core.script;

import java.util.List;

/**
 * A read-only snapshot of one grave, as scripts see it. Only strings and numbers, so any script engine can use it. Call the
 * methods ({@code grave.ownerName()}).
 *
 * @param id            the grave's id (the first eight characters are what {@code /gravestone list} shows)
 * @param ownerId       the dead player's UUID
 * @param ownerName     the dead player's name
 * @param dimension     dimension id such as {@code minecraft:overworld}
 * @param x             grave position
 * @param y             grave position
 * @param z             grave position
 * @param createdMillis real time the grave was made, milliseconds since 1970 (0 when unknown)
 * @param deathMessage  how the player died
 * @param xp            experience points stored in the grave
 * @param xpCostLevels  experience levels the grave cost
 * @param items         the stacks inside; empty for a light view (see {@link #full()})
 * @param full          {@code true} when {@code items} was read from the grave itself; list views leave it out
 * @param minutesLeft   minutes until the grave expires, or -1 when it never does
 */
public record GraveView(String id, String ownerId, String ownerName, String dimension, int x, int y, int z, long createdMillis,
                        String deathMessage, int xp, int xpCostLevels, List<GraveItem> items, boolean full, int minutesLeft) {
    /** How many stacks the grave holds (only meaningful when {@link #full()}). */
    public int itemCount() {
        return items.size();
    }

    /** How many items in total (counts added up). */
    public int totalItems() {
        int total = 0;
        for (GraveItem item : items) {
            total += item.count();
        }
        return total;
    }

    /** How many of one item the grave holds, by id such as {@code minecraft:diamond}. */
    public int count(String itemId) {
        int total = 0;
        for (GraveItem item : items) {
            if (item.id().equals(itemId)) {
                total += item.count();
            }
        }
        return total;
    }

    /** One stack of a grave. */
    public record GraveItem(String id, int count) {
    }
}
