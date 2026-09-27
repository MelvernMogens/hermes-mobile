# UI Research Finding — Desain AI Chat Mobile Terbaik 2025–2026
Target: Hermes Mobile (Compose, dark-first, immersive, chrome minimal, tanpa tombol gede, presisi tinggi)
Metode: analisis ChatGPT / Claude / Gemini (Neural Expressive) + design-system docs + audit repo `/tmp/hermex-inspect`
Tanggal: 2026-09-27

---

## 0. Diagnosis repo sekarang (kenapa "kek app bawaan")

Audit `ui/theme/Theme.kt` + `HermexChrome.kt`:

| Masalah | Bukti di repo | Efek persepsi |
|---|---|---|
| Warna = iOS system palette | `primary = 0xFF0A84FF`, `surfaceVariant = 0xFF1C1C1E`, `error = 0xFFFF453A` (iOS semantics) | Terlihat seperti template iOS, bukan identitas sendiri |
| Background pure black tanpa tonal layer | `background = Color.Black` | Flat "stock"; ChatGPT/Gemini juga gelap tapi berstruktur (tone steps) |
| Font sistem saja | `FontFamily.SansSerif` untuk SEMUA role | Tidak ada kontras tipografi user-vs-assistant → terasa generic |
| Radius scale acak | 5, 6, 8, 9, 12, 13, 14, 18, 20, 21, 22, 24, 44, 999 dp dipakai bersamaan | Tidak ada sistem; ini ciri khas "default app" |
| Light scheme juga ada tapi half-hearted | `background = 0xF5F4F7FA` (ARGB typo-ish, alpha di wrong place) | fokus dark-first saja lebih baik |

Kesimpulan arah: **bukan menambah ornamen — tapi sistem token yang punya pendapat** (warm-tinted dark atau cool-neutral berstruktur, type role-asymmetry, radius 3-step, spring motion konsisten).

---

## 1. Analisis 3 app top

### 1.1 ChatGPT (OpenAI) — "quiet gray canvas, conversation-first"

- **Bubble vs flat**: user = bubble filled abu (`#303030` di dark) align kanan; assistant = **flat full-width, tanpa container** — hanya teks + avatar kecil. Ini konsensus industri 2025–2026: full-width untuk assistant karena bubble SMS-like "undermines the tool framing" (setproduct).
- **Dark palette**: rangkaian neutral `#212121 / #303030 / #48484a`; accent blue lembut `#a8c7fa` / `#8ab4f8` (bukan blue satur); user-bubble gray, bukan blue. Accent hanya ~12% dari pemakaian warna.
- **Typography**: satu keluarga font (system / Söhne di web), hierarchy lewat weight 400→650 — tidak menambah typeface.
- **Composer**: pill `999px`, auto-grow, plus-button kiri (attachment/tools), mic, send kecil di kanan. Send→Stop toggle di slot yang sama.
- **Reasoning**: collapsed pill "**Thought for 12 seconds**" — tap untuk expand. Tidak pernah auto-expand; reasoning = second-class citizen, jawaban = first-class.
- **Thinking indicator**: input composer **"shimmers"** saat streaming (dokumen resmi OpenAI Apps SDK menyebut pattern ini), bukan spinner global.
- **Micro-interactions**: send button morph ke stop square; copy button muncul on-hoover/tap dengan fade; pesan masuk tanpa animasi mencolok — tenang.
- **Empty state**: greeting pendek + 3–4 suggestion chips dismissible; chips tidak pernah block composer.
- **Elevation**: hampir tanpa shadow — "blur-less hairline offsets; this system barely uses elevation".

### 1.2 Claude (Anthropic) — "warm paper, prose-first" (paling relevan untuk rasa premium)

