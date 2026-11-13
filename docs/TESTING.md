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

## 3. Forge runtime probe (one scenario per fresh server)

```bash
bash tools/probe-forge.sh 1.12.2 1.12.2-14.23.5.2847 default
bash tools/probe-forge.sh 1.12.2 1.12.2-14.23.5.2847 custom
bash tools/probe-forge.sh 1.12.2 1.12.2-14.23.5.2847 vanilla
bash tools/probe-forge.sh 1.12.2 1.12.2-14.23.5.2847 reload
bash tools/probe-forge.sh 1.12.2 1.12.2-14.23.5.2847 skip
bash tools/probe-forge.sh 1.12.2 1.12.2-14.23.5.2847 replace
```

Each command boots the built jar on a **fresh JVM, fresh world and fresh config**, runs exactly one
scenario, and exits. Nothing is shared between scenarios, so a passing run proves that scenario on
its own and cannot be explained by state left behind by an earlier one.

What every run does:

* picks two free ports by binding them, writes `server.properties` with them, and refuses to continue
  unless the booted server's own console contains `RCON running on ...:<rcon port>` — a stale server
  on a fixed port used to answer RCON and made the probe test the wrong instance;
* deletes `world/`, `world_nether/`, `world_the_end/` and aborts if a stale server still holds them;
* copies the built jar into `mods/` and verifies the copy byte-for-byte (`cmp`) so a locked, older
  jar can never be silently tested instead of the new build;
* reads `/rottenleather status` and checks that the running recipe's **fingerprint equals the
  fingerprint computed from the config file the probe just wrote** (for `default` it also equals the
  generated `BuildDefaults.SPEC_FINGERPRINT`, which catches stale code generation);
* moves the world spawn onto the probe area (`/setworldspawn 2 4 2`) so the server keeps those chunks
  loaded while no player is online;
* builds `chest → hopper → furnace (1 coal) → hopper → chest` and waits on observable state only:
  the furnace block becomes `lit_furnace`, the output chest matches the expected stack with
  `/testforblock`, and the coal, input stack and output stack disappear from their slots.

Cook time is measured in **server ticks** with `/time query gametime` between "furnace is lit" and
"output reached the chest", so machine load cannot turn a correct smelt into a failure:

| Scenario | Expected |
| --- | --- |
| `default` | 1 leather, 150–420 ticks (vanilla 200; the window starts at 150 ticks to absorb the state-poll latency of the first lit-furnace observation), `registered=true`, `runtimeXp=0.1` |
| `custom` | 3 leather, 30–150 ticks when `customCookTime=true`, `runtimeXp=0.35`; where the version cannot adjust cook time, the console must contain the documented `fixed at 200 ticks` warning and the smelt must take 150–420 ticks |
| `vanilla` | with `outputCount=3, ticks=60` configured, beef → 1 cooked beef in 150–420 ticks and the chest must not contain leather, proving other recipes keep vanilla timing |
| `reload` | boot at defaults, rewrite the config and `/rottenleather reload`: the status fingerprint, `ticks=80`, `runtimeXp=0.35` and `registered=true` must all update, then the smelt must use the reloaded timing. Forge reloads to 3/0.35/80 and smelts 3 leather; Bukkit reloads to 1/0.35/80 (the 1.8-1.12.2 API cannot re-register a recipe, so a count change needs a restart) and smelts 1 leather |
| `skip` | beef → leather with `skip`: `install=KEEP_EXISTING`, `registered=false`, the furnace produces vanilla cooked beef and the chest never contains leather |
| `replace` | beef → leather with `replace`: on 1.9+ `registered=true` and the furnace produces leather; on 1.8.x the console must contain `replace is not available on MC` and vanilla cooked beef must still be produced |

A run fails on any mod stack trace, reflection/classloading error (`NoSuchField`, `NoClassDefFound`,
…), or `cook-time adjustment failed` warning in the server console.

## 4. Bukkit / Paper runtime probe

```bash
bash tools/probe-bukkit.sh 1.12.2 default
bash tools/probe-bukkit.sh 1.12.2 custom
bash tools/probe-bukkit.sh 1.12.2 vanilla
bash tools/probe-bukkit.sh 1.12.2 reload
bash tools/probe-bukkit.sh 1.12.2 skip
```

Same design and assertions as the Forge probe, with the platform's own records:
`registered=`, `nmsXp=`, `customCookTime=`, `install=` and the same fingerprint check. The plugin is
installed into `plugins/` with the same byte-for-byte verification. `replace` is not probed because
the 1.8–1.12.2 Bukkit API cannot remove a recipe; the plugin logs that limitation instead of
pretending to support it.

