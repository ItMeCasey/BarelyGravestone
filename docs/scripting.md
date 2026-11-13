---
title: Scripting with ModJS
summary: Listen to grave events, read graves and change options from scripts.
---
With ModJS installed, server and startup scripts get a `BarelyGravestone` object. ModJS is not required; without it nothing changes. The object is not available in client scripts, because graves live on the server. Handlers are cleared and registered again every time the scripts reload. A handler that throws is logged as one line and the game carries on.

```js
// server_scripts/graves.js
BarelyGravestone.beforeCreate(e => {
  // no grave for anyone who died in the Nether: the items drop normally
  if (e.grave().dimension() == 'minecraft:the_nether') e.cancel()
})

BarelyGravestone.created(e => {
  const g = e.grave()
  console.log(g.ownerName() + ' died: grave at ' + g.x() + ' ' + g.y() + ' ' + g.z()
    + ' holding ' + g.totalItems() + ' items, ' + g.count('minecraft:diamond') + ' diamonds')
})

BarelyGravestone.beforeCollect(e => {
  // let one named player open any grave
  if (e.collectorName() == 'Alex') e.allow()
  // and nobody but the owner may open a grave that holds a diamond block
  else if (!e.isOwner() && e.grave().count('minecraft:diamond_block') > 0) e.deny()
})

BarelyGravestone.expiring(e => {
  // graves with 1000+ items never expire
  if (e.grave().totalItems() >= 1000) e.cancel()
})

BarelyGravestone.setOption('graveExpiryMinutes', 60)
```

## Events

| Call | When | Event methods |
|---|---|---|
| `beforeCreate(fn)` | After the place is chosen, before anything changes. | `grave()`, `cancel()` (no grave; items drop normally and no XP is taken) |
| `created(fn)` | After the grave exists. | `grave()` |
| `beforeCollect(fn)` | A player right-clicks a grave. | `grave()`, `collectorId()`, `collectorName()`, `isOwner()`, `allowed()`, `allow()`, `deny()`, `cancel()` (same as `deny`) |
| `collected(fn)` | After a player took items. | `grave()`, `collectorId()`, `collectorName()`, `stacksLeft()`, `emptied()` |
| `expiring(fn)` | The expiry timer ran out. | `grave()`, `cancel()` (the grave stays and its timer starts again) |
| `expired(fn)` | After a grave expired and dropped its items. | `grave()` |

`beforeCollect` starts with what the options decided (`allowed()`), and every handler can change it. The chat message for a refused player is the normal one.

## Reading graves

| Call | Returns |
|---|---|
| `isReady()` | `true` while a world is loaded. |
| `graves()` | Every grave as a light view (no items). |
| `gravesOf(nameOrUuid)` | The graves of one player, light views. |
| `get(id)` | One grave with its items. `id` may be the first characters of the id. `null` if none or ambiguous. |
| `graveAt(dimension, x, y, z)` | The grave at that position with its items, or `null`. |
| `removeGrave(id)` | Removes a grave and drops its items there. Returns whether it did. |

A grave view has `id()`, `ownerId()`, `ownerName()`, `dimension()`, `x()`, `y()`, `z()`, `createdMillis()`, `deathMessage()`, `xp()` (points stored), `xpCostLevels()`, `minutesLeft()` (`-1` without a timer), `full()` (`true` when the items were read), `items()` (stacks with `id()` and `count()`), `itemCount()`, `totalItems()` and `count(itemId)`. Views are snapshots: they do not change later.

## Options

| Call | Does |
|---|---|
| `optionNames()` | The option names: `collectBy`, `teamMembersCanOpen`, `xpCostPercent`, `keepLostXp`, `graveExpiryMinutes`, `showCoordinates`, `deathMarker`, `obituaryParticles`. |
| `option(name)` | The value the game uses now. |
| `options()` | All of them. |
| `setOption(name, value)` | Overrides one until the game restarts; the config file is not changed. `collectBy` takes `'OWNER_ONLY'` or `'ANYONE'`, numbers are clamped, flags take `true` or `false`. Returns whether the value was accepted. |
| `resetOption(name)` | Back to the config value; `'all'` resets every override. |

An override applies to graves made or opened from then on; it does not change graves that already exist, except that a changed expiry applies to every grave the next time its timer is checked.