- **Bubble vs flat**: user = **soft pill** (rounded rect rendah-kontras); assistant = **flat, tanpa bubble, type flowing di canvas** dipimpin avatar logomark 16pt. "No chat bubbles with tails — user pill (soft) + assistant flat flow."
- **Dark palette (warm, bukan blue-grey)**: canvas `#1F1B16` (warm near-black, orange-undertone), surface1 `#2A2520`, surface2/pressed `#3A332C`, text primary `#E8E0D2` (cream), secondary `#B5AB9E`, accent **Claude Orange `#D97757`** yang tidak berubah di dark. "Don't blue-tint the dark mode" — aturan eksplisit.
- **Typography — ini signature-nya**: **assistant body pakai serif (Tiempos) 16pt line-height 1.55; user + chrome pakai sans (Styrene/Inter)**. Role-asymmetry tipografi = brand. Body tidak pernah < 16pt ("Claude is prose, not Slack").
- **Composer**: bottom-pinned, auto-grow textarea + model chip + paperclip; send button = satu-satunya elemen orange di layar (dengan pressed state `#BE6242`).
- **Streaming cursor**: **caret orange kecil yang blink** saat generating — cursor jadi brand moment, bukan afterthought.
- **Reasoning/thinking**: chip "thinking…" dengan soft fill `#F2DDD0` (light) / `#4A352A` (dark), expandable.
- **Elevation**: flat — "conversation surface intentionally without elevation karena typography adalah visual interest". Shadow warm `rgba(40,30,20,0.06)`, never blue-grey. Depth via tone steps (`#F8F4ED → #FBF9F4 → #F0EAE0`), bukan shadow.
- **Code block**: selalu di warm dark `#1F1B16`, radius 12, padding 16, header strip bahasa + copy; horizontal scroll no-wrap. Syntax warm: keyword orange `#D97757`, string sage `#7FB069`, number gold `#E8B96F`, function periwinkle `#9DA4F2`.
- **Detail premium**: logomark asterisk 6-point HANYA di orange, tidak pernah warna lain.

### 1.3 Gemini (Google) — "Neural Expressive" (2026, M3 Expressive)

- **Direction**: pure black AMOLED + **deep blue gradient glow dari bawah layar** — "sense of depth and warmth without breaking minimal aesthetic".
- **Empty state**: greeting besar centered + logo gradient di atasnya; suggestion chips DIHAPUS (lebih bersih).
- **Composer**: **pill yang float/detach dari keyboard**, dark rounded pill: `+` kiri, mic, waveform button kanan.
- **Thinking indicator — paling ekspresif**: saat model bekerja, **setengah bagian atas layar glow gradient yang cycle warna logo Gemini**. Ini pengganti spinner — ambient feedback, bukan elemen UI kecil.
- **Live activity**: respons voice collapse jadi pill kecil menampilkan plain text.
- **Pelajaran untuk Hermes**: satu ambient gradient motion yang well-tuned > banyak animasi kecil. Tapi versi Android lebih flat dari iOS — glow di Android lebih subtle.

### Sintesis pola lintas app

| Aspek | Konsensus 2025–2026 |
|---|---|
| Assistant message | **Flat full-width**, bukan bubble. User boleh pill/bubble lembut |
| Reasoning | Collapsed by default ("Thought for Xs" / "thinking…"), tap expand |
| Thinking feedback | Bukan spinner: shimmer composer (ChatGPT) / caret accent (Claude) / ambient glow (Gemini) |
| Send/Stop | **Satu slot, morph antar state** — tidak pernah dua tombol |
| Suggestion chips | Maksimal 3–4, dismissible, tidak pernah menutup composer; Gemini malah menghapus |
| Warna dark | Bukan abu netral iOS: ChatGPT cool-neutral berstruktur, Claude warm-tinted, Gemini black+glow. Yang mana pun: **tone steps untuk depth, bukan shadow** |
| Accent | Saturated rendah, coverage kecil (<12% pemakaian warna) |

---

## 2. Design tokens konkret (rekomendasi untuk Hermes Mobile)

Dua opsi arah, keduanya terbukti di app top. **Rekomendasi: Opsi A (warm dark)** — paling membedakan dari "bawaan" dan paling cocok dengan selera "premium, presisi tinggi".

### 2.1 Palet dark — Opsi A: Warm Dark "ink & ember" (basis Claude dark)

