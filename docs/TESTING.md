# Testing

Every supported version is built and then exercised on a real server. Nothing is marked supported
because it compiled.

## 1. Shared core suite (no Minecraft)

```bash
bash tools/test-core.sh
```

Compiles `shared/` with JDK 8 and runs `CoreSuite`: 77 checks over spec validation (bad ids, counts,
XP, cook times), conflict decisions (`skip`, `replace`), the cook-time rule (vanilla, faster, slower,
mid-cook changes, never stalling) and tracker bookkeeping (restore, foreign-value protection,
release).

## 2. Forge per-version contract suite

`bash tools/build-forge.sh <mc>` runs `CoreSuite` again plus `ForgeSuite` (24 checks) compiled
against that exact Minecraft/Forge version. It verifies the generated adapter compiles against the
real API surface of that release (item registry, stack count, chat component, furnace field API) and
that defaults come from the spec.

## 3. Forge runtime probe

```bash
bash tools/probe-forge.sh 1.12.2 1.12.2-14.23.5.2847 25571 25581
```

Boots a real Forge dedicated server from the built jar and asserts, through RCON:

1. **Default recipe + hopper automation** — chest → hopper → furnace → hopper → chest produces
   exactly 1 leather; `/rottenleather status` reports `registered=true`, `runtimeXp=0.1` and the spec
   fingerprint.
2. **Custom shape** — `outputCount=3`, `experience=0.35`, `smeltTimeTicks=60`: nothing reaches the
   output chest after 2 s, 3 leather arrive by 5 s.
3. **Timing isolation** — a beef furnace in the same world has not finished at 5 s and has finished
   by 14 s, i.e. other recipes keep vanilla 200-tick timing while this recipe uses 60.
4. **`conflictPolicy=skip`** — with `inputItem=porkchop`, the vanilla porkchop→cooked_porkchop recipe
   survives and `/rottenleather reload` reports `install=KEEP_EXISTING`.
5. **`conflictPolicy=replace`** — the same input now produces leather, proving the vanilla entry was
   displaced.
6. **Restore** — switching back to `skip` and reloading makes porkchop→cooked_porkchop work again.
7. **Log hygiene** — no reflection/classloading errors, no stack trace from this mod, and no
   “cook-time adjustment failed” warning.

Output is read from chests with `/testforblock`, because `/blockdata` cannot read NBT before 1.13.
Each furnace feeds a hopper into a chest that contains only the expected stack, so the assertion is
exact.

## 4. Bukkit runtime probe

```bash
bash tools/probe-bukkit.sh 1.12.2 25671 25681
```

Boots a real Paper server (1.8.8–1.12.2) with the built plugin and asserts:

1. default recipe, `nmsXp=0.1`, fingerprint, and hopper automation;
2. `outputCount=3`, `experience=0.35`, `smeltTimeTicks=60`: 3 leather by 5 s, not present at 2 s;
3. a beef furnace keeps vanilla timing (empty at 5 s, done by 14 s);
4. live `/rottenleather reload` applies `experience=0.25`, `smelt-time-ticks=80` to new smelts;
5. `input-item=porkchop` with `skip` preserves the vanilla porkchop recipe;
6. the NMS bridges report available (no “cook-time API not available” / “recipe experience API not
   available”), and no stack trace from the plugin appears.

`conflict-policy: replace` is not tested because the 1.8–1.12.2 Bukkit API cannot remove a recipe;
the plugin reports that limitation instead.

## 5. Proxy load test

```bash
bash tools/probe-proxies.sh
```

Starts the real BungeeCord jar and the real Velocity jar with the shipped modules, waits for the
proxy to finish booting, and asserts each module logs its enable line. Proxies have no world, so this
proves loading, channel and command registration — not smelting.

## 6. What is not covered

* No automated GUI/client test: the probes are headless servers, so the visual furnace progress
  bar and the player-facing XP orb amount are not asserted.
* No Bedrock client test: Geyser translation is documented, not exercised.
* Proxy reporting end-to-end needs a connected player; only module loading is automated.
* Versions without a probe run are not listed as supported in
  [`COMPATIBILITY.md`](COMPATIBILITY.md), even if they build.

## Evidence

Probe output is written to `logs/`:

* `logs/probe-forge-<mc>.log` and `logs/probe-forge-<mc>/` (status lines, failing checks, console)
* `logs/probe-bukkit-<mc>.log` and `logs/probe-bukkit-<mc>/`
* `logs/probe-proxies.log`, `logs/bungee-console.log`, `logs/velocity-console.log`
