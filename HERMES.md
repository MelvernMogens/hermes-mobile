# hermes-mobile

## What this is
Client Android native untuk Hermes Agent yang jalan di Mac Melvern — target
backend yang SAMA dengan desktop app (`hermes serve` JSON-RPC/WebSocket), bukan
server pihak ketiga. Semua session (desktop/CLI/bot) muncul, chat streaming
real-time. Dibuat karena Hermex (port Android dari hermes-webui) ngomong ke
server lain yang tidak melihat session desktop dan update-nya lambat.

## Stack
- **Kotlin + Jetpack Compose** (minSdk 26, compileSdk 36, JVM 17), single-activity,
  tanpa DI/Room — state di DataStore. Alasan: sama dengan Blokees (proven di
  mesin ini), ringan, dan UI-nya memang Compose.
- **OkHttp WebSocket + kotlinx-serialization** — protokol JSON-RPC 2.0 di-port
  dari `apps/shared` Hermes desktop (heartbeat `gateway.ping` 15s/45s deadline,
  reconnect full-jitter 300ms→15s, replay `session.events.since`).
- **Auth**: basic auth dashboard (`/auth/password-login` → cookie →
  `/api/auth/ws-ticket` single-use 30s → WS `?ticket=`) — jalur native-app resmi.
  Password disimpan di DataStore (private app storage).
- **Backend**: LaunchAgent `com.hermes.mobile-serve` = `hermes serve --port 8788`
  + `dashboard.public_url` (Tailscale) + basic auth + `ws_orphan_reap_grace_s 900`.
  Expose via `tailscale serve --bg 8788`. *Ditolak: hermes-webui (beda dunia,
  session desktop tidak kelihatan — filter `show_cli_sessions` default off,
  profile scoping, polling yang berhenti saat screen mati); API-server gateway
  :8642 (REST/SSE, documented — tapi beda protokol dari desktop: gak bisa
  resume runtime session desktop, gak ada event fan-out tui_gateway, dan
  butuh `hermes gateway` + flag baru. Dipilih serve karena Melvern minta
  "functionnya kaya desktop app" = protokol yang sama, dan sudah dibuktikan
  E2E + verifier independen 5/5 PASS).*

## Commands
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd app-android
./gradlew :app:assembleDebug        # APK → app/build/outputs/apk/debug/
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
```
Server: `bash server/install.sh` (idempotent; set LaunchAgent + tailscale serve).
Log server: `tail -f ~/.hermes/logs/mobile-serve.log`.

## Milestones — status
| # | Scope | Status | Model / effort |
|---|---|---|---|
| M1 | Slice E2E: connect (auth+ticket+WS), session list desktop, buka chat + transcript, kirim pesan, jawaban live streaming | done (emulator 5580, 26 Sep) | GLM 5.3 / high |
| M2 | UI premium pass: markdown render, reasoning collapsible, approval cards, interrupt UI, session search | todo | — |
| M3 | Notifications (foreground service), share target, quick tile | todo | — |
| M4 | Parity fitur desktop (model picker, approval, aksi session, profile) | done (27 Sep) | GLM 5.3 + 1 subagent reviewer |
| M5 | Signed release APK + update path | done (M5a/M5b/M5c 27-28 Sep) | GLM 5.3 / high |
| M6 | Multi-surface HP+desktop via gateway desktop (proxy 8790) | done (28 Sep) | GLM 5.3 |
| M8 | Full visual redesign "Quiet Mono" (DESIGN.md): token Ink/Type/Dim, Inter, Icons.Rounded, 5 layar + sheets + motion + splash; bonus: tool row expand isi args+output, Connect probe password juga di mode desktop | done (28 Sep) — verify/m8 | Opus + 1 subagent reviewer |
| M9 | 6 UX pass dogfood 28 Sep: tabel markdown, effort chip di ModelSheet (config.set reasoning), video player media3 via proxy mobile-media, seleksi teks sebagian (SelectionContainer), Bot Chat render audit, TranscriptCache LRU-8 (back/rotate gak reload) | done (2 Okt) — verify/m9, 52 unit test, 1 subagent reviewer (4 temuan difix) | GLM 5.3 + reviewer sonnet |
| M7 | 3 fix UX dogfood: banner read-only derived (4090 nyata + gateway mobile), avatar WEBP raw-base64 parse + inisial loading, showHidden persist DataStore (default ON) | done (28 Sep) — reviewer subagent: watchdog race + race load DataStore, keduanya difix | GLM 5.3 + 1 subagent reviewer |
| M13 | Artifacts per chat: ikon+badge di top bar → ArtifactsScreen (link/foto/video/file dari TranscriptCache, pure client-side, tanpa RPC baru), grup hari, tap link=browser/foto=fullscreen zoom+Save/video=player/file=save+toast, 12 unit test parser | done (3 Okt) — verify/m13, 71 unit test, 1 subagent reviewer (2H+3M difix) | GLM 5.3 |
| M14 | Notifikasi background tanpa FCM: foreground service dataSync + partial wake lock 4h re-acquire + poller session.active_list 20s (event ring TIDAK sampai ke non-viewer — terbukti E2E) → notif reply/approval/error, dedup 30s, deep-link tap→chat, POST_NOTIFICATIONS flow + banner, heartbeat 15s/25s fg/bg, channel agent high + connection low | done (3 Okt) — verify/m14, 96 unit test, 1 subagent reviewer (2H+4M difix) | GLM 5.3 |

## Konvensi
- Bahasa kode/komentar Indonesia; UI string English (user request 28 Sep).
- Satu GatewayClient process-wide (HermesApp) — jangan bikin client kedua.
- Wire contract: `~/.hermes/hermes-agent/apps/shared/src/gateway-contract.openrpc.json`
  (219 method) — sumber kebenaran untuk field.

## Pelajaran keras (jangan diulang)
- **Frame event JSON-RPC tidak punya `id`** — dispatch harus match `method`
  dulu, bukan `id&&method`. Bug ini bikin semua event dibuang senyap.
- `session.resume` dengan `defer_history:true` balikin `messages: []` —
  SELALU resume tanpa defer (187 msg = 694KB, aman).
- `ws_orphan_reap_grace_s` default 20s terlalu pendek buat mobile —
  sudah dinaikin ke 900s via config.
- connectLoop tidak boleh bikin socket baru sebelum yang lama mati total
  (jangan complete-kan deferred di `onOpen`).
- Emulator: `adb -s emulator-5580` (AVD Blokees dipakai bareng — jangan
  `adb kill-server`).
- **M14**: event ring gateway hanya terkirim ke transport viewer session itu —
  deteksi aktivitas background WAJIB polling `session.active_list` (status
  working/waiting/idle + message_count + preview + session_key), bukan listen
  inbound. Notif id & PendingIntent requestCode = storedId.hashCode().
- logcat emulator API 36 kadang tak menampilkan log app lama — untuk bukti
  gunakan `logcat -v threadtime -T 1` live capture + dumpsys notification.
