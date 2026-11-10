# Known gaps

The user-facing list is in the wiki draft (`wiki/barelygravestone/pages/known-gaps.md` in the site folder). Short version:

- Curios only tested with a stand-in drop; FTB Teams never run with the mod installed; multiplayer with real connections not tested.
- Real clients: Forge 26.3, NeoForge 26.3, NeoForge 1.21.1, NeoForge 1.20.1. The other builds are covered by the headless self test (CI is not allowed to run it, see below) and compile.
- Antique Atlas: real mod seen on NeoForge 1.21.1 only.
- **Build risk, JourneyMap's Maven:** the JourneyMap integration compiles against API jars (snapshot versions) from `https://jm.gserv.me/repository/maven-public/`. If that site is unreachable the build of every module fails at dependency resolution. Fallback: keep a copy of the API jar in `libs/` and use `compileOnly files(...)` instead, or remove the `compat/journeymap` package from the build. Nothing of JourneyMap ships in the jars.
- The Antique Atlas and Open Parties and Claims compile-time/dev dependencies come from `curse.maven`; mirror them to the private Maven before relying on CI (see the playbook).
