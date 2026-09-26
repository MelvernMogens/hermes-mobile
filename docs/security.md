# Security — Hermes Mobile

## Model ancaman
Pemilik tunggal (Melvern) mengakses agent pribadinya dari HP. Adversary:
orang lain di tailnet Tailscale-nya (tidak ada, akun pribadi), atau orang di
jaringan yang sama dengan HP.

## Yang sudah dilindungi
- Backend bind `127.0.0.1:8788` saja — tidak ada expose publik.
- Akses luar hanya lewat `tailscale serve` (tailnet-only, TLS otomatis).
- Mode gated auth aktif (`dashboard.public_url` set) → SEMUA request tanpa
  session cookie ditolak 401; WS upgrade butuh ticket single-use 30 detik;
  legacy `?token=` DITOLAK di gated mode (token lama tidak berlaku).
- Password basic auth disimpan scrypt-hash di config.yaml; plaintext hanya di
  app (DataStore private storage) dan file `~/.hermes/mobile-serve.env.tmp`
  (mode 600) untuk setup.
- Rate limit login bawaan dashboard auth (429 setelah beberapa percobaan).

## Catatan hardening (temuan verifier 26 Sep)
- Server tidak memberlakukan subprotocol WS — upgrade 101 jalan juga tanpa
  `Sec-WebSocket-Protocol`. Auth gate tetap utuh (ticket wajib), jadi ini bukan
  lubang, tapi kalau mau pinning versi protokol, itu urusan sisi server
  (upstream Hermes), bukan app ini.

## Yang perlu diperhatikan
- Jangan pernah set `HERMES_SERVE_INSECURE` / bind 0.0.0.0.
- Password app di DataStore = root device bisa baca; ancaman fisik di luar scope.
- `usesCleartextTraffic=true` di manifest untuk dev HTTP — rilis harus false
  (saat semua koneksi https/wss).
