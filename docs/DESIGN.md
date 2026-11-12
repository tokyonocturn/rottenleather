# Design

## One recipe, many loaders

```
spec/recipe.json                    <- the only place the recipe is defined
        |
        +-- tools/generate.py       -> shared/.../BuildDefaults.java, bukkit config.yml
        +-- tools/generate_forge.py -> forge/versions/<mc>/... (11 modules)
```

`spec/recipe.json` holds the input, output, output count, XP, cook time and conflict policy. The
generators also compute a SHA-256 fingerprint over the spec, which every platform reports at runtime
and in `/rottenleather status`. Two servers whose fingerprints differ are not running the same
recipe, which is what the proxy modules watch for.

Generated files are checked in. `python tools/generate.py --check` and
`python tools/generate_forge.py --check` fail if a generated file no longer matches the spec, so the
build cannot silently drift.

## Shared core (pure Java, no Minecraft classes)

`shared/src/main/java/com/rottenleather/core` is compiled into every module and tested on its own:

* `RecipeSpec` — validated recipe values; rejects empty ids, counts outside 1–64, negative XP, cook
  times outside 1–72000 ticks.
* `ConflictPolicy` / `ConflictDecision` — what to do when the input already has a recipe.
* `CookTimeRule` — given a configured cook time, the furnace's current progress and its current
  total, returns the total to write (or "unchanged"). It never returns a total at or below the
  progress, so a furnace cannot get stuck.
* `CookTimeTracker` — remembers which furnaces this mod adjusted and the value it replaced, keyed by
  furnace identity in a weak map so an unloaded chunk cannot leak tile entities. It restores the
  original value when the input changes, when the feature is disabled, and when the server stops.
  Restoration only happens if the field still holds the value this mod wrote, so another mod's later
  change wins.
* `FurnaceTarget` — the small interface each platform implements (input check, read progress, read
  total, write total).

## Forge modules

All shared Forge logic lives in `forge/shared`; the eleven version modules are generated and only
contain the parts that actually moved between 1.8 and 1.12.2:

| Concern | 1.8.x | 1.9–1.10 | 1.11+ |
| --- | --- | --- | --- |
| item registry field | `itemRegistry` | `REGISTRY` | `REGISTRY` |
| stack count | `stackSize` | `stackSize` | `getCount()` |
| chat component | `ChatComponentText` | `ChatComponentText` | `TextComponentString` |

Registration uses `GameRegistry.addSmelting`, i.e. the vanilla `FurnaceRecipes` map. XP is stored in
the same map Forge uses for every other furnace recipe, keyed by the result stack. With
`conflictPolicy=replace` the existing entry for the input is removed first and remembered, so a later
reload (or a switch back to `skip`) can restore it.

1.8.x Forge refuses to re-register a smelting input that already has a recipe and its map does not
reliably accept removal, so `replace` is reported as unavailable there (one warning) and the existing
recipe is kept. `skip` works on every version, and `replace` works on 1.9+ (verified on 1.12.2).

Cook time: 1.8–1.12.2 has no per-recipe cook time. The world-tick handler scans loaded tile entities
every 8 ticks, and for furnaces whose input slot holds this recipe's input it writes the configured
total through `TileEntityFurnace.getField(3)` / `setField(3, ticks)` — the public furnace field API
the vanilla GUI itself uses to sync progress. Compiled code is remapped by ForgeGradle, so no
reflection, SRG names or obfuscation tables are involved. When the input slot no longer holds the
recipe input, the original total is written back.

Scanning every 8 ticks is enough because the furnace only reads the total when it completes, and it
keeps the cost negligible in large modpacks (one list walk per 8 ticks, no per-furnace allocation
beyond the tracked ones).

## Bukkit plugin

One jar for 1.8.8–1.12.2. Recipe registration uses the Bukkit API (`FurnaceRecipe`), so it composes
with other plugins that use the same API. XP and cook time need the server internals the 1.8–1.12.2
Bukkit API does not expose, so `NmsBridge` reflects into the running CraftBukkit package:

* experience map of `RecipesFurnace` (keyed by result stack, like vanilla);
* `TileEntityFurnace.getField(2)` / `setField(3, ticks)` for progress;
* `World.tileEntityList` to find loaded furnaces.

Only class names are version-dependent (`net.minecraft.server.<v1_x_Ry>`); member names come from
the public furnace field API. Every lookup is defensive: if a fork removes something, the plugin
logs one warning and keeps the recipe at vanilla timing/XP instead of pretending the setting worked.

`conflictPolicy=replace` is not implementable on these versions (no per-recipe removal in the Bukkit
API), so the plugin reports it as unavailable and behaves like `skip`.

Item ids use the 1.13 names in the config even on 1.8–1.12 servers; `Materials` maps the flattened
names to the legacy enum (`porkchop` -> `PORK`, `cooked_porkchop` -> `GRILLED_PORK`, and so on), so
the same config works on both platforms.

The Bukkit plugin cannot apply a non-vanilla cook time before 1.10 either, because those CraftBukkit
builds have no total-cook-time field; it logs one warning and keeps vanilla timing, exactly like the
Forge mod.

## Proxies

BungeeCord/Waterfall and Velocity modules exist to solve the one problem a proxy can actually solve
for a recipe: backends disagreeing. They cannot smelt and do not pretend to — see
[`PROXIES.md`](PROXIES.md).

## Why the recipe stays in the vanilla furnace

* Hoppers, fuel, XP orbs, comparator output, redstone, furnace minecarts and every “automation”
  mod/plugin keep working, because nothing is custom.
* No new block or item means no registry ids, no chunk data, no client-side mod requirement and no
  world corruption when the mod is removed.
* Existing recipes are untouched unless the operator explicitly chooses `replace` for a colliding
  input.