```kotlin
// ui/theme/Color.kt — dark scheme
val Bg          = Color(0xFF1A1815)  // canvas utama — warm near-black, BUKAN #000 / blue-grey
val Surface1    = Color(0xFF232019)  // kartu, sheet, composer fill — tone step 1
val Surface2    = Color(0xFF2E2A22)  // pressed, chip aktif, hover — tone step 2
val Surface3    = Color(0xFF3A352B)  // FAB kecil/dialog — tone step 3 (highest)
val Stroke      = Color(0xFF3A352B)  // hairline divider 1dp (setara Surface3, netral)
val TextPrimary = Color(0xFFEDE7DC)  // cream, warm — bukan #FFFFFF
val TextSecondary = Color(0xFFB3AA98)
val TextTertiary  = Color(0xFF7E7666)  // placeholder, timestamp, hint
val Accent      = Color(0xFFD97757)  // terracotta — send button, streaming caret, link, active state
val AccentPressed = Color(0xFFBE6242)
val AccentSoft  = Color(0xFF4A352A)  // background chip "thinking…"/reasoning aktif
val AccentDim   = Color(0xFF8A5A3F)  // border reasoning card, progress track
val Success     = Color(0xFF7FB069)  // sage — tool sukses, sent
val Error       = Color(0xFFF14D42)
val CodeBg      = Color(0xFF14120E)  // code block — lebih gelap dari canvas, tetap warm
```

Aturan pemakaian (diambil dari disiplin Claude/ChatGPT):
- Accent **hanya** di: send button, streaming caret, active session indicator, link. Target < 10% area berwarna.
- Depth = tone steps (Bg → Surface1 → Surface2 → Surface3), **bukan elevation shadow**. Perbedaan tiap step ±5–6 luminance — cukup untuk terbaca, tidak sampai "abu-abu blok".
- TextPrimary cream `#EDE7DC` di atas `#1A1815` → kontras ~13:1; TextTertiary masih ~4.6:1 (AA untuk teks kecil).
- **Larangan**: cool blue-grey (`#1C1C1E`, `#2C2C2E`), pure black untuk surface besar (OLED black hanya untuk code block & keyboard scrim).

### 2.1b Alternatif Opsi B: Cool neutral "graphite" (basis ChatGPT) — kalau mau lebih tool-like
`Bg #171717, Surface1 #212121, Surface2 #303030, Surface3 #48484A, Text #EDEDED/#B4B4B4/#8F8F8F, Accent #A8C7FA (soft blue), AccentPressed #8AB4F8`. Catatan: lebih dekat ke yang sekarang → risiko tetap terasa "bawaan" lebih tinggi.

### 2.2 Type scale (sp, Exact) — dengan role-asymmetry

Pakai dua family: **sans untuk UI + user message, serif (mis. `Bitter`/`Source Serif` variable, atau `NIUDGW`-class) untuk assistant body**. Kalau tidak mau serif, minimal assistant = sans weight 400 + user = weight 500 dan bedakan warna — tapi serif adalah pembeda terbesar Claude.

```kotlin
val Type = Typography(
    // Assistant prose — serif, nyaman dibaca panjang
    bodyLarge = TextStyle(Serif, FontWeight.Normal, 16.sp, lineHeight = 25.sp), // 1.55 — assistant body, JANGAN < 16sp
    // User message + input composer — sans
    bodyMedium = TextStyle(Sans, FontWeight.Medium, 16.sp, lineHeight = 22.sp),
    // Timestamp, sender label, meta
    bodySmall  = TextStyle(Sans, FontWeight.Normal, 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    // Title screen / empty state greeting
    titleLarge = TextStyle(Sans, FontWeight.SemiBold, 22.sp, lineHeight = 28.sp),
    // Card title, session row title
    titleMedium = TextStyle(Sans, FontWeight.SemiBold, 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    // Chip, tombol, tab
    labelLarge = TextStyle(Sans, FontWeight.Medium, 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    // Caption/code header
    labelSmall = TextStyle(Sans, FontWeight.Medium, 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    // Code
    //  Mono 13.5.sp lineHeight 21.sp (JetBrains Mono / Fira Code)
)
```
Aturan: hierarchy lewat **weight + serif/sans**, bukan nambah typeface. Code block selalu mono 13–14sp di `CodeBg`.

### 2.3 Spacing — grid 4dp ketat

```kotlin
object Spacing {
    val Xs = 4.dp; val S = 8.dp; val M = 12.dp; val L = 16.dp
    val Xl = 24.dp; val Xxl = 32.dp   // HANYA 7 nilai. hapus semua nilai di luar ini
}
```
- Horizontal padding layar chat: 16–20dp (immersive, teks max ~68-72 char).
- Gap antar message beda role: 24dp; antar block dalam 1 message: 12dp.
- Composer: content padding 8dp, jarak ke edge layar 12dp, gap ke keyboard 8dp.

