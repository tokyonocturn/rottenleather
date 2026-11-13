# Rotten Flesh to Leather

Smelt **1 rotten flesh in a normal furnace into 1 leather** on Minecraft 1.8–1.12.2, with normal fuel,
normal experience and normal hopper automation. Nothing is replaced: the recipe is added to the
vanilla furnace, so every other recipe, mod, plugin and redstone contraption keeps working.

The recipe is defined once in [`spec/recipe.json`](spec/recipe.json) and generated into every module,
so the Forge mod and the Bukkit plugin cannot drift apart.

```
minecraft:rotten_flesh x1 --furnace--> minecraft:leather x1
```

## Downloads

| Platform | File | Minecraft |
| --- | --- | --- |
| Forge | `rottenleather-<mcversion>-1.0.2.jar` | one jar per supported 1.8–1.12.2 release |
| Bukkit / Spigot / Paper | `rottenleather-bukkit-1.0.2.jar` | 1.8.8–1.12.2 (see the matrix) |
| BungeeCord / Waterfall | `rottenleather-bungee-1.0.2.jar` | proxy module, any modern BungeeCord/Waterfall |
| Velocity | `rottenleather-velocity-1.0.2.jar` | Velocity 3.x |

See [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md) for the exact versions and how each one was
verified. Versions that were not tested are not listed as supported.

The Bukkit plugin is verified on **Paper 1.8.8, 1.9.4, 1.10.2, 1.11.2, 1.12 and 1.12.2** and on
**Spigot 1.8.8, 1.9, 1.12 and 1.12.2**, each with its own fresh server boot per scenario. Spigot
servers were built locally from SpigotMC's BuildTools and every Spigot probe asserts the server
reports itself as CraftBukkit/Spigot at that exact Minecraft version before any check runs. Spigot
1.8.9, 1.9.4, 1.10, 1.10.2, 1.11, 1.11.2 and 1.12.1 could not be built in this environment; the
concrete reason for each is recorded in the matrix.

## Install

**Forge (single player or server):** put the jar matching your Minecraft version in `mods/`.
Nothing else is required; the recipe is registered on load.

**Bukkit / Spigot / Paper:** drop `rottenleather-bukkit-1.0.2.jar` in `plugins/`.

**BungeeCord / Waterfall / Velocity:** drop the matching proxy jar in `plugins/`. The proxy has no
world and therefore no furnace — see [`docs/PROXIES.md`](docs/PROXIES.md) for what the module does
and does not do.

Full instructions, including config files and permission nodes, are in
[`docs/INSTALL.md`](docs/INSTALL.md).

## Configure

Forge writes `config/rottenleather.cfg`; Bukkit writes `plugins/RottenFleshToLeather/config.yml`.
Both expose the same values:

| Setting | Default | Meaning |
| --- | --- | --- |
| `inputItem` / `input-item` | `minecraft:rotten_flesh` | furnace input |
| `outputItem` / `output-item` | `minecraft:leather` | furnace output |
| `outputCount` / `output-count` | `1` | items produced per smelt (1–64) |
| `experience` | `0.1` | XP per smelt, like vanilla food smelting |
| `smeltTimeTicks` / `smelt-time-ticks` | `200` | cook time; 20 ticks = 1 second |
| `conflictPolicy` / `conflict-policy` | `skip` | `skip` keeps another recipe for the same input, `replace` takes the input over |
| `adjustCookTime` (Forge only) | `true` | allow a non-vanilla cook time at all |
| `logRegistration` / `log-registration` | `true` | log the active recipe and any conflict decision |

Every value is validated. An invalid or unreadable config falls back to the defaults with one
warning instead of stopping the server.

Non-vanilla cook times are applied **per furnace**: only furnaces holding this recipe's input are
changed, and their original timing is restored the moment the input slot changes, so other recipes
keep vanilla 200-tick timing.

Minecraft 1.8.x and 1.9.x furnaces compare progress against a hard-coded 200 and expose no
total-cook-time field, and Forge 1.10 behaves the same way, so `smeltTimeTicks` is ignored on those
combinations (the mod logs one warning and smelts at vanilla speed). `/rottenleather status` reports
`customCookTime` for the running server; custom timing is verified on Forge 1.10.2-1.12.2 and on
Paper 1.10.2-1.12.2.

`conflictPolicy=replace` needs a way to remove an existing recipe. That exists on Forge 1.9+ only;
on Forge 1.8.x and on every Bukkit 1.8–1.12.2 server it is unavailable, and the mod says so and
keeps the existing recipe instead of deleting it.

## Commands

| Command | Where | Purpose |
| --- | --- | --- |
| `/rottenleather status` | Forge, Bukkit (`/rfl`) | print the active recipe, XP, ticks, fingerprint and registration state |
| `/rottenleather reload` | Forge, Bukkit (`/rfl`) | re-read the config and apply it live (see the note below) |
| `/rflnet` | BungeeCord/Waterfall/Velocity | show the recipe reports collected from backends |

Bukkit's command requires the `rottenleather.admin` permission (default: op).

On Forge, reload re-registers the recipe, so input, output, count, XP and cook time all change. The
1.8-1.12.2 Bukkit API has no way to remove a registered furnace recipe, so a Bukkit reload applies a
changed XP and cook time immediately and logs that an output-count change only takes effect after a
server restart.

## Build from source

Requirements: JDK 8 for the Minecraft 1.8–1.12.2 toolchain, Python 3 for the generators, and the
Gradle distributions listed in `tools/build-forge.sh`.

```bash
python tools/generate.py          # regenerate BuildDefaults.java + bukkit config from the spec
python tools/generate_forge.py    # regenerate the 11 Forge version modules
bash tools/test-core.sh           # shared recipe/conflict/cook-time suite
bash tools/build-forge.sh 1.12.2  # one Forge version (omit the argument for all of them)
bash tools/build-bukkit.sh        # Bukkit plugin
bash tools/build-proxies.sh       # Bungee + Velocity modules
bash tools/build-all.sh           # everything above, into dist/
```

Artifacts land in `dist/`.

## Test

`tools/test-core.sh` runs the shared Java suite (spec validation, conflict decisions, cook-time
rules). `tools/probe-forge.sh <mc> <forge> <scenario>` and `tools/probe-bukkit.sh <mc> <scenario>`
boot a real server with a fresh JVM and world for **one** scenario, place a hopper-fed furnace, and
assert on observable state only: the furnace block turning lit, the output chest's exact NBT, the
coal/input/output slots emptying, and the smelt measured in server ticks. Scenarios cover the default
recipe, output count, XP, custom cook time (or the documented warning where a version cannot honour
it), vanilla-recipe isolation, live reload, and both conflict policies.

`tools/run-matrix.sh` runs every version and scenario in sequence and records `logs/MATRIX.txt`, from
which `tools/matrix.py` regenerates [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md).
`tools/probe-proxies.sh` boots BungeeCord and Velocity and proves the modules enable.

See [`docs/TESTING.md`](docs/TESTING.md) for what each check proves and what it does not cover.

## Design

The mod never adds a block, tile entity or GUI. It registers a normal furnace recipe and, when a
non-vanilla cook time is configured, adjusts the furnace's own progress fields through the public
furnace field API that reobfuscation maps for every supported version. Details, including why Fabric,
Geyser and Floodgate get documentation instead of a jar, are in [`docs/DESIGN.md`](docs/DESIGN.md).

## License

MIT — see [`LICENSE`](LICENSE).
