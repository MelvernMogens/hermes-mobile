# Hermes Mobile — DESIGN.md ("Quiet Mono", M8)

Hitam-putih, tenang, rapi — WhatsApp/iMessage tapi lebih dewasa. Konten dulu,
chrome hampir tak terlihat. Warna HANYA untuk makna (status), bukan hiasan.
Rasa referensi: Linear mobile, iMessage dark, Things.

Sumber kebenaran kode: `app-android/app/src/main/java/id/melvern/hermesmobile/ui/theme/`
(Color.kt `Ink`, Type.kt `Type`, Dimens.kt `Dim`/`Radius`/`hairline()`, Motion.kt `Motion`,
Theme.kt ripple). **Nol hex / sp / dp ajaib di screen** — semua lewat token.

## 1. Warna (`Ink`)

| Token | Hex | Pakai |
|---|---|---|
| Bg | #0B0B0C | canvas, top bar, composer container |
| Surface1 | #141416 | composer field, sheet, code block body, banner, card |
| Surface2 | #1C1C1F | user bubble, pressed row, chip, inline code, scroll-to-bottom |
| Surface3 | #26262A | code block header, field focus bg |
| Hairline | #232326 | divider (1dp; 0.5dp kalau density ≥ 3) |
| HairlineStrong | #3A3A3F | border field idle, garis blockquote, outline send redup |
| Text | #F2F2F3 | primary (18.1:1 di Bg) |
| Text2 | #A1A1A6 | secondary: meta, preview (7.65:1) |
| Text3 | #808086 | tertiary: timestamp, placeholder (5.01:1 Bg / 4.69:1 Surface1) |
| Accent | = Text | send aktif, tombol Connect |
| OnAccent | = Bg | ikon/teks di atas fill putih |
| Live | #34C759 | running / streaming / connected |
| Warn | #FF9F0A | read-only, reconnecting |
| Danger | #FF453A | error, delete |
| Scrim | black 60% | di belakang semua sheet |

- Text3 menyimpang dari brief (#6C6C72 = 3.77:1, fallback #7A7A80 = 4.31:1 di
  Surface1 → dua-duanya gagal 4.5:1 untuk placeholder composer). #808086 lolos keduanya.
- Semantik HANYA sebagai dot 8dp, ring 2dp avatar running, atau teks status kecil.
  Tidak pernah fill besar. Tidak ada hijau-aksen, ungu, amber dekorasi.

## 2. Tipografi (`Type`)

Inter v4.1 (OFL, `docs/licenses/Inter-OFL.txt`) — Regular/Medium/SemiBold/Bold di `res/font`.
JetBrains Mono HANYA untuk code, inline code, path. Semua sp (ikut font scale sistem).

| Style | Ukuran | Pakai |
|---|---|---|
| Display | 28/34 SemiBold, −0.4 | large title "Chats", judul Connect |
| Title | 17/22 SemiBold, −0.2 | header chat, nama session, judul sheet, heading markdown |
| Body | 16/24 Regular | prosa assistant, bubble user, input composer |
| Callout | 15/20 Regular | preview baris 2, isi sheet, banner, card |
| Meta | 13/18 Regular (MetaMedium) | model line, waktu list, label kecil, tool row |
| Caption | 12/16 Medium | timestamp bubble, badge, day chip |
| Button | 16 SemiBold | tombol solid Connect |
| Mono | 13/19 | body code block |
| MonoMeta | 12/16 | nama bahasa di header code block (lowercase) |

**Aturan:** nol uppercase + letterSpacing lebar. Sentence case di semua UI copy (English).

## 3. Ikon

`material-icons-extended`, **Icons.Rounded.\*** saja (+ `AutoMirrored.Rounded.ArrowBack`).
Tidak ada emoji/unicode sebagai ikon. Standar 22dp (`Dim.Icon`) dalam target 48dp;
16dp di baris tool/chevron model; 12dp status di bubble.
attach = Add · send = ArrowUpward · stop = Stop · copy = ContentCopy → Check ·
back = ArrowBack · model = ExpandMore · new chat = Edit · search = Search ·
more = MoreVert · file = Description · image = Image · tool: Terminal / Search / Description / Build.

## 4. Radius & spacing

Grid 4dp. Margin layar 16dp. Radius: 6 inline code · 8 thumbnail · 10 chip/code block ·
12 field + tombol Connect · 14 card approval · 18 bubble/composer (user: kanan-bawah 6) ·
22 sheet atas · full avatar/send. Semua avatar lingkaran, ukuran per konteks:
32 top bar · 44 row session · 40 sheet · 56 empty chat.

