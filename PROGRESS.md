# M9 — UX pass dogfood Melvern (28 Sep) — PROGRESS

Repo: ~/Code/hermes-mobile. Brief 6 item, verify di verify/m9/.
Baseline: commit 11c39ec (test 37 PASS).

## Status: SEMUA ITEM DONE + terverifikasi emulator 5572 + review fixes applied

- [x] 1. Tabel markdown — MdBlock.Table, header bold, weight kolom, hscroll
      >4 kolom / cell >40 char, divider hairline, cell ≤200dp ellipsis +
      tap → sheet. Bukti: verify/m9/01_table_render.png + 5 unit test.
- [x] 2. Reasoning effort — ModelSheet section Effort (4 chip Low/Medium/
      High/Max → kata server low/medium/high/max; VALID_REASONING_EFFORTS:
      minimal|low|medium|high|xhigh|max|ultra; desktop pakai kata sama).
      Live → config.set reasoning session-scoped; chat kosong → remembered
      → createSession reasoning_effort. Bukti: 02_effort_chips.png +
      config.get session live = {"value":"high"} setelah tap.
- [x] 3. Video player — regex .mp4/.mov/.webm/.mkv/.avi (line+anywhere),
      MediaRepo.fetchVideo via /api/mobile-media → cacheDir, media3
      ExoPlayer+PlayerView 16:9 radius10 controller ON. Proxy _IMG_MIME +
      suffix video (auth guard UTUH — dibuktikan 403 path-not-in-chat).
      Bukti: 03_video_player.png + cache/mdvideo_m9_test_clip.mp4 50608B.
- [x] 4. Copy seleksi — SelectionContainer per konten message; long-press
      full-copy tetap (pointerInput wrapper; area teks → seleksi native,
      area non-teks → sheet Copy text). Bukti: 04_selection_handles.png
      (handle+toolbar native) + 04c_fullcopy_sheet.png (sheet Message).
- [x] 5. Bot Chat rapi — gap konsisten, nested bullet (NestIndent 16dp/
      level), spasi ganda kolaps, emoji utuh (✅ terlihat di screenshot
      verify ulang). Bukti: 05_botchat_after.png.
- [x] 6. TranscriptCache LRU-8 (core/repo) key storedId → (items,
      runtimeId, cursor events.since); ChatScreen hydrate dari cache →
      delta replaySince(cursor) → fallback full resume; rememberSaveable
      input. Bukti: log "cache hit: 20260926_141522_eefc53 (353/363
      items)" + 06_back_reopen_cache_hit.png + rotate landscape↔portrait
      transcript utuh.

## Review subagent (claude-sonnet-5, 1 reviewer fresh) — temuan & fix
- HIGH cacheSnapshot pakai val client stale → FIXED app.client.
- HIGH open_requests di-skip saat running=true (approval pending) →
  FIXED: resume penuh selalu restore out.openRequests; lazy hanya delta.
- MED indent nested 20dp → FIXED 16dp (NestIndent).
- MED HR `---` polos dianggap separator tabel → FIXED pipe wajib + test.
- LOW kata di luar 4 chip (minimal/xhigh/ultra) → semua chip tampil
  non-aktif (sesuai brief literal 4 chip; dicatat sebagai edge known).

## Build/test (final, setelah review fixes)
- :app:testDebugUnitTest = 52 tests, 0 failures (baseline 37 + 15 baru).
- :app:assembleDebug PASS. APK → ~/Desktop/hermes-mobile.apk.
- Commit 409ac13..HEAD per-item + push origin/main.

## Catatan
- Emulator: emulator-5572 (Medium_Phone_API_36.1 = alias Medium_Phone.avd).
- Session test 1525 pesan: full load awal di emulator lambat (GC) —
  perbaikan utamanya justru TranscriptCache (reopen instan).
- Rotasi diuji settings user_rotation 1/3 (accelerometer off) — beres.
