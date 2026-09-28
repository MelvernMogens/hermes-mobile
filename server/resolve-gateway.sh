#!/bin/bash
# M6: resolve gateway desktop aktif — READ-ONLY discovery.
# Baca ~/.hermes/runtime/active_sessions.json (surface=desktop) + lsof verify
# bahwa pid itu benar-benar LISTEN di 127.0.0.1. Output JSON satu baris:
#   {"port": 57840, "pid": 84269, "surface": "desktop"}
# Desktop mati → {"port": 0, "pid": 0, "surface": "none"}.
set -euo pipefail

REGISTRY="$HOME/.hermes/runtime/active_sessions.json"
PREF_SURFACES="${HERMES_GATEWAY_SURFACES:-desktop}"

python3 - "$REGISTRY" "$PREF_SURFACES" <<'PY'
import json, subprocess, sys

registry, pref_raw = sys.argv[1], sys.argv[2]
prefs = [s.strip() for s in pref_raw.split(",") if s.strip()] or ["desktop"]

def emit(port, pid, surface):
    print(json.dumps({"port": port, "pid": pid, "surface": surface}))
    sys.exit(0)

try:
    entries = json.load(open(registry))["entries"]
except Exception:
    emit(0, 0, "none")

for surface in prefs:
    for e in entries:
        if str(e.get("surface") or "") != surface or not e.get("pid"):
            continue
        pid = int(e["pid"])
        try:
            out = subprocess.run(
                ["/usr/sbin/lsof", "-nP", "-a", "-p", str(pid), "-iTCP", "-sTCP:LISTEN"],
                capture_output=True, text=True, timeout=3).stdout
        except Exception:
            continue
        for line in out.splitlines()[1:]:
            parts = line.split()
            if len(parts) >= 9 and "TCP" in parts[7] and "(LISTEN)" in parts:
                addr = parts[8]
                if addr.startswith("127.0.0.1:"):
                    emit(int(addr.rsplit(":", 1)[1]), pid, surface)

emit(0, 0, "none")
PY
