#!/bin/bash
# Antrian brief @coder — jalanin satu per satu sampai kelar, anti raket.
# Dipantau Megatron; log: ~/Code/hermes-mobile/supervise.log
set -u
cd ~/Code/hermes-mobile

BRIEFS=(/tmp/brief-coder-m31.md /tmp/brief-coder-m32.md /tmp/brief-coder-m33.md /tmp/brief-coder-m4.md)

for B in "${BRIEFS[@]}"; do
  [ -f "$B" ] || continue
  echo "[queue] === $(basename "$B") ==="
  # bot-run sudah idempotent-ish per brief; jalankan dan tunggu
  ~/.hermes/bin/bot-run coder "$B"
  # beri jeda kecil biar state.db settle
  sleep 5
done
echo "[queue] SEMUA BRIEF SELESAI"
