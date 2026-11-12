#!/usr/bin/env python3
"""Minimal Minecraft RCON client: rcon.py <port> <password> <command> [command...]"""
import socket
import struct
import sys
import time


class Rcon:
    def __init__(self, host, port, password, timeout=15):
        self.sock = socket.create_connection((host, port), timeout)
        self.sock.settimeout(timeout)
        self._send(3, password)
        self._recv()

    def _read(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise IOError("rcon connection closed")
            buf += chunk
        return buf

    def _send(self, kind, body):
        data = struct.pack("<ii", 1, kind) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(data)) + data)

    def _recv(self):
        (length,) = struct.unpack("<i", self._read(4))
        data = self._read(length)
        return data[8:-2]

    def cmd(self, command):
        self._send(2, command)
        return self._recv().decode("utf-8", "replace").strip()


def connect(port, password, attempts=3):
    """Connect quickly; probes call this for every observation, so a dead server must not stall."""
    last = None
    for _ in range(attempts):
        try:
            return Rcon("127.0.0.1", port, password, timeout=10)
        except Exception as exc:  # server still starting
            last = exc
            time.sleep(0.3)
    raise SystemExit("rcon connect failed: %s" % last)


def main():
    port = int(sys.argv[1])
    password = sys.argv[2]
    rcon = connect(port, password)
    for command in sys.argv[3:]:
        print(">> %s" % command)
        try:
            print(rcon.cmd(command))
        except Exception as exc:
            print("!! %s" % exc)


if __name__ == "__main__":
    main()
