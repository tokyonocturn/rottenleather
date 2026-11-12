# Compatibility matrix

This file is generated from the probe and build logs by `tools/matrix.py`; edit the logs, not the
table. A cell is **verified** only when that exact version has a full set of passing runtime logs,
each produced on a fresh server boot for that one scenario. Nothing is upgraded from a shared jar, a
compile test or a different Minecraft version.

Logs: `logs/MATRIX.txt`, `logs/probe-forge-<mc>-<scenario>.log`, `logs/probe-bukkit-<mc>-<scenario>.log`,
`logs/build-forge-matrix.log`. Design and reproduction: [`TESTING.md`](TESTING.md).

## Forge 1.8–1.12.2

| Minecraft | Forge | build + checks | runtime scenarios (fresh boot each) | support |
| --- | --- | --- | --- | --- |
| 1.8.8 | 1.8.8-11.15.0.1655 | pass (101 checks) | pass: default, custom, vanilla, reload, skip, replace | **verified** |
| 1.8.9 | 1.8.9-11.15.1.2318-1.8.9 | pass (101 checks) | pass: default, custom, vanilla, reload, skip, replace | **verified** |
| 1.9 | 1.9-12.16.1.1938-1.9.0 | pass (101 checks) | pass: default, custom, vanilla, reload, skip, replace | **verified** |
| 1.9.4 | 1.9.4-12.17.0.2317-1.9.4 | pass (101 checks) | pass: default, custom, vanilla, reload, skip, replace | **verified** |
| 1.10 | 1.10-12.18.0.2000-1.10.0 | pass (101 checks) | pass: default, custom, vanilla, reload, skip, replace | **verified** |
| 1.10.2 | 1.10.2-12.18.3.2511 | pass (101 checks) | pass: default, custom, vanilla, reload, skip, replace | **verified** |
| 1.11 | 1.11-13.19.1.2199 | pass (101 checks) | pass: default, custom, vanilla, reload, skip, replace | **verified** |
| 1.11.2 | 1.11.2-13.20.1.2588 | pass (101 checks) | pass: default, custom, vanilla, reload, skip, replace | **verified** |
| 1.12 | 1.12-14.21.1.2443 | pass (101 checks) | pass: default, custom, vanilla, reload, skip, replace | **verified** |
| 1.12.1 | 1.12.1-14.22.1.2485 | pass (101 checks) | pass: default, custom, vanilla, reload, skip, replace | **verified** |
| 1.12.2 | 1.12.2-14.23.5.2847 | pass (101 checks) | pass: default, custom, vanilla, reload, skip, replace | **verified** |

`build + checks` is the shared core suite (77 checks) plus the per-version Forge contract suite
(24 checks) compiled against that exact Minecraft/Forge version. A runtime scenario is only listed as
passing when its own log ends in `PASS <mc> <scenario>`.

Verified here: 1.8.8, 1.8.9, 1.9, 1.9.4, 1.10, 1.10.2, 1.11, 1.11.2, 1.12, 1.12.1, 1.12.2

## Bukkit / Spigot / Paper

One jar, `rottenleather-bukkit-1.0.1.jar`, compiled against the Spigot 1.8.8 API, resolves the running
CraftBukkit package at enable time.

| Server | runtime scenarios (fresh boot each) | support |
| --- | --- | --- |
| Paper 1.8.8 | pass: default, custom, vanilla, reload, skip | **verified** |
| Paper 1.9.4 | pass: default, custom, vanilla, reload, skip | **verified** |
| Paper 1.10.2 | pass: default, custom, vanilla, reload, skip | **verified** |
| Paper 1.11.2 | pass: default, custom, vanilla, reload, skip | **verified** |
| Paper 1.12 | pass: default, custom, vanilla, reload, skip | **verified** |
| Paper 1.12.2 | pass: default, custom, vanilla, reload, skip | **verified** |
| Spigot (any) | not run | **not claimed** — no Spigot jars were available; Paper passes are not reported as Spigot passes |

Verified here: 1.8.8, 1.9.4, 1.10.2, 1.11.2, 1.12, 1.12.2

Spigot itself is **not claimed**: only Paper jars were available for the runtime sweep, and a Paper
pass is not a Spigot pass even though both expose the same Bukkit API and CraftBukkit package layout.

## Proxies

| Platform | Artifact | Status |
| --- | --- | --- |
| BungeeCord | `rottenleather-bungee-1.0.1.jar` | loads and enables (probe log) |
| Waterfall | same jar as BungeeCord | same BungeeCord plugin API; not separately booted |
| Velocity | `rottenleather-velocity-1.0.1.jar` | loads and enables (probe log) |

Proxies have no world: the modules report the recipe fingerprint between server and proxy and warn on
a mismatch. They never implement furnace behaviour. The reporting path needs a connected player and is
not covered by the automated probe. Details: [`PROXIES.md`](PROXIES.md).

## Geyser and Floodgate

| Platform | Artifact | Reason |
| --- | --- | --- |
| Geyser | none | Bedrock clients open the Java server's furnace through Geyser's container translation, so the Java recipe applies unchanged. This is a documented rationale, not a tested Bedrock session. |
| Floodgate | none | Floodgate maps identities, not recipes or containers; there is nothing to hook. |

## Fabric

| Platform | Artifact | Reason |
| --- | --- | --- |
| Fabric | none | Fabric's first releases target 1.14; no Fabric loader exists for 1.8–1.12.2. Documented rationale, not a tested artifact. |

## Feature availability by version

| Feature | 1.8.x | 1.9–1.12.2 |
| --- | --- | --- |
| recipe, fuel, XP, hopper automation | yes (probed) | yes (probed) |
| configurable output count | yes (probed) | yes (probed) |
| configurable XP | yes (probed) | yes (probed) |
| configurable cook time | **no on Forge 1.8.x–1.10 and on Bukkit 1.8.x–1.9.x** — the furnace compares against a hard-coded 200 ticks and exposes no usable total field; the mod logs one warning and uses vanilla timing, which the `custom` scenario asserts | yes on Forge 1.10.2–1.12.2 and on Paper 1.10.2–1.12.2, per furnace, restored when the input changes (probed) |
| `conflictPolicy=skip` | yes (probed) | yes (probed) |
| live reload | Forge: re-registers the recipe, so input, output, count, XP and cook time all change (probed). Bukkit: applies XP and cook time only — the 1.8–1.12.2 API cannot re-register a recipe, so a count change needs a restart (probed) | same split: Forge 1.9–1.12.2 reload everything, Bukkit reloads XP and cook time |
| `conflictPolicy=replace` | **no on Forge 1.8.x** — a warning is logged and the existing recipe is kept (asserted by the `replace` scenario) | Forge: yes (probed). Bukkit/Paper: no — the 1.8–1.12.2 API cannot remove a recipe, and the plugin logs that |