### 2.4 Corner radius — 3 step + pill (perbaiki 14 nilai acak sekarang)

```kotlin
val ShapeXs  = RoundedCornerShape(8.dp)    // chip, tag, code inline
val ShapeS   = RoundedCornerShape(12.dp)   // card, code block, tool row
val ShapeM   = RoundedCornerShape(20.dp)   // composer, sheet, user pill
val Pill     = RoundedCornerShape(999.dp)  // send button, chip pill, indicator
```
User pill asymmetric (optional, khas WhatsApp-iOS-modern): `RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)` — tail-corner 6dp mengarah ke sender.

### 2.5 Elevation strategy
- **Default 0.** Chat surface datar; depth dari tone steps.
- Composer & sheet: tonal elevation saja (Surface1/2) + hairline `Stroke` 1dp, bukan shadow.
- Dialog/FAB: maks `tonalElevation = Surface3` + shadow sangat lembut warm: `Color(0x0F000000)` blur 24 (opacity ~6%, referensi Claude `rgba(40,30,20,0.06)`).
- Satu-satunya shadow "berani": send button pressed (lihat motion).

---

## 3. Pola spesifik (implementasi konkret)

### 3.1 Typing indicator — state-aware, bukan 3 dot generik
- Saat send → sebelum token pertama: **skeleton baris assistant** (lihat 3.6) + label status yang update: "Thinking…" → "Reading files" → "Running command" (state-aware menaikkan toleransi tunggu — HelloFrontend/Koder).
- Style: pill `AccentSoft` bg, teks `labelSmall` `TextSecondary`, leading icon spinner 14dp `Accent`, tinggi 28dp.
- 3-dot hanya kalau status tidak diketahui; animasi: 3 dot 6dp, bounce translate -4dp, stagger 120ms, period 900ms, `MediumBouncy`.

### 3.2 Streaming text cursor
- **Caret inline** setelah karakter terakhir: blok 2×18dp `Accent`, radius 1dp, blink opacity 1.0→0.3→1.0 period **1000ms** (spesifikasi Koder Design); hilang dengan fade-out **100ms** saat `done`. Reduced-motion: caret statis tetap terlihat.
- Streaming markdown: **defer code block** — fence terbuka render sebagai placeholder dim sampai fence tertutup, baru syntax-highlight sekali (hindari re-highlight per token → jank).
- Token buffer flush ~16ms (1 frame) — batch sebelum re-render.

### 3.3 Reasoning / tool-call — collapse/expand
- Collapsed row (default): `[icon] Thought for 14s` atau `[icon] Searched web · 3 results` — teks `bodySmall` `TextSecondary`, chevron 12dp, tinggi row 36dp, bg transparent, tap target ≥48dp.
- Expanded: card `Surface1` radius `ShapeS` padding 12dp, isi mono/`bodySmall` `TextSecondary`, reasoning text **TIDAK pakai serif** (ini metadata, bukan prose).
- Live progress: leading icon spinner `Accent` + label berjalan; selesai → spinner berganti check `Success` 12dp, label freeze ke durasi final. Animasi collapse: `animateContentSize` spring lihat §4.
- Tool lifecycle states yang harus didukung UI: `input-streaming → input-available → running → output-available | error | denied` (pola MUI X / Vercel AI SDK).

### 3.4 Session row (info density tinggi, tanpa tombol)
Per baris (tinggi ~64dp, padding vertikal 12dp, hairline divider `Stroke` antar row, TIDAK ada card per row — flat list lebih presisi):
1. Baris 1: **judul** `titleMedium` `TextPrimary` ellipsize 1 baris (kiri) + **relative time** `labelSmall` `TextTertiary` (kanan, mis. "2h").
2. Baris 2: preview `bodySmall` `TextSecondary` max 2 baris ellipsize.
3. Baris 3 (meta, 1 baris, `labelSmall` `TextTertiary`): `model-name · N messages` + dot indicator `Accent` 6dp kalau stream aktif.
- Aksi: long-press → context menu (rename/delete/pin). Swipe-to-delete dengan background `Error` 20% + icon. **Tidak ada trailing icon button** (chrome minimal).
- Active session: leading bar 3dp `Accent` tinggi penuh row (bukan bg highlight besar).

