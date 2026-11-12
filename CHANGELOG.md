# Changelog

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
