<div align="center">

# Hermes Mobile

**Client Android native untuk Hermes Agent di Mac-mu — backend yang sama dengan desktop app.**

</div>

Semua session desktop/CLI/bot muncul. Chat streaming real-time lewat WebSocket
JSON-RPC (protokol yang sama persis dengan Hermes Desktop). Koneksi lewat
Tailscale + password. Tidak ada server perantara, tidak ada analytics.

## Kenapa ini ada
Hermex (client Android community) hanya bisa ngomong ke `hermes-webui` — server
pihak ketiga yang punya view session sendiri. Session desktop tidak muncul dan
update-nya lambat (polling yang berhenti saat screen mati). Hermes Mobile
menembak langsung ke backend resmi `hermes serve` — satu `state.db`, satu
sumber kebenaran.

## Cara kerja
```
 HP Android ──Tailscale──▶ Mac
   │                        │ tailscale serve --bg 8788
   │                        ▼
   │              hermes serve :8788  (LaunchAgent)
   │                        │  basic auth + ws-ticket
   └── WebSocket JSON-RPC ───┘
       (protokol tui_gateway — sama dengan desktop app)
```

- **Auth**: password login → cookie session → WS ticket single-use (30s) →
  upgrade WebSocket. Jalur native-app resmi dashboard auth.
- **Keepalive**: `gateway.ping` tiap 15s, deadline 45s, reconnect full-jitter
  300ms→15s, replay `session.events.since` setelah reconnect.
- **Session runtime** diparkir server 15 menit setelah WS putus
  (`ws_orphan_reap_grace_s 900`) — cukup untuk pindah jaringan / screen off.

## Setup (sekali, di Mac)
```bash
bash server/install.sh
```
Skrip idempotent: LaunchAgent `com.hermes.mobile-serve` (serve :8788),
basic auth + public URL Tailscale, `tailscale serve --bg 8788`. Password
tercetak di akhir output — dipakai di app.

## Build
```bash
cd app-android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Status
- **M1 done** — connect, session list desktop, transcript, kirim pesan,
  jawaban streaming live. Diverifikasi E2E di emulator.
- Todo: markdown render, approval cards, notifikasi background, signed release.

## Batasan jujur
- Turn yang sedang BERJALAN di desktop hanya terlihat hasilnya setelah
  selesai (transcript di-refresh saat buka chat) — live fan-out antar-client
  untuk turn berjalan belum (butuh server yang sama persis; sekarang port 8788
  terpisah dari desktop app port-nya sendiri).
- Tidak ada offline cache (M2+).
- Bahasa UI Indonesia (untuk pemilik tunggal).
