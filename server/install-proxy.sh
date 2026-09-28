#!/bin/bash
# M6: pasang proxy multi-surface (port 8790) sebagai LaunchAgent + arahkan
# tailscale serve ke proxy. Idempotent. Jalankan: bash server/install-proxy.sh
set -euo pipefail

LABEL="com.hermes.desktop-gateway-proxy"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
PROXY="$HOME/Code/hermes-mobile/server/desktop_gateway_proxy.py"
TS=/Applications/Tailscale.app/Contents/MacOS/Tailscale
PORT=8790

echo "==> Tulis LaunchAgent $LABEL (port $PORT)"
mkdir -p "$HOME/Library/LaunchAgents"
cat > "$PLIST" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>$HOME/.hermes/hermes-agent/venv/bin/python</string>
    <string>$PROXY</string>
  </array>
  <key>EnvironmentVariables</key>
  <dict>
    <key>HERMES_PROXY_PORT</key><string>$PORT</string>
  </dict>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key>
  <dict><key>SuccessfulExit</key><false/></dict>
  <key>StandardOutPath</key><string>$HOME/.hermes/logs/desktop-gateway-proxy.log</string>
  <key>StandardErrorPath</key><string>$HOME/.hermes/logs/desktop-gateway-proxy.log</string>
</dict>
</plist>
EOF

echo "==> Load agent"
launchctl bootout "gui/$(id -u)" "$PLIST" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST"
sleep 2

echo "==> Health check proxy"
for i in 1 2 3 4 5; do
  if curl -sf -m 3 "http://127.0.0.1:$PORT/api/desktop-port" >/dev/null 2>&1; then
    echo "OK: discovery endpoint hidup:"; curl -s "http://127.0.0.1:$PORT/api/desktop-port"; echo
    break
  fi
  sleep 2
done
curl -sf -m 3 "http://127.0.0.1:$PORT/api/desktop-port" || {
  echo "GAGAL: proxy tidak naik — cek ~/.hermes/logs/desktop-gateway-proxy.log"; exit 1; }

echo "==> Tailscale serve → proxy $PORT"
if [ -x "$TS" ]; then
  STATE=$("$TS" status --json 2>/dev/null | python3 -c 'import json,sys; print(json.load(sys.stdin).get("BackendState",""))' || echo Unknown)
  if [ "$STATE" != "Running" ]; then
    echo "Tailscale belum login (BackendState=$STATE) — login dulu, lalu: $TS serve --bg $PORT"; exit 1
  fi
  if "$TS" serve status 2>/dev/null | grep -q ":$PORT"; then
    echo "serve $PORT sudah aktif"
  else
    "$TS" serve --bg "$PORT" || { echo "Gagal tailscale serve — manual: $TS serve --bg $PORT"; exit 1; }
  fi
  "$TS" serve status
else
  echo "Tailscale CLI tidak ketemu — manual: tailscale serve --bg $PORT"
fi
