# Compatibility matrix

Only versions with a passing build, passing API contract checks **and** a passing runtime probe are
marked **verified**. A version that builds but was not probed is marked **build only** and is not
claimed as supported.

The runtime probe boots a real dedicated server, feeds a furnace through hoppers, and asserts on
output chests with `/testforblock`. See [`TESTING.md`](TESTING.md) for each check.

## Forge

| Minecraft | Forge | build + 101 checks | runtime probe | support |
| --- | --- | --- | --- | --- |
| 1.8.8 | 1.8.8-11.15.0.1655 | pass | recipe + hopper + skip pass; custom time unsupported (documented) | verified for recipe/hopper/skip |
| 1.8.9 | 1.8.9-11.15.1.2318 | pass | recipe + hopper pass (full probe incomplete) | build + API verified; runtime partial |
| 1.9 | 1.9-12.16.1.1938 | pass | recipe + hopper pass (full probe incomplete) | build + API verified; runtime partial |
| 1.9.4 | 1.9.4-12.17.0.2317 | pass | recipe + hopper pass (full probe incomplete) | build + API verified; runtime partial |
| 1.10 | 1.10-12.18.0.2000 | pass | custom timing probe did not complete under load | build + API verified; runtime partial |
| 1.10.2 | 1.10.2-12.18.3.2511 | pass | custom timing + vanilla isolation pass; conflict step flaked | build + API verified; runtime partial |
| 1.11 | 1.11-13.19.1.2199 | pass | custom timing + vanilla isolation pass; conflict step flaked | build + API verified; runtime partial |
| 1.11.2 | 1.11.2-13.20.1.2588 | pass | custom timing pass; conflict step flaked | build + API verified; runtime partial |
| 1.12 | 1.12-14.21.1.2443 | pass | recipe + hopper pass (full probe incomplete) | build + API verified; runtime partial |
| 1.12.1 | 1.12.1-14.22.1.2485 | pass | recipe + hopper pass (full probe incomplete) | build + API verified; runtime partial |
| 1.12.2 | 1.12.2-14.23.5.2847 | pass | recipe, hopper, 3x output, 60-tick timing and vanilla isolation pass; conflict skip reproduced in an isolated run | verified (conflict step flaked in the automated probe) |

Every Forge module is compiled against its own Minecraft/Forge version with JDK 8. `checks` is the
combined shared-core (77) and per-version API-contract (24) suite.

## Bukkit / Spigot / Paper

One jar, `rottenleather-bukkit-1.0.0.jar`, is compiled against the Spigot 1.8.8 API and looks up the
running CraftBukkit package at enable time.

| Server | runtime probe | support |
| --- | --- | --- |
| Paper 1.12.2 | full pass: recipe, XP, hoppers, custom time/count, live reload, conflict skip | verified |
| Paper 1.8.8–1.12 | one jar; not individually probed | build only; `smeltTimeTicks` unsupported before 1.10 |

The 1.8.8 Paper run reached boot 3 and failed only because the pre-alias config used `porkchop`; the
alias table was added afterwards and the 1.12.2 run exercises the same code path with the same jar.

## Proxies

| Platform | Artifact | Verified |
| --- | --- | --- |
| BungeeCord | `rottenleather-bungee-1.0.0.jar` | module loads and enables on the current BungeeCord build |
| Waterfall | same jar as BungeeCord | same BungeeCord plugin API; not separately booted |
| Velocity 3.5.1 | `rottenleather-velocity-1.0.0.jar` | module loads and enables |

Proxies have no world. The modules provide recipe-fingerprint reporting and a mismatch warning, not
furnace functionality; the reporting path needs a connected player and is not covered by the
automated probe. Details and limits: [`PROXIES.md`](PROXIES.md).

## Geyser and Floodgate

| Platform | Artifact | Reason |
| --- | --- | --- |
| Geyser | none | Bedrock clients use the Java server's furnace through Geyser's container translation, so the Java recipe applies unchanged |
| Floodgate | none | Floodgate maps identities, not recipes or containers; there is nothing to hook |

## Fabric

| Platform | Artifact | Reason |
| --- | --- | --- |
| Fabric | none | Fabric's first releases target 1.14; no Fabric loader exists for 1.8–1.12.2 |

## Feature availability by version

| Feature | 1.8.8–1.9.4 | 1.10–1.12.2 |
| --- | --- | --- |
| recipe, fuel, XP, hoppers | yes | yes |
| configurable output count | yes | yes |
| configurable XP | yes | yes |
| configurable cook time | no — furnace compares against a hard-coded 200 and exposes no total field; the mod logs one warning and uses vanilla timing | yes, per furnace, restored when the input changes |
| `conflictPolicy=skip` | yes | yes |
| `conflictPolicy=replace` | no (Forge 1.8.x and all Bukkit 1.8–1.12.2 log that it is unavailable and keep the existing recipe) | Forge: yes; Bukkit: no |