### 3.5 Pull-to-refresh vs tombol
- Session list: **pull-to-refresh** dengan indicator custom kecil (arc 16dp `Accent`), karena list = data yang bisa stale. Pull threshold standar, snap-back spring `NoBouncy/StiffnessMedium`.
- Chat screen: **TIDAK ADA pull-to-refresh** (bisa bentrok dengan scroll history & jump-to-bottom). Refresh/stop = state tombol send (morph ke stop).
- **Jump-to-bottom chip**: muncul saat user scroll up >50dp dari bottom; pill `Surface2` 80% alpha + shadow lembut, icon arrow-down + unread count; scroll balik <10dp auto-hide. Autoscroll hanya saat pinned (≤60px dari bottom) — pola "pinned-to-bottom" standar industri.

### 3.6 Skeleton loading
- Session list first-load: 5–6 baris skeleton — rect `Surface1` radius `ShapeXs`, shimmer sweep `Surface2` 1200ms linear, tinggi meniru layout asli (title 40% width, preview 90%/70%).
- Chat history load: skeleton bubble assistant (2 baris) + user (1 baris, 60% width align kanan) — meniru struktur, bukan kotak abu generik.
- <300ms: jangan tampilkan apa pun (langsung crossfade ke konten).

---

## 4. Premium vs "bawaan" — motion, haptic, detail

### 4.1 Motion — spring specs exact (definisikan SEKALI sebagai MotionToken)

```kotlin
object Motion {
    // Preset — dipakai di seluruh app, jangan ad-hoc per layar
    val Snappy   = spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)        // 1.0f, 400f — default UI kecil
    val Gentle   = spring<Float>(Spring.DampingRatioNoBouncy, 190f)                          // sheet, panel masuk
    val Bouncy   = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)    // 0.5f, 400f — HANYA momen sukses/delight
    val Expressive = spring<Float>(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)        // 0.75f, 200f — empty state logo, glow
    val FadeIn   = tween<Float>(150, easing = FastOutSlowInEasing)
    val FadeOut  = tween<Float>(100)
}
```
Pemakaian wajib:
- **Send button**: scale 1→0.96 on press (`Snappy`), morph send→stop via `AnimatedContent` + rotation 90°; saat selesai → morph ke check 1x `Bouncy` + balik.
- **Message masuk**: assistant content `animateContentSize(Gentle)`; JANGAN slide-in tiap token.
- **Tool expand/collapse**: `animateContentSize(Gentle)` + chevron rotate 180° `Snappy`.
- **Ambient glow saat thinking** (opsional, khas Gemini): radial gradient `Accent` 8% alpha di bagian atas composer, breathe scale 1→1.05 loop 2s `Expressive`. Satu ambient motion > 10 animasi kecil.
- Durasi prinsip: feedback tap **<100ms** mulai; transisi standar 150–250ms; >250ms terasa laggy kecuali memang progress.
- `ReducedMotion`: semua spring → fade 100ms; caret statis.

### 4.2 Haptic — vocabulary, bukan satu getaran
```kotlin
// pakai LocalHapticFeedback / VibratorManager
Tap on session row      -> HapticFeedbackType.TextHandleMove   // paling ringan
Send message            -> LongPress (ringan sekali, sekali per send)
Stream complete         -> ContextClick + Segment tick  // "tick" sukses halus
Error / stream stop     -> ContextClick double-pendek
Tool expand/collapse    -> TextHandleMove
Long-press context menu -> LongPress
```
Aturan: ringan untuk aksi frekuen, lebih berat untuk perubahan state penting (kompresi input transaksi → tab switch). Haptic reserve — kalau semua berat, semua jadi noise.

