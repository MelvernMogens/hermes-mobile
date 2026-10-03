# M13 — Artifacts per session — PROGRESS

Repo: ~/Code/hermes-mobile. Brief M13 (artifacts per chat), verify di verify/m13/.
Baseline: commit c0c24be (test 64? tidak — baseline 52 PASS, lihat bawah).

## Status: DONE + terverifikasi emulator 5572 (3 Okt) + review fixes applied

### Apa yang dibangun
Pure client-side (TANPA RPC baru, tanpa sentuh core/rpc, core/auth, core/store, server/):
- [x] Entry point: ikon Inventory2 di top bar ChatScreen (kiri MoreVert) +
      badge jumlah artifacts (>99 → "99+"). Tap → route `artifacts/{storedId}|t=title`.
- [x] Parser pure (ui/chat/ArtifactsParser.kt): link (regex URL + trim tanda baca
      identik MdSpan.Link), foto (imagePathsIn), video (videoPathsIn), file
      (ekstensi non-media pdf/zip/txt/apk/json/md/dll + chip @file:/@image: di
      pesan user). Distinct per (type,value) keep kemunculan terbaru, sort
      terbaru-dulu, grup hari via RelTime (Today/Yesterday/gaya chat).
- [x] ArtifactsScreen Quiet Mono: top bar back + "Artifacts" + subtitle nama chat;
      row 56dp ikon per type (Link/Image/PlayCircle/Description) lingkaran surface1
      36dp, judul 1 line, sub meta domain/path pendek, waktu relatif kanan;
      skeleton 4 row saat parse; empty state ikon 48 text3.
- [x] Tap behavior: link → openUrlExternal (Custom Tabs); image → Dialog fullscreen
      pinch-zoom (transform gesture 1x–5x + pan) + tombol Save reuse MediaFetchSave;
      video → Dialog player reuse MarkdownVideo (media3, kontrol ON); file →
      MediaFetchSave.saveAny + Toast path/hasil.
- [x] Data: parse on-demand dari TranscriptCache tiap buka screen (no 2nd cache);
      note "Older messages not loaded" kalau entry cache tidak ada (belum hydrate).
      Parse di Dispatchers.Default (transcript 187+ msg gak jank main thread).

### Bukti verify (verify/m13/, emulator 5572, server 10.0.2.2:8790, login melvern/ted)
- 01-artifacts-list.png — chat "Hermes Android" (badge 89) → list campuran
  File (hermes-mobile.apk, send_media2.py) + Link (127.0.0.1:8790, github.com) +
  Video (hermes-test-video.mp4) + Image (MEGATRON.png, upload_*.jpg), grup "Yesterday".
- 02-artifact-link.png — tap link 127.0.0.1:8790 → Chrome kebuka
  (dumpsys: topResumedActivity=com.android.chrome FirstRunActivity).
- 03-artifact-image.png — tap MEGATRON.png → fullscreen dialog + Save kanan-atas
  (vision check: pixel-art robot tampil penuh, dark bg).
- 04-artifact-video.png — tap hermes-test-video.mp4 → player jalan (frame SMPTE
  color bars terlihat, kontrol + progress 00:04/00:05, header close + filename).
- 05-empty.png — chat kosong (New chat) → empty state "Nothing shared yet" +
  sub, ikon box 48dp text3, centered.
- Unit test: ArtifactsParserTest 12 test (link http/https/markdown/ip-host-judul-jalur,
  image path, video+file ext, chip @file:/@image:, campuran multi pesan, distinct
  keep-latest, skip streaming/tool/notice, grup hari rows, empty).

### Review subagent (glm-5.3, 1 reviewer fresh) — temuan & fix
- HIGH phantom File/Image: substring path di DALAM URL (https://x.com/a.pdf →
  File palsu) → FIXED: URL di-mask dengan spasi sebelum match path. Bukti:
  badge "Hermes Android" 89 → 84, 0 row judul "https://" sisa.
- HIGH badge jank: full parse di main thread per delta streaming → FIXED:
  LaunchedEffect + Dispatchers.Default, skip saat running.
- MED TranscriptCache.get() dobel (counter observability) → FIXED: sekali,
  hasil direuse.
- MED empty state menyesatkan saat cache ter-evict → FIXED: state terpisah
  "Nothing loaded yet" + GIG baru menulis snapshot kosong ke cache.
- MED chip @file: trailing punctuation + chip di dalam URL → FIXED: clean +
  word-boundary + mask URL.
- MED ExoPlayer leak di MarkdownVideo saat file berganti (pre-existing M9) →
  NOT FIXED (di luar scope M13, jalur M13 stabil per-path) — dicatat utk M14.
- LOW dead code domainOf dihapus; key row stabil (type,value) tanpa indeks.

