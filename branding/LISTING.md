# Barely Gravestone listing kit: CurseForge

Everything needed to fill in the CurseForge project form. Files are in `branding/icon/` and `branding/banners/`.

| Field | CurseForge |
|---|---|
| Project ID | not created yet (Casey creates the project; record the id here and in `.github/scripts/publish_curseforge.py`) |
| Name | Barely Gravestone |
| Slug / URL | `barelygravestone` |
| Mod id | `barelygravestone` |
| Icon / logo | `icon/icon_512.png` (square, at least 400x400) |
| Class | Mods |
| Main category | Utility and QoL, or the closest "death and inventory" category the form offers |
| Loaders | Forge and NeoForge |
| Game versions | Minecraft 1.20.1, 1.21.1, 26.1.2, 26.2 and 26.3, ten jars, one per loader and Minecraft version |
| Environment | Client and Server (the upload script sends both) |
| License | MIT |
| Source / Issues | https://github.com/ItMeCasey/BarelyGravestone and `/issues` |
| Wiki | https://itmecasey.com/wiki/barelygravestone/ |

### One-line description

> Graves for Forge and NeoForge: your items wait under a pike with your skinned head on top, and an Obituary book with particles shows the way back.

### Gallery

| File | Use |
|---|---|
| `banners/features.png` (1920x1080) | First gallery image |
| `banners/gallery.png` (1920x1080) | The config options, second gallery image |
| `docs/frames/forge-26.3_*.png` | Real client screenshots (pike, Obituary screen, particles) |
| `banners/social.png` (1280x640) | GitHub social preview |
| `banners/banner.png` (800x150, saved at 2x) | Top of the description |
| `banners/h_*.png` (800x46, saved at 2x) | Section headers (features, how, collect, compat, faq) |

Rebuild: `python branding/source/make_icon.py`, `python branding/source/build_banners_html.py`, `python branding/source/render_banners.py` (needs Pillow and Google Chrome).

The description is in [`curseforge/DESCRIPTION.md`](../curseforge/DESCRIPTION.md); paste it with the description type set to **Markdown**. Its images load from GitHub, so `branding/` has to be pushed first. Its Tested line says only what was run.