## 5. Spigot runtime probe

Spigot has no downloadable server jar: SpigotMC only distributes BuildTools, which compiles the
server from the SpigotMC repositories. `tools/build-spigot.sh` drives it with the JDK 8 toolchain and
writes `runtime/spigot/spigot-<mc>.jar`; `tools/probe-spigot.sh` then runs the same five scenarios as
the Paper probe against that jar.

```bash
bash tools/build-spigot.sh 1.8.8 1.9 1.12 1.12.2
bash tools/probe-spigot.sh 1.8.8 default
```

Before any scenario assertion the probe asks the server `/version` and requires the reply to say
"This server is running CraftBukkit version ... Spigot ... (MC: <mc>)". A Paper or API-only jar can
therefore never be recorded as a Spigot pass; the raw reply is stored as
`logs/probe-spigot-<mc>/version-<scenario>.txt`.

Spigot versions that could not be built here, and why:

| Version | Blocking reason |
| --- | --- |
| 1.8.9 | BuildTools has no version manifest for 1.8.9 (`https://hub.spigotmc.org/versions/1.8.9.json` answers 404), so SpigotMC never published a buildable 1.8.9 revision. |
| 1.9.4, 1.10.2, 1.11.2, 1.12.1 | Those revisions declare `net.md-5:bungeecord-chat:<version>-SNAPSHOT`; the artifact is gone from Sonatype and is not served by SpigotMC's or PaperMC's public Maven groups either (HTTP 404), so `spigot-api` cannot resolve and no genuine jar can be produced. |
| 1.10, 1.11 | The dependency artifacts exist and BuildTools reaches the patching stage, but on this Windows host BuildTools exports an empty `GIT_COMMITTER_NAME`/`EMAIL` to `applyPatches.sh`, so `git am` aborts with `empty ident name ... not allowed`. Not a plugin defect; no server jar was produced, so those versions stay unverified. |

## 6. Whole matrix

```bash
bash tools/run-matrix.sh forge     # 11 versions x 6 scenarios
bash tools/run-matrix.sh bukkit    # 6 Paper versions x 5 scenarios
bash tools/run-matrix.sh spigot    # every Spigot jar that exists in runtime/spigot
bash tools/run-matrix.sh all
```

Runs sequentially, one fresh server per scenario, and appends
`<platform> <mc> <scenario> PASS|FAIL` to `logs/MATRIX.txt`. A failure does not stop the sweep, so the
matrix records every result. `tools/matrix.py` then regenerates
[`COMPATIBILITY.md`](COMPATIBILITY.md) from those logs.

## 6. Proxy load test

```bash
bash tools/probe-proxies.sh
```

Starts the real BungeeCord jar and the real Velocity jar with the shipped modules, waits for the
proxy to finish booting, and asserts each module logs its enable line. Proxies have no world, so this
proves loading, channel and command registration — not smelting.

## 7. What is not covered

* No automated client test: the servers are headless, so the visual furnace progress bar and the
  exact XP orb amount a player picks up are not asserted. XP is checked through the platform's own
  smelting record, which is what both Forge and CraftBukkit read when a player takes the output.
* No Bedrock client test: Geyser translation is documented, not exercised.
* Proxy reporting end-to-end needs a connected player; only module loading is automated.
* Spigot is not claimed. Only Paper jars were available on the build machine; Paper is a Spigot fork
  using the same Bukkit API and CraftBukkit package layout, but a Paper pass is not reported as a
  Spigot pass.
* Versions without a passing run are not listed as supported in
  [`COMPATIBILITY.md`](COMPATIBILITY.md), even if they build.

## Evidence

Every probe writes its own log, and the matrix is derived from those files, not from memory:

* `logs/probe-forge-<mc>-<scenario>.log` — one file per Forge version and scenario
* `logs/probe-bukkit-<mc>-<scenario>.log` — one file per Paper version and scenario
* `logs/probe-forge-<mc>/status-<scenario>.txt`, `reload-<scenario>.txt` — raw command responses
* `logs/MATRIX.txt` — the PASS/FAIL summary used to generate the compatibility matrix
* `logs/build-forge-matrix.log` — per-version `CORE_SUITE` and `FORGE_SUITE` results
* `logs/probe-proxies.log`, `logs/bungee-console.log`, `logs/velocity-console.log`
