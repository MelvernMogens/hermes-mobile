#!/usr/bin/env python3
"""Monitor WS client di desktop gateway: hitung koneksi & request active_list dari log stdio.
Baca log stdout process desktop gateway via lsof pipe tidak mungkin — jadi pakai
pendekatan lain: connect WS SENDIRI sebagai client dan pantau... tidak bisa lihat client lain.

Alternatif: baca /tmp log process. Desktop gateway pid dari resolve-gateway.sh.
Kita trace tcp: koneksi ESTABLISHED ke port desktop = jumlah WS client.
"""
import subprocess, json, sys

out = subprocess.run(["bash", "/Users/melvernmogens/Code/hermes-mobile/server/resolve-gateway.sh"],
                     capture_output=True, text=True).stdout.strip()
info = json.loads(out)
port, pid = info["port"], info["pid"]
print(f"desktop gateway pid={pid} port={port}")
conns = subprocess.run(["lsof", "-nP", "-a", "-p", str(pid), "-iTCP", "-sTCP:ESTABLISHED"],
                       capture_output=True, text=True).stdout
lines = [l for l in conns.splitlines()[1:] if f":{port}" in l]
print(f"koneksi ESTABLISHED ke gateway: {len(lines)}")
for l in lines:
    print(" ", l.split()[8] if len(l.split()) > 8 else l)
