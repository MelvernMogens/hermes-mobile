# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
Melvern (pemilik tunggal). Situation: jauh dari Mac (-mobile), butuh ngontrol
agent Hermes yang jalan di Mac-nya: lihat session yang sama kayak desktop,
kirim chat, pantau jawaban streaming. Bahasa UI: Indonesia santai.

## Product Purpose
Client Android native untuk Hermes Agent pribadi di Mac — backend resmi yang
sama dengan desktop app (`hermes serve` JSON-RPC/WS via Tailscale). Sukses =
buka app, semua session desktop kelihatan, chat balas live, gak mikirin
koneksi.

## Positioning
Satu-satunya client mobile yang ngomong LANGSUNG ke protokol desktop Hermes
(bukan server pihak ketiga seperti hermes-webui) — session & transcript selalu
sinkron dengan desktop karena satu state.db.

## Operating Context
- Mac jalan 24/7 dengan LaunchAgent `hermes serve` :8788 + Tailscale + basic auth.
- User juga pakai Hermes desktop, bot fleet (11 bot), dan WhatsApp demo bot.
- Session yang dibuka di HP biasanya lanjutan kerja desktop (Vasanta, Blokees, dll).

## Capabilities and Constraints
- M1 done: connect (password→ws-ticket→WS), session list, transcript, kirim
  pesan, streaming jawaban live. E2E terverifikasi.
- Protokol fixed: JSON-RPC 2.0 (219 method) — contract di
  `~/.hermes/hermes-agent/apps/shared/src/gateway-contract.openrpc.json`.
- Reconnect harus seamless (heartbeat, replay) — sudah di GatewayClient.
- UI saat ini polos (komplain user: "kek app bawaan") → redesign M2.

## Brand Commitments
- Dark-first, immersive, chrome minimal, BENCi tombol gede (preferensi user
  lama, binding).
- Satu aksen warna (nous-blue terang), bukan dua aksen bersaing.
- Tanpa emoji-icon; Material icons vector.

## Evidence on Hand
- Screenshot M1: docs/assets/hm-sessions.png, docs/assets/hm-chat.png
- Teardown Hermex (anti-pattern): docs/m2-ui-notes.md
- Riset desain AI chat 2025-2026: /tmp/ui-research-finding.md (subagent)

## Product Principles
1. Connection itu invisible — user gak boleh nunggu/n mikirin state koneksi.
2. Info density tinggi, chrome tipis — konten dulu, kontrol micro.
3. Satu sumber kebenaran visual — design tokens terpusat, gak ada nilai hardcode di screen.
4. Presisi literal — yang di-preview = yang jadi.
5. Indonesia santai di semua copy UI.

## Accessibility & Inclusion
Touch target 48dp minimum; sp units (ikuti font scale sistem); dark theme
first-class; uji font_scale 1.3 saat polish.
