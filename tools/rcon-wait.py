#!/usr/bin/env python3
"""Poll one RCON command on a single connection until a condition holds.

    rcon-wait.py <port> <password> <timeout-seconds> <contains|absent> <needle> <command>

Exit status 0 when the condition was met, 1 on timeout, 2 when the connection failed. Using one
connection and 250 ms polling keeps observation loopback-cheap and deterministic: the probe reacts to
server state instead of guessing how long a smelt should take.
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rcon import Rcon  # noqa: E402


def main():
    port = int(sys.argv[1])
    password = sys.argv[2]
    timeout = float(sys.argv[3])
    mode = sys.argv[4]
    needle = sys.argv[5]
    command = " ".join(sys.argv[6:])
    try:
        rcon = Rcon("127.0.0.1", port, password, timeout=10)
    except Exception as exc:
        print("CONNECT-FAILED %s" % exc)
        return 2
    deadline = time.time() + timeout
    last = ""
    while True:
        try:
            last = rcon.cmd(command)
        except Exception as exc:
            print("COMMAND-FAILED %s" % exc)
            return 2
        matched = needle in last
        if (mode == "contains" and matched) or (mode == "absent" and not matched):
            print("OK")
            return 0
        if time.time() >= deadline:
            print("TIMEOUT %s" % last)
            return 1
        time.sleep(0.25)


if __name__ == "__main__":
    sys.exit(main())
