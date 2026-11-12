# Proxies, Geyser, Floodgate and Fabric

## What a proxy can and cannot do

A BungeeCord, Waterfall or Velocity proxy has no worlds, no chunks and no tile entities. It cannot
smelt anything, and a module that claimed to add a “proxy furnace” would be a fake. The one real
problem a proxy can solve for this recipe is **network consistency**: with several backends it is
easy to update one server and forget another, so players see rotten flesh turn into leather on
`survival-1` and stay rotten on `survival-2`.

The proxy modules therefore implement a recipe-consistency bridge:

1. A backend with the Bukkit plugin announces its recipe fingerprint on
   `rottenleather:hello` when a player joins (`RFL1`, server name, fingerprint, summary, registered).
2. The proxy module registers that channel, accepts reports **only** from backend servers (a client
   cannot spoof one), and records the latest report per backend.
3. If two reporting backends have different fingerprints, the proxy logs a warning naming them.
4. `/rflnet` prints the collected reports for an operator.

The modules never modify gameplay, never cancel unrelated plugin messages, and never claim a furnace
exists on the proxy.

## Scope and limits (read this before relying on it)

* **Bukkit-family backends only.** The sender is implemented in the Bukkit plugin, because plugin
  messaging is the stable cross-platform channel on 1.8–1.12.2. The Forge mod does not send a report;
  on a Forge-backed network `/rflnet` will not show that server.
* **Load-tested, not end-to-end tested.** The automated suite boots real BungeeCord and Velocity and
  proves the module loads, registers its channel and command, and enables. Delivering a report
  requires a connected player, which the headless suite does not simulate. Treat the reporting path
  as implemented but not covered by automated tests.
* The bridge is informational. Mismatched backends still smelt with their own recipe; the fix is to
  copy the same jar and config everywhere (the fingerprint makes the difference visible).

## Geyser and Floodgate

No Geyser or Floodgate module is shipped, and none is needed:

* Geyser translates the Java server's furnace container and inventory for Bedrock clients. A recipe
  added to the Java furnace is therefore the recipe Bedrock players use — same fuel, same XP, same
  hoppers, same automation.
* Floodgate only maps Bedrock identities onto Java accounts; it has no recipe or container layer to
  hook.
* A separate Bedrock-only recipe would mean two recipes for one furnace and would desynchronise the
  moment the Java config changed, which is exactly the duplication this project avoids.

If you run Geyser in standalone mode (connecting Bedrock clients straight to a Java server without a
proxy), install the normal Forge or Bukkit jar on that Java server; Geyser needs nothing extra.

## Fabric

Fabric's first public releases target Minecraft 1.14. There is no Fabric loader, API or mapping set
for 1.8–1.12.2, so no Fabric jar is produced: it could not be loaded by any client or server in the
supported range. This is a limitation of the version range, not an omitted port. On 1.14+ the same
design (vanilla furnace recipe, optional per-furnace cook time) would port directly, but that is
outside this project's supported versions and is not claimed.
