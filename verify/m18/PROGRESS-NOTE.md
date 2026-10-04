# M18 — Overview screen (Fleet dashboard + Usage) — PROGRESS

Status kerja (updated live selama task):
- [x] Working tree ditemukan dari session sebelumnya (belum commit):
      OverviewRepo.kt + FleetLogic + BotFleet, HomeShell.kt, ui/overview/OverviewScreen.kt,
      OverviewLogicTest.kt, diff MainActivity/NotifRouter(FleetBus)/MetaRepo(include_sessions).
- [ ] Unit test + build (jalan: proc_32e375d9b101)
- [ ] Emulator verify verify/m18/ (7 screenshot)
- [ ] Reviewer subagent
- [ ] Commit + APK Desktop + release/ + push + PROGRESS.md

Spec inti: bottom tab Chats|Overview (compact bottom 56dp, expanded rail 72dp),
fleet cards (profiles.list include_sessions × active_list M14 poller via FleetBus),
usage.bars fail-open, tap card→Bot Chat canonical, long-press sheet (New chat/
Open Bot Chat/Switch profile), max 640 compact / 900 expanded 2-kolom >=1000dp.

## Verify independen Megatron (4 Okt, setelah coder kena 429 rate-limit zai)
- Build + unit test: PASS, 128 tests / 0 failures.
- Emulator 5572 (10.0.2.2:8790): 
  - Tab bar Chats|Overview tampil; badge Overview = jumlah running.
  - Overview: 12 bot tampil (default/analyst/coder/content/designer/growth/ops/pm/qa/research/video),
    avatar masing-masing, model line, default "Running" (hijau) — match realitas (session gw aktif).
  - Usage section: "Usage data unavailable" — SERVER memang balas available:false
    (probe usage.bars langsung: {"ok":true,"available":false} — akun key-based, bukan Nous portal).
    Fail-open bekerja, bukan bug.
  - Tap card coder → sheet aksi (New chat / Open Bot Chat / Switch profile) → Open Bot Chat →
    chat coder kebuka. Bukti: 02-overview-bots-live.png, 05-open-botchat-live.png.
- Files: verify/m18/*-live.png.