## 5. Komponen

**Sessions ("Chats")** — top bar: avatar profil 32 + dot status (live/warn/danger) + nama profil
meta; kanan Search (filter lokal inline) + Edit (new chat). Large title "Chats" collapse jadi
title 17 di bar saat scroll; hairline muncul saat scroll. Subtitle hanya kalau ada masalah
("Reconnecting…", "Offline — tap to retry"). Row min 72dp, padding 16h/12v: avatar 44
(ring 2dp live saat running) · title 1 baris · preview 1 baris (kosong → "Desktop · 509 messages")
· waktu relatif kanan atas (18:54 / Yesterday / Mon / 15 Sep) · "Running" live kanan bawah.
Divider inset 72dp. Pressed = Surface2. Section "Hidden" (meta text2). Empty: ikon 48 +
"No chats yet" + "Start one from the pencil icon". Loading: 3 skeleton row shimmer.
Tidak ada FAB / tombol besar.

**Chat** — top bar 56: back · avatar 32 · title + model line (ExpandMore → model sheet) atau
"Working…" + dot live pulse 1.2s · MoreVert (rename/branch/hide/delete/copy id). Hairline
hanya saat scroll. Read-only: strip 36dp Surface1, dot warn + "Open on desktop — view only" + "Retry".
User bubble kanan max 80% Surface2, padding 12h/9v, timestamp caption di dalam; Queued = ikon
Schedule + "Queued"; tidak ada centang. Assistant tanpa bubble, full width. Markdown: heading
Title, list indent 20, link underline warna text (long-press copy), blockquote garis kiri 2dp
HairlineStrong + text2, inline code mono 14 Surface2 radius 6. Code block: Surface1 radius 10
border hairline, header 32 Surface3 (bahasa lowercase mono + Copy→Check 1.5s), body mono 13/19
scroll horizontal. Tool: row 32dp ikon 16 + "Ran terminal"/"Read file" + chevron, expand → code
block (args + output); berurutan → "Used N tools". Thinking: "Thinking…" shimmer → "Thought for Ns".
Day chip "Today"/"Yesterday". Scroll-to-bottom 36dp Surface2 + badge, hanya saat > 1 layar di atas.
Empty: avatar 56 + "Chat with default" + model line. Loading: 3 skeleton blok.

**Composer** — container Bg + hairline atas, imePadding. Add 22 (target 44) → sheet Photo/File.
Field Surface1 radius 18, min 44, maks 6 baris, placeholder "Message" / "Reply or queue a message".
Tombol 36: kosong = outline redup; ada teks = putih + ArrowUpward hitam; running & kosong = Stop.
Attachment chip: thumb 40 radius 8 / ikon file + nama + X.

**Approval / clarify** — Surface1 radius 14 border hairline, judul Title, isi Callout, tombol
teks kanan: "Deny" text2, "Allow" text SemiBold.

**Sheets** — ModalBottomSheet: Surface1, atas 22, handle 32×4 Text3, scrim 60%.
Profiles: row 64 avatar 40, nama Title, baris 2 model (meta), Check untuk aktif; "Show hidden
chats" Switch (thumb putih, track putih 30%) di bawah dengan divider. Model: row + provider meta,
Check aktif, footer "Model applies to new chats".

**Connect** — terpusat optik, max 420. Logo 56 · "Connect to your Mac" (Display) · sub Callout
text2. OutlinedTextField radius 12 label mengambang: Server address (auto `https://`), Username,
Password (masked + Visibility). Border idle HairlineStrong, fokus Text 1.5dp. Tombol Connect
50dp putih teks hitam (satu-satunya tombol solid); loading = spinner 18 hitam + "Connecting…"
(tetap putih). Error Danger Callout di bawah: unreachable → "Can't reach your Mac. Check
Tailscale is on (same account on both devices)."; 401 → "Wrong username or password.";
lain → "Couldn't connect (HTTP 502). Is Hermes running on your Mac?". Tanpa footnote install.

## 6. Motion (`Motion`)

Satu keluarga, halus. Nav list→chat shared axis X: slide 24dp + fade, 220ms emphasized
(back = kebalikan). Pesan baru: fade + translateY 8→0, 180ms; streaming = append tanpa animasi.
Sheet = default M3. Ripple putih 8%. `ANIMATOR_DURATION_SCALE == 0` → semua motion mati
(nav, pulse, shimmer, enter).

## 7. System chrome

`enableEdgeToEdge` (bar transparan, ikon terang). Splash (values-v31) bg #0B0B0C + logo
foreground; < 31 windowBackground gelap. Adaptive icon background #0B0B0C.
