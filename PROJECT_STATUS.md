# Project Status
<!-- status-format: 1 -->
Updated: 2026-11-10
Summary: Barely Gravestone is built for all ten targets (Forge and NeoForge on 26.3, 26.2, 26.1.2, 1.21.1, 1.20.1); the reference builds were run on real clients, the release flow (CI, tag, CurseForge, wiki deploy) is the project manager's.
Spend: none

## Foundation
- [x] Ten Gradle modules (forge and neoforge for 26.3, 26.2, 26.1.2, 1.21.1, 1.20.1), version 2026.11.1 everywhere, MIT (w:2)
- [x] Shared Minecraft-free core with unit tests: placement planner, access rules, XP math, grave index, pending Obituaries (16 tests pass in all ten modules) (w:4)
- [x] All ten release jars build, hold no dev classes, and the 1.20.1 jars have SRG names (verify_jars: 10 ok, 284 SRG names) (w:3)

## Features (seen on real clients unless noted)
- [x] Grave on death with items, armor and off-hand; placement incl. cobblestone support in water, lava and void (headless self test, 45 checks) (w:5)
- [x] Gravestone block: pike, skinned head renderer, unbreakable in survival (frames: Forge 26.3, NeoForge 26.3, NeoForge 1.21.1, NeoForge 1.20.1) (w:4)
- [x] Obituary item and book screen with live item list, particles toward the grave (frames, same builds) (w:5)
- [x] Collect rules: collectBy, teamMembersCanOpen, ops, XP cost and keepLostXp, expiry, /gravestone commands (headless self test) (w:5)
- [x] Config file with comments (Forge config API and NeoForge config API) (w:2)
- [x] Death marker on JourneyMap, every build: shown at the grave and removed when emptied, seen with real JourneyMap on NeoForge 26.3, Forge 26.3, NeoForge 1.21.1 and NeoForge 1.20.1; compile-checked on the other six (w:5)
- [x] Death marker on Xaero's Minimap and World Map, VoxelMap, FTB Chunks: n/a: none of them has a public waypoint API (FTB Chunks has no waypoints; Xaero's and VoxelMap make their own death waypoint) (w:1)
- [ ] Death marker on Antique Atlas (builds exist for 1.21.1 Forge and NeoForge and Forge 1.20.1, none for 26.x): not integrated yet, its marker API has not been looked at (w:2)
- [x] Team links: Open Parties and Claims verified with a real party on NeoForge 1.21.1 (member counts, outsider does not); FTB Teams written against its published API (javap-checked), builds exist for NeoForge 1.21.1 and 26.1.2 and Forge 1.20.1, not run with the mod installed (w:2)
- [x] Curios: n/a on Forge 1.21.1 and 26.x (no Curios build); on the others the grave takes what Curios drops with the death loot (tested with a stand-in drop, not with Curios installed) (w:2)

## Ports and checks
- [x] Forge 26.3, NeoForge 26.3: self test and real-client tour (w:3)
- [x] Forge and NeoForge 26.2, 26.1.2: compile and unit tests; self tests run in CI (w:3)
- [x] NeoForge 1.21.1: self test and real-client tour; Forge 1.21.1 compile and unit tests (w:3)
- [x] Forge and NeoForge 1.20.1: self test (Forge), real-client tour (NeoForge); both jars load in real Forge 47.4.28 and NeoForge 47.1.106 installs (w:4)

## Documentation and listing
- [x] README, CHANGELOG, CurseForge description, listing kit (w:3)
- [x] Branding: icon, banners, headers, social card (w:2)
- [x] Wiki draft pages in the site folder (home, getting-started, configuration, commands, faq, compatibility, versions, known-gaps) (w:3)
- [x] GitHub workflows, issue templates, upload script (project id 0 until the CurseForge project exists) (w:2)
- [ ] Push, CI run, v2026.11.1 tag, CurseForge upload and wiki deploy (project manager) (w:3)