## Build/test (final, setelah review fixes)
- :app:testDebugUnitTest = 71 tests, 0 failures (52 baseline + 19 baru).
- :app:assembleDebug PASS. APK → ~/Desktop/hermes-mobile.apk + release/ (git add -f).
## M15 — Adaptive layout tablet (Tab S8) — done 3 Okt
Brief: two-pane list-detail WhatsApp-style di layar >=840dp, konten gak
full-stretch, artifacts side sheet, sheets centered, compact identical.

### Terimplementasi
- Dep `material3-window-size-class` (BOM sama). WindowSizeClass dihitung di
  MainActivity → HermesApp.windowSize (flow) + LocalWinSize (CompositionLocal).
- Threshold dua-pane TERUKUR: sizeClass Expanded DAN screenWidthDp >= 1000.
  Alasan: tablet portrait 1600px@276dpi = 928dp dan phone landscape modern
  ~914dp keduanya masuk Expanded kanonik (>=840) padahal bukan target
  two-pane. Tab S8 landscape 2560px@276 = 1463dp → two-pane.
- Chats+Chat two-pane: Row { SessionsPane 340dp fixed + hairline + ChatPane
  weight 1f }. Selection = rememberSaveable paneSelection di SessionsScreen
  (bukan nav). Compact = nav seperti biasa. Back di expanded = clear selection.
- ChatPane kosong: "Select a chat" + sub, center.
- Konten chat max 640dp CENTER per blok: header Row, LazyColumn (wrapper Box
  fillMaxWidth+TopCenter), Composer, hairline. Medium+ (tablet portrait) juga
  kena cap; Compact full.
- Bubble user: max = min(80% pane, 560dp).
- Artifacts: expanded → side sheet 380dp slide kanan tanpa scrim (reuse
  ArtifactsScreen penuh), compact → route full screen seperti M13.
- QuietSheet: expanded → Dialog center widthIn(max 480), compact →
  ModalBottomSheet M8 (satu pola konsisten: dialog 480).
- Connect: max 420 center M8 dipertahankan (sudah benar).

### Ukuran aktual (emulator 5572, wm 2560x1600@276, px = dp x 1.725)
- Divider list|chat: x=588px = 340.8dp.
- Konten chat landscape: 1054-2084px (center 1569 ≈ ideal 1574; 640dp=1104px).
- Portrait tablet (1600x2560@276 = 928dp = Medium): single-pane; chat konten
  278-1300px center 789 (cap 640dp jalan).
- Artifacts side sheet: x=1904..2560 = 656px = 380dp tepat, no scrim,
  list kiri + chat tetap terlihat.
- Sheet profile tablet: surface 1082-1694 (<=480dp), centered.
- Phone compact (1080x2400@420): konten full 42-1036px, nav lama, tidak
  berubah. Compact landscape (2400x1080): konten capped center 1206, header
  56dp (58px@2.625), tanpa duplikat padding.
- Rotate landscape↔portrait: selection bertahan (chat sama tetap render).
  verify/m15/01..07 screenshots.

### Unit test
- AdaptiveTest 6 test: mode mapping (hanya Expanded two-pane, Medium/Compact
  single), parser ChatRouteArg (bare/stored|runtime/stored|t=encoded,
  roundtrip rotate, percent-decode UTF-8 pure Kotlin — Uri.decode stub di JVM).
- Total: 102 tests, 0 failures.

### Review subagent (glm-5.3, 1 reviewer fresh) — temuan dan fix
- HIGH state ChatScreen bocor antar chat di two-pane (pindah row pakai state
  chat lama: draft, approval, scroll) → FIXED: key(sel) di sekitar ChatScreen.
- HIGH pane kiri tanpa statusBarsPadding (bentrok status bar edge-to-edge) →
  FIXED: statusBarsPadding selalu.
- MED rotate saat "new chat" terpilih → cache transcript tertimpa kosong →
  FIXED: branch NEW GIG hanya jika cache kosong.
- MED selection stale setelah chat dihapus dari list → FIXED: onDone cek
  paneSelection vs target.id.
- MED side sheet juga aktif di Medium tanpa scrim → FIXED: hanya Expanded.
- MED exit animation dead (visible=true konstan) → FIXED: state closing
  (masih satu-arah; dismiss via Back).
- LOW dead param inTwoPane dihapus; winSize satu sumber (CompositionLocal).
- NOT FIXED (dilaporkan, minor): scrim dialog sheet pakai dim platform;
  skipPartiallyExpanded diabaikan di path Dialog.

## Build/test M15 (final, setelah review fixes)
- :app:testDebugUnitTest = 102 tests, 0 failures (96 baseline + 6 baru).
- :app:assembleDebug PASS. APK → ~/Desktop/hermes-mobile.apk + release/.
