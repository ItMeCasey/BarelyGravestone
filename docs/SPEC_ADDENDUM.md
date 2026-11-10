# Barely Gravestone: spec addendum from Casey (read with the brief)

Added 2026-11-09 after the brief was sent:

- **Team members can open:** new config option `teamMembersCanOpen` (default false). When true, a member of the dead player's team can collect the gravestone too.
  - Team = the vanilla scoreboard team (a player on the same team as the owner counts).
  - When FTB Teams or Open Parties and Claims is loaded and has a build for that version, treat their party/team members the same way (soft dependency; n/a where no build exists).
  - It works with `collectBy`: `OWNER_ONLY` means owner plus (if enabled) team members; `ANYONE` overrides both.
  - Put it in the config comments, the wiki configuration page and the self test (owner, teammate, stranger, op).
