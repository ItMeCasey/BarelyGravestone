# Project Status
<!-- status-format: 1 -->
Updated: 2026-11-09
Summary: Barely Gravestone puts a dead player's items in a skinned-head gravestone with an Obituary; Forge 26.3 is built and seen on a real client, the other nine builds are scaffolded but not ported yet.
Spend: none

## Foundation
- [x] Ten Gradle modules scaffolded (forge and neoforge for 26.3, 26.2, 26.1.2, 1.21.1, 1.20.1), version 2026.11.1, MIT (w:2)
- [x] Shared Minecraft-free core with unit tests: placement planner, access rules, XP math, grave index, pending Obituaries (16 tests pass) (w:4)

## Reference build: Forge 26.3
- [x] Grave on death with items, armor and off-hand; placement incl. cobblestone support in water, lava and void (w:5)
- [x] Gravestone block: pike, skinned head renderer, unbreakable in survival (frame seen on a real client) (w:4)
- [x] Obituary item and book screen with live item list, particles toward the grave (frames seen) (w:5)
- [x] Collect rules: collectBy, teamMembersCanOpen, ops, XP cost and keepLostXp, expiry, /gravestone commands (headless self test, 44 checks) (w:5)
- [x] Config file with comments (Forge config API) (w:2)
- [ ] Curios items (drops from other mods join the grave; tested with a fake drop, not with Curios installed) (w:2)
- [ ] Death marker on map mods (JourneyMap, Xaero's, VoxelMap, FTB Chunks, Antique Atlas): lookup and integration (w:5)
- [ ] Team integrations: FTB Teams, Open Parties and Claims (w:2)

## Ports
- [ ] NeoForge 26.3 (w:3)
- [ ] Forge and NeoForge 26.2 (w:3)
- [ ] Forge and NeoForge 26.1.2 (w:3)
- [ ] Forge and NeoForge 1.21.1 (w:4)
- [ ] Forge and NeoForge 1.20.1, SRG name check, real-install load check (w:5)

## Documentation and release
- [ ] README, CHANGELOG, CurseForge description, wiki draft pages (w:3)
- [ ] Branding: icon and banners (w:2)
- [ ] GitHub workflows, issue templates, publish script (PM owns CI and release) (w:2)