### 4.3 Detail kecil yang mindahin persepsi (highest ROI, urut)
1. **Press state semua yang tappable**: scale 0.98 + Surface2 overlay — di frame yang sama dengan touch-down, sebelum navigasi. Ini #1 pembeda "fast app feel" (Doherty threshold 100ms).
2. **Streaming caret berwarna accent** — Claude membuktikan cursor bisa jadi signature.
3. **Warm-tinted neutrals** — satu keputusan (`#1A1815` vs `#1C1C1E`) yang mengubah seluruh mood; user tidak tahu kenapa tapi terasa "authored".
4. **Hairline dividers 1dp + tone steps** menggantikan card/outline di mana pun bisa — makin sedikit border, makin premium.
5. **Serif untuk assistant** — pembeda instan dari semua app chat bawaan.
6. **Copy button muncul dengan fade 100ms** saat stream selesai di block code, bukan selalu ada.
7. **Empty state greeting `titleLarge` + satu motion halus** (logo breathe) — tanpa suggestion chip carousel.
8. **Time-relative label live update** ("now" → "2m") tanpa refresh.
9. Icon set konsisten 1.5dp stroke (mis. Lucide/Phosphor thin) — jangan mix filled + outline.
10. Status bar transparen + canvas menembus edge-to-edge; composer docked dengan `imePadding()`, keyboard push tanpa jump.

### 4.4 Anti-pattern yang harus dihindari (dari riset)
- Bubble SMS dengan tail untuk assistant (undermines tool framing).
- Spinner global saat thinking — pakai state-aware label / shimmer / glow.
- Tombol Stop terpisah dari Send, atau di overflow menu — harus 1-tap di slot send.
- Pull-to-refresh di dalam chat screen.
- Skeleton kotak generik — harus meniru layout akhir.
- Animasi >300ms untuk feedback biasa; bounce di semua tempat (bounce = rationed).
- Cool blue-grey dark + accent blue satur = "template iOS" (persis kondisi sekarang).

---

## 5. Referensi

1. OpenAI Developers — Apps SDK UI guidelines & design guidelines (display modes, composer shimmer "Thinking" pattern): developers.openai.com/apps-sdk/concepts/ui-guidelines, /design-guidelines
2. Setproduct — "Designing AI chat interfaces: Anatomy, patterns, pitfalls" (May 2026; flat-vs-bubble konsensus, docked composer, a11y streaming): setproduct.com/blog/ai-chat-interface-ui-design
3. Claude iOS DESIGN.md (Meliwat/awesome-ios-design-md) — full token: warm palette, Tiempos/Styrene, caret orange, shadow philosophy, code block spec: github.com/Meliwat/awesome-ios-design-md
4. OpenDesigner + DesignMD — Claude design system (parchment, terracotta #c96442/#cc785c, warm neutrals, ring shadows): opendesigner.io/design-systems/claude, designmd.co/d/claude
5. ChatGPT design tokens mined (neutrals #212121/#303030/#48484a, accent #a8c7fa, radius 999px, elevation minimal): source--design.vercel.app/designs/chatgpt
6. Material 3 color system (dark tone stops, surfaceContainer family, tone-based elevation): hex-color.net/en/blog/material-design-3-how-the-new-color-tokens-work + m3.material.io
7. Android Authority / AndroidSage / 9to5Google — Gemini "Neural Expressive" redesign 2026 (pure black + bottom glow, pill composer, thinking glow): androidauthority.com/gemini-neural-expressive-android-app-hands-on-3668985, androidsage.com/2026/05/15/gemini-v1-0-91-apk
8. HelloFrontend — "AI Chat UI Patterns" (pinned-to-bottom 60px buffer, state-aware indicator, send/stop satu slot, optimistic send): hellofrontend.com/frontend-ai-interview/chat-ui-patterns
9. Koder Design System — AI streaming text spec (caret blink 1000ms, fade-out 100ms, defer code fence, autoscroll escape 50px/10px, stop 1-tap ≥48dp): kds.koder.dev/en-US/reference/ai-ui-streaming-text.html
10. MUI X Chat streaming protocol (tool/reasoning chunk lifecycle states): mui.com/x/react-chat/behavior/streaming
11. Compose SpringSpec constants (DampingRatio NoBouncy=1.0/Medium=0.5/Low=0.75, Stiffness Low=200/Medium=400/High=1000) — AOSP source + SpringSpec gist: android.googlesource.com, gist.github.com/jacksonmafra-umain
12. Micro-interactions premium (press <100ms, scale 0.98, haptic vocabulary, skeleton mirrors layout, motion as tokens 150–250ms): baxchain.com/blogs/micro-interactions-that-make-apps-feel-premium, novaqube.com/blog-high-craft-mobile-design, dolfy.ai/blog/micro-interactions-small-animations-apps-feel-built
