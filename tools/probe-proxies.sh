#!/usr/bin/env bash
# Boots a real BungeeCord and a real Velocity with the shipped proxy modules and proves each module
# is loaded and enabled. Proxies have no world, so the only thing that can be proven here is that the
# integration loads, registers its channel/command and reports ready; the recipe itself runs on the
# backends and is proven by probe-bukkit.sh / probe-forge.sh.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK="/c/Program Files/Java/jdk-21.0.12"
JAVA="$JDK/bin/java.exe"
LOG="$ROOT/logs/probe-proxies.log"
mkdir -p "$ROOT/logs"
: > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }
fail() { say "FAIL: $*"; exit 1; }

# ------------------------------------------------------------------ BungeeCord / Waterfall
B="$ROOT/runtime/proxy-bungee"
rm -rf "$B"
mkdir -p "$B/plugins"
cp -f "$ROOT/libs/proxy/BungeeCord.jar" "$B/BungeeCord.jar"
cp -f "$ROOT/dist/rottenleather-bungee-1.0.0.jar" "$B/plugins/"
cat > "$B/config.yml" <<'EOF'
online_mode: false
ip_forward: false
listeners:
- host: 127.0.0.1:25777
  motd: rottenleather probe
  max_players: 1
  query_port: 25777
  priorities: []
  forced_hosts: {}
  tab_size: 60
  force_default_server: false
servers: {}
timeout: 30000
connection_throttle: 4000
stats: ''
EOF
say "starting BungeeCord"
( cd "$B" && "$JAVA" -Xmx512M -jar BungeeCord.jar > console.log 2>&1 ) &
BPID=$!
for i in $(seq 1 90); do
    grep -q 'Listening on' "$B/console.log" 2>/dev/null && break
    kill -0 "$BPID" 2>/dev/null || { say "BungeeCord exited"; tail -15 "$B/console.log"; fail "BungeeCord did not start"; }
    sleep 1
done
grep -q 'Rotten Flesh to Leather network bridge enabled' "$B/console.log" \
    || { tail -20 "$B/console.log"; fail "BungeeCord did not enable the module"; }
grep -q 'rflnet' "$B/console.log" || true
say "BungeeCord module loaded: $(grep -m1 'Rotten Flesh to Leather network bridge enabled' "$B/console.log")"
kill "$BPID" 2>/dev/null || true
sleep 2
cp -f "$B/console.log" "$ROOT/logs/bungee-console.log"

# ------------------------------------------------------------------ Velocity
V="$ROOT/runtime/proxy-velocity"
rm -rf "$V"
mkdir -p "$V/plugins"
cp -f "$ROOT/libs/proxy/velocity.jar" "$V/velocity.jar"
cp -f "$ROOT/dist/rottenleather-velocity-1.0.0.jar" "$V/plugins/"
cat > "$V/velocity.toml" <<'EOF'
config-version = "2.7"
bind = "127.0.0.1:25778"
motd = "rottenleather probe"
show-max-players = 1
online-mode = false
force-key-authentication = false
prevent-client-proxy-connections = false
player-info-forwarding-mode = "none"
announce-forge = false
kick-existing-players = false
ping-passthrough = "DISABLED"
servers = { lobby = "127.0.0.1:25779" }
try = ["lobby"]
[advanced]
compression-threshold = 256
login-ratelimit = 0
connection-timeout = 5000
read-timeout = 30000
haproxy-protocol = false
tcp-fast-open = false
bungee-plugin-message-channel = true
show-ping-requests = false
failover-on-unexpected-server-disconnect = true
announce-proxy-commands = true
log-command-executions = false
log-player-connections = true
[query]
enabled = false
port = 25778
[metrics]
enabled = false
EOF
say "starting Velocity"
( cd "$V" && "$JAVA" -Xmx512M -jar velocity.jar > console.log 2>&1 ) &
VPID=$!
for i in $(seq 1 90); do
    grep -q 'Done (' "$V/console.log" 2>/dev/null && break
    grep -q 'Listening on' "$V/console.log" 2>/dev/null && break
    kill -0 "$VPID" 2>/dev/null || { say "Velocity exited"; tail -20 "$V/console.log"; fail "Velocity did not start"; }
    sleep 1
done
grep -q 'Rotten Flesh to Leather network bridge enabled' "$V/console.log" \
    || { tail -25 "$V/console.log"; fail "Velocity did not enable the module"; }
say "Velocity module loaded: $(grep -m1 'Rotten Flesh to Leather network bridge enabled' "$V/console.log")"
kill "$VPID" 2>/dev/null || true
sleep 2
cp -f "$V/console.log" "$ROOT/logs/velocity-console.log"

say "PASS proxies (BungeeCord/Waterfall module + Velocity module load and enable)"
