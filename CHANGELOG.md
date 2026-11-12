# Changelog

## 1.0.1

Verification and correctness release. Every supported cell in
[`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md) now points at its own runtime log produced on a
fresh server boot.

* Forge: smelting experience is kept as exactly one entry per result item. Forge looks experience up
  by item and metadata, so re-registering the same output with a different `outputCount` used to
  leave two entries and the XP a player received depended on map iteration order. The value is now
  canonicalised and read back from Forge's own map after registration.
* Forge: registration is verified after the fact. If the server refuses the recipe, the mod reports
  `install=registration-refused` and logs the installed result instead of claiming success.
* Forge: `conflictPolicy=replace` now really displaces vanilla recipes. Vanilla registers its
  furnace inputs with metadata `32767` (“any variant”), so strict metadata matching never found the
  entry to remove; removal now uses Forge's own wildcard rule before re-registering.
* Bukkit: the plugin removes any earlier experience entry for the same result item before storing the
  configured value, so a reload that changes `output-count` cannot leave a stale XP value behind.
* Cook-time capability now matches what each platform can actually do. Minecraft 1.8.x/1.9.x
  furnaces (Forge and Bukkit) and the Forge 1.10 furnace compare progress against a hard-coded 200
  ticks, so `smelt-time-ticks` is reported as unsupported there, one documented warning is logged at
  startup and on reload, and vanilla timing is used instead of silently ignoring the setting.
* Bukkit: the furnace-experience map is now located by its value type and proven with a set/get round
  trip before the plugin relies on it, which restores working experience on the 1.8 and 1.9 NMS
  packages where the member name differs. A bridge that cannot store experience reports itself as
  unavailable instead of writing the wrong field.
* Forge: the cook-time adjuster iterates a snapshot of the loaded tile entities, so a world tick that
  adds or removes a furnace can no longer abort a pass with a concurrent-modification warning.
* Bukkit reload applies the reloaded experience and cook time and never claims an output-count change
  it cannot make: the 1.8-1.12.2 API has no way to remove a registered furnace recipe, so a new count
  needs a restart, which the plugin logs and `docs/INSTALL.md` states.

* Probes were rebuilt around observable state and one fresh server per scenario: unique free ports
  with an RCON bind check, byte-for-byte verification of the jar that is actually loaded, world
  deletion that fails loudly, spawn-chunk anchoring, `/testforblock` state waits instead of fixed
  sleeps, and smelt time measured in server ticks.
* `docs/TESTING.md` documents the per-scenario design and the exact commands; `tools/run-matrix.sh`
  records `logs/MATRIX.txt`, and `tools/matrix.py` generates the compatibility matrix from it.

## 1.0.0

First release.

* Smelting 1 rotten flesh into 1 leather in a normal furnace, with vanilla fuel, experience and
  hopper automation.
* Forge modules for 1.8.8, 1.8.9, 1.9, 1.9.4, 1.10, 1.10.2, 1.11, 1.11.2, 1.12, 1.12.1 and 1.12.2.
* One Bukkit/Spigot/Paper plugin for 1.8.8–1.12.2 using the Bukkit recipe API plus an NMS bridge for
  experience and cook time.
* BungeeCord/Waterfall and Velocity modules that collect backend recipe fingerprints and warn when a
  network serves different recipes. They do not fake furnace functionality.
* Configurable input, output, output count, experience, cook time and conflict policy, defined once
  in `spec/recipe.json` and generated into every module.
* Non-vanilla cook times apply only to furnaces holding this recipe's input; other recipes keep
  vanilla timing, and the original timing is restored when the input changes.
* `/rottenleather status` and `/rottenleather reload` on Forge and Bukkit; `/rflnet` on proxies.
* Automated shared-core suite, per-version Forge contract suite, and headless runtime probes on real
  Forge and Paper servers.
