#!/bin/bash
# Setup backend mobile Hermes — serve di port fix 8788 + token fix + LaunchAgent.
# Idempotent; aman dijalankan ulang.
set -euo pipefail

PORT=8788
LABEL="com.hermes.mobile-serve"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
ENVFILE="$HOME/.hermes/mobile-serve.env"
VENV_BIN="$HOME/.hermes/hermes-agent/venv/bin"

echo "==> Generate token fix (sekali saja)"
if [ ! -f "$ENVFILE" ]; then
  umask 077
  TOKEN=$(python3 -c "import secrets; print(secrets.token_urlsafe(24))")
  {
    echo "# Token backend serve untuk Hermes Mobile (dipakai app Android)."
    echo "HERMES_DASHBOARD_SESSION_TOKEN=$TOKEN"
  } > "$ENVFILE"
  chmod 600 "$ENVFILE"
  echo "    token baru dibuat."
else
  echo "    token sudah ada (dipakai yang lama)."
fi

echo "==> Tulis LaunchAgent $LABEL"
mkdir -p "$HOME/Library/LaunchAgents"
cat > "$PLIST" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>$VENV_BIN/python</string>
    <string>-m</string><string>hermes_cli.main</string>
    <string>serve</string>
    <string>--host</string><string>127.0.0.1</string>
    <string>--port</string><string>$PORT</string>
  </array>
  <key>EnvironmentVariables</key>
  <dict>
    <key>HERMES_DASHBOARD_SESSION_TOKEN</key><string>$(grep -o 'HERMES_DASHBOARD_SESSION_TOKEN=.*' "$ENVFILE" | cut -d= -f2)</string>
  </dict>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key>
  <dict><key>SuccessfulExit</key><false/></dict>
  <key>StandardOutPath</key><string>$HOME/.hermes/logs/mobile-serve.log</string>
  <key>StandardErrorPath</key><string>$HOME/.hermes/logs/mobile-serve.log</string>
</dict>
</plist>
EOF

echo "==> Load agent"
launchctl bootout "gui/$(id -u)" "$PLIST" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST"
sleep 3

echo "==> Health check"
for i in 1 2 3 4 5; do
  if curl -sf -m 3 "http://127.0.0.1:$PORT/api/health" >/dev/null 2>&1; then
    echo "OK: http://127.0.0.1:$PORT/api/health merespons"
    break
  fi
  sleep 2
done
curl -sf -m 3 "http://127.0.0.1:$PORT/api/health" || { echo "GAGAL: backend tidak naik — cek log: tail -50 ~/.hermes/logs/mobile-serve.log"; exit 1; }

echo
echo "==> Tailscale serve"
TS=/Applications/Tailscale.app/Contents/MacOS/Tailscale
if [ -x "$TS" ]; then
  STATE=$("$TS" status --json 2>/dev/null | python3 -c "import json,sys; print(json.load(sys.stdin).get('BackendState',''))" || echo Unknown)
  if [ "$STATE" != "Running" ]; then
    echo "Tailscale belum login (BackendState=$STATE). Login dulu, lalu jalankan:"
    echo "  $TS serve --bg $PORT"
    exit 0
  fi
  if "$TS" serve status 2>/dev/null | grep -q ":$PORT"; then
    echo "serve $PORT sudah aktif"
  else
    "$TS" serve --bg "$PORT" || { echo "Gagal tailscale serve — jalankan manual: $TS serve --bg $PORT"; exit 1; }
  fi
  HOST=$("$TS" serve status 2>/dev/null | grep -o 'https://[a-z0-9.-]*' | head -1)
  echo
  echo "=================================================="
  echo " URL app : $HOST (port $PORT di-proxy otomatis)"
  echo " Token   : $(grep -o 'TOKEN=.*' "$ENVFILE" | cut -d= -f2)"
  echo "=================================================="
else
  echo "Tailscale CLI tidak ketemu — expose manual: tailscale serve --bg $PORT"
fi
