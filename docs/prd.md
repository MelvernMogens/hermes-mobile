# PRD — Hermes Mobile

## 1. Masalah
Melvern pakai Hermes desktop di Mac dan ingin kontrol yang sama dari HP
Android. Client yang ada (Hermex) menyasar `hermes-webui` (server pihak ketiga):
session desktop tidak muncul (profile-scoping bug upstream #6879) dan chat
tidak update real-time (poll 30s yang berhenti saat tab hidden).

## 2. Solusi
App Android native yang menembak backend resmi `hermes serve` — JSON-RPC over
WebSocket, protokol yang sama dengan desktop app (219 method, contract OpenRPC
generated). Koneksi via Tailscale; auth via dashboard basic-auth + ws-ticket.

## 3. User
Melvern (tunggal). UI language: English (user request 28 Sep).

## 4. Non-goals (M1)
- Multi-user / akun.
- Offline-first (cache transcript lokal).
- Menjalankan agent di HP (backend tetap di Mac).
- Light theme.

## 5. Inventory fitur (turunan desktop)
Session list + search, transcript + branch, kirim pesan + steer/interrupt,
tool activity, approval/clarify cards, reasoning view, model/provider switch,
cron jobs, browser control, memory, skills, usage.

## 6. What not to oversell
Boleh bilang: "app Android yang nampilin semua session Hermes-mu dan bisa
ngobrol live dengan agent-nya, koneksi lewat Tailscale."
Tidak boleh bilang: "semua fitur desktop ada di HP" (M1 cuma core loop).
Live streaming dari turn yang JALAN di desktop app lain belum real-time
(hasilnya kelihatan setelah selesai).

## 7. Arsitektur keputusan
- Target `hermes serve` (bukan hermes-webui, bukan API-server gateway).
- Satu GatewayClient per app process; reconnect loop tunggal.
- Port fix 8788 + LaunchAgent supaya tailscale serve stabil.

## 8. Risiko
- Contract wire berubah saat Hermes update → cek `gateway-contract.openrpc.json`.
- Tailscale di HP harus login akun yang sama.
- Emulator test ≠ HP beneran (keyboard, battery, background kill) — M3.

## 9. Open questions
- Notifikasi background Android: foreground service vs push (butuh infra)?
- Apakah desktop app harus dialihkan ke :8788 juga biar live fan-out penuh?
  (desktop sekarang spawn serve sendiri di port acak)
- Signed APK distribution: sideload saja atau Play Internal?
