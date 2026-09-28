# M8 — Quiet Mono redesign — PROGRESS

Status: DONE (28 Sep) — satu blocker verify: emulator ke-logout (lihat "Sisa").

## Baseline
- `./gradlew :app:testDebugUnitTest` sebelum perubahan → 17 tests, 0 failures (MarkdownParserTest).
- Akhir: 37 tests, 0 failures (MarkdownParser 17, RelTime 7, ConnectErrors 5, ChatRows 4, ToolResultText 4).
- `./gradlew :app:assembleDebug` PASS; install emulator-5572 Success.

## Per layar
- Theme: `Ink` (Color.kt) ramp #0B0B0C→#26262A, hairline, Text/Text2/Text3, semantik
  Live/Warn/Danger; `Type` Inter 4 weight (res/font, OFL di docs/licenses); `Dim`/`Radius`/
  `hairline()`; `Motion` (220 nav / 180 msg / 1.2s pulse, reduce-motion); ripple putih 8%.
  Text3 = #808086 (brief #6C6C72 & fallback #7A7A80 gagal 4.5:1 di Surface1).
- Connect (01, 02, 02b, 02c): logo 56, "Connect to your Mac", OutlinedTextField radius 12,
  auto https://, password masked + toggle, tombol putih 50dp, loading spinner hitam tetap di atas
  putih, error manusiawi (unreachable / 401 / HTTP n). Footnote install.sh dihapus.
- Sessions (03, 04, 10, 11): avatar profil 32 + dot status, large title collapse, Search inline +
  Edit, row 72dp anatomi seragam, ring running, "Running", divider inset 72, section "Hidden",
  skeleton loading, empty state.
- Sheets (05, 09): QuietSheet Surface1 radius 22, handle 32×4, scrim 60%; Profiles row 64 +
  Check + Switch "Show hidden chats" di bawah; Model sheet Check + footer.
- Chat (06*, 07, 08, 11): top bar avatar + title + model line / "Working…" pulse, MoreVert aksi
  session, hairline saat scroll; bubble user, assistant tanpa bubble, markdown baru (code block
  header + copy→check, inline code, link underline), tool rows + "Used N tools", Thinking →
  "Thought for Ns", day chip, scroll-to-bottom + badge, empty state, skeleton; composer
  Add/field/send-stop, attach sheet Photo/File, attachment chip thumbnail; approval/clarify card.
- System: edge-to-edge transparan, splash v31 #0B0B0C + logo, icon bg #0B0B0C.

## Bug yang ketemu saat verify (difix)
1. Tool row live gak bisa di-expand: `tool.start/complete` cuma nyimpen nama. Sekarang simpan
   `tool_id` + args (`args_text`/`preview`/`context`) + output (`result_text` / `result` /
   `summary`) → expand = code block. Match start↔complete by tool_id (dulu by nama → 2 tool
   sama nama ke-complete barengan). Test: ToolResultTextTest.
2. Connect mode desktop (proxy 8790) SKIP probe password → password salah tetap "connected".
   Sekarang probe selalu; di mode desktop hanya 401 yang memblok. Bukti: 02c (wrongpass →
   "Wrong username or password.").
3. Tombol Connect saat loading jadi abu gelap + teks hitam (disabled colors) → tetap putih.
4. Ukuran ikon 18/20dp (send, remove attachment, search field) → Dim.Icon 22.

## Review subagent (1 reviewer fresh, claude-sonnet-5, report-only)
Baca brief + diff + semua screenshot. Temuan: LOW — ikon 20/18dp (fixed #4); LOW — screenshot
01/02/09/11 & DESIGN.md belum ada saat review (sekarang ada). Tidak ada temuan high/med: nol hex
di luar theme, nol emoji-icon, Rounded konsisten, nol uppercase/tracking, row konsisten, callback
fitur M1–M7 semua masih ter-wire, core/rpc|auth|repo|store + server/ tidak disentuh.

## Sisa / blocker
- Emulator-5572 sekarang di layar Connect (pm clear untuk screenshot 01/02). Password basic auth
  TIDAK ada di ~/.hermes/mobile-serve.env (isinya HERMES_DASHBOARD_SESSION_TOKEN; server balas
  401 untuk nilai itu) — config.yaml cuma simpan hash. Login ulang perlu Melvern isi password.
- SECURITY (server/, di luar scope M8, belum disentuh): proxy 8790 gak cek auth sama sekali
  saat desktop hidup. Terverifikasi 28 Sep tanpa cookie/tiket:
  `curl … Upgrade: websocket http://127.0.0.1:8790/api/ws` → 101;
  `curl http://127.0.0.1:8790/api/sessions` → 200. `tailscale serve` → 8790, jadi siapa pun di
  tailnet dapat akses penuh ke gateway desktop. Fix #2 cuma nutup di sisi app.
- 06b_chat_codeblock.png dari build sebelum fix #1 (visual code block sama).
