# M6 progress — multi-surface (HP + desktop, satu gateway)

## Fakta infrastruktur (verified 28 Sep)
- Desktop gateway: pid 84269, 127.0.0.1:57840, `--profile default serve --port 0` (loopback, auth_required=false).
- Mobile-serve: pid 81980, 127.0.0.1:8788 (gated, basic auth + ws-ticket).
- Kedua process → HERMES_HOME ~/.hermes (default profile) → registry sama: ~/.hermes/runtime/active_sessions.json.
- Lease session 20260926_141522_eefc53: pid 84269, surface=desktop, live_session_id=40325aa0.

## Auth backend desktop (loopback mode)
- HTTP /api/*: butuh header `X-Hermes-Session-Token: <token>` (auth_middleware, web_server.py:645).
- WS /api/ws: butuh `?token=<token>` (web_server_chat.py::_ws_auth_reason loopback branch).
- Token = env HERMES_DASHBOARD_SESSION_TOKEN (di-set Electron), di-inject di HTML index:
  `window.__HERMES_SESSION_TOKEN__="..."` — GET http://127.0.0.1:57840/ (200, public).
- WS peer check: loopback bind → peer IP HARUS loopback (peer_not_loopback) → HP via Tailscale
  DITOLAK walau token benar → PROXY LOCAL WAJIB.

## Multi-surface server-side (didukung)
- session.resume client kedua (same process) → _claim_or_reuse_live → reuse live sid + _rebind_live_transport
  → viewers + FanoutTransport → event streaming ke semua WS client.
- prompt.submit → _ensure_active_session_slot: lease sudah ada di session dict → no 4090.
- 4090 hanya kalau writer beda (pid,live_session_id) — ini yang selama ini terjadi via 8788.

## Desain implementasi
1. server/resolve-gateway.sh — baca active_sessions.json (surface=desktop) + lsof verify → JSON port/pid.
2. server/desktop_gateway_proxy.py — proxy 127.0.0.1:8790 (LaunchAgent com.hermes.desktop-gateway-proxy):
   - GET /api/desktop-port → JSON resolve (atau surface=mobile-serve fallback)
   - /api/ws → desktop (inject ?token= hasil scrape index) kalau hidup; else → 8788 verbatim (ticket)
   - /auth/*, /api/auth/*, /api/media → 8788 selalu (password auth + cookie)
   - /api/* lain → desktop kalau hidup (inject X-Hermes-Session-Token) else 8788
   - Host header di-rewrite 127.0.0.1:<port>; WS = raw relay setelah 101.
3. tailscale serve: / → 8790 (menggantikan 8788).
4. App: GatewayDiscovery (GET /api/desktop-port) → mode desktop (WS tanpa kredensial, proxy injek token)
   vs mode mobile (ticket seperti M1). GatewayClient: ticketSupplier → credentialSupplier (query string),
   dispatch logic TIDAK diubah.
5. Read-only banner di ChatScreen saat 4090 (banner + composer disabled).
6. SessionsScreen: dengarkan inbound events (message.*/session.reclaimed/session.title) → refresh badge RUNNING.

## Status
- [x] Discovery infra
- [x] resolve-gateway.sh + verify output (`{"port":57840,"pid":84269,"surface":"desktop"}`)
- [x] proxy + LaunchAgent + tailscale serve (serve → proxy :8790, bukan 8788 lagi)
- [x] app changes (discovery, credential, read-only, live badge)
- [x] emulator E2E: buka eefc53, kirim pesan, bukti no-4090, screenshot
- [x] sqlite bukti pesan masuk (msg id 52360, jawaban 52364)
- [x] build + APK ~/Desktop/hermes-mobile.apk + commit 4a6ffba + push

## Bukti verifikasi (28 Sep 15:50-15:53)
1. `bash server/resolve-gateway.sh` → `{"port": 57840, "pid": 84269, "surface": "desktop"}`
   (juga via Tailscale: `curl https://melverns-macbook-pro.tail4588ae.ts.net/api/desktop-port` → sama).
2. Emulator-5580 (APK M6): connect → badge "· desktop-linked" → buka "Hermes Android"
   (= 20260926_141522_eefc53, lease desktop pid 84269) → transcript 262 msg termuat →
   kirim "M6-verify-HP-via-gateway-desktop" → BERHASIL, BUKAN 4090.
   Screenshots: 01-app-launch / 02-after-connect / 03-send-success / 04-reply-live.
3. Bukti DB (state.db): user msg id 52360 masuk, assistant 52361/52364 balas —
   dan itu session yang live dipegang desktop pid 84269 → multi-surface terbukti.
   Bonus: badge "● RUNNING" muncul live di sessions list untuk session lain yang
   lagi jalan (event message.* → refresh) → requirement #3 live update terpenuhi.
4. Build `./gradlew :app:assembleDebug` BUILD SUCCESSFUL; APK ke ~/Desktop/hermes-mobile.apk
   (12.8MB); commit 4a6ffba push ke MelvernMogens/hermes-mobile.

## Catatan
- Login screen emulator fresh (DataStore ke-wipe) → URL+password diisi manual saat verify.
- Password basic-auth tidak pernah ditulis di repo (hash saja di ~/.hermes/config.yaml).
