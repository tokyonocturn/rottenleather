# Installation

## Forge (1.8–1.12.2)

1. Install the Forge version that matches your Minecraft release.
2. Put `rottenleather-<mcversion>-1.0.1.jar` into `.minecraft/mods/` (client) or the server's `mods/`.
3. Start the game or server once. `config/rottenleather.cfg` is created on first launch.

The jar is a normal Forge mod: it works in single player, on a LAN world and on a dedicated server,
and clients do not need it to join a server that has it (it adds no blocks or items).

Default config:

```hocon
recipe {
    S:inputItem=minecraft:rotten_flesh
    S:outputItem=minecraft:leather
    I:outputCount=1
    F:experience=0.1
    I:smeltTimeTicks=200
    S:conflictPolicy=skip
}
furnace {
    B:adjustCookTime=true
}
logging {
    B:logRegistration=true
}
```

Apply changes with `/rottenleather reload` (no restart). Changing the recipe shape also changes the
network fingerprint reported by `/rottenleather status`.

On Minecraft 1.8.x, 1.9.x and Forge 1.10 the furnace only knows a 200-tick cook time, so
`smeltTimeTicks` is ignored there; the mod logs one warning instead of silently pretending.
Forge 1.10.2–1.12.2 applies the configured value per furnace, and `/rottenleather status` reports
`customCookTime` for the running server so you can see what it actually did.

### Upgrading Minecraft versions

Use the jar built for that exact version. Every supported release has its own module because Forge
changed its item registry, `ItemStack` count API and chat component between 1.8 and 1.12.2.

## Bukkit / Spigot / Paper (1.8.8–1.12.2)

1. Put `rottenleather-bukkit-1.0.1.jar` into `plugins/`.
2. Start the server. `plugins/RottenFleshToLeather/config.yml` is created on first enable.
3. Check the result with `/rottenleather status` (alias `/rfl`); `/rottenleather reload` re-reads
   the config.

```yaml
recipe:
  input-item: minecraft:rotten_flesh
  output-item: minecraft:leather
  output-count: 1
  experience: 0.1
  smelt-time-ticks: 200
  conflict-policy: skip

logging:
  log-registration: true
```

Notes specific to the Bukkit family:

* One jar serves every listed 1.8–1.12.2 server. The plugin looks up the CraftBukkit package for the
  running server, so Spigot, Paper and forks that keep the CraftBukkit layout are covered.
* `conflict-policy: replace` requires removing an existing furnace recipe, which the 1.8–1.12.2
  Bukkit API does not offer. The plugin logs that replace is unavailable and keeps the existing
  recipe; use `skip`, or change the input item.
* Changing the recipe *shape* (input/output/count) needs a server restart on these versions. XP and
  cook time changes apply on `/rottenleather reload`.
* Bukkit 1.8.x and 1.9.x servers compare furnace progress against a hard-coded 200 ticks, so
  `smelt-time-ticks` is ignored there (the plugin logs one warning); 1.10.2 and newer apply it.
* The command needs `rottenleather.admin` (default op).

## BungeeCord / Waterfall

Put `rottenleather-bungee-1.0.1.jar` into the proxy's `plugins/` directory. The module has no
configuration file. It registers the `rottenleather:hello` channel and the `/rflnet` command.

A proxy does not tick worlds and cannot smelt, so this module never fakes furnace behaviour. It
collects the recipe fingerprint each backend announces, warns when two backends disagree, and lists
the reports in `/rflnet`. Install the Bukkit plugin on backends to get reports; see
[`PROXIES.md`](PROXIES.md) for the exact scope.

## Velocity

Put `rottenleather-velocity-1.0.1.jar` into Velocity's `plugins/` directory. Velocity 3.x on Java 11
or newer is required (the module is compiled for Java 11). It registers the same channel and
`/rflnet` command.

## Geyser / Floodgate

There is no Geyser or Floodgate jar to install. Bedrock players connected through Geyser use the
Java server's furnace: Geyser translates the Java furnace container, so the recipe works for them
exactly as it does for Java players, including fuel, XP and hoppers. Adding a second, Bedrock-only
recipe would create two sources of truth. See [`PROXIES.md`](PROXIES.md) for the reasoning.

## Fabric

There is no Fabric jar for 1.8–1.12.2 because Fabric did not exist for those versions (its first
public releases target 1.14). Building one would mean shipping an artifact no 1.8–1.12.2 client or
server can load, so the project documents the gap instead.

## Uninstalling

Remove the jar and restart. Forge leaves `config/rottenleather.cfg`; Bukkit leaves
`plugins/RottenFleshToLeather/`. Neither affects the world: no blocks, items or tile entities are
added, and the recipe disappears with the mod.
