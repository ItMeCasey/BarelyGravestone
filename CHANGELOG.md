# Changelog

## 2026.11.3

- The grave list and the waiting Obituaries are now kept in the world's saved data instead of two text files, so they save with the world, follow world backups and copy with the save.
- Worlds from 2026.11.2 and older: the old `barelygravestone/graves.txt` and `pending_obituaries.txt` are imported once, every grave and waiting Obituary exactly as it was. The old files are never changed, moved or deleted.

## 2026.11.2

- Optional ModJS scripting: a `BarelyGravestone` global in server and startup scripts with grave created, collected and expired events (creating, collecting and expiring can be cancelled or decided by the script), read access to every grave (owner, items, XP, position, time left) and overrides for the options. See `docs/scripting.md`.
- Removed the hints: the Obituary tooltip line about right-clicking and following particles, the chat message when an Obituary is handed over, and the chat message after collecting a whole grave. The "not yours" message is shorter.

## 2026.11.1

First release.

- Graves with the dead player's skinned head on a pike; everything carried goes in (inventory, armor, off-hand, Curios slots).
- Placement: nearest valid spot, cobblestone at the surface for deaths in water or lava, cobblestone under the grave over the void.
- Obituary item and book screen with the live list of what is left in the grave; particles lead the way.
- Collect rules: `collectBy`, `teamMembersCanOpen`, operators and creative players; survival players cannot break a grave.
- XP price (`xpCostPercent`, `keepLostXp`), optional expiry timer, `/gravestone` commands for operators.
- Death markers on map mods.
