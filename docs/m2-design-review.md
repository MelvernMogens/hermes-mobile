# M2 Design Review — Fillmore Handbill (independen @designer)

Tanggal: 27 Sep 2026 · Scope: semua Kotlin di `ui/` (6 file) · Mode: review only, gak ada kode yang diubah.
Acuan: `.impeccable/surfaces/app-src-main-java-id-melvern-hermesmobile-ff5b4941.md` (direction contract), `PRODUCT.md`, comp approved `.impeccable/mocks/comp-2-chat.webp` (diverifikasi pixel-sampling: composer strip comp memang ber-vermillion — border + send — jadi itu bukan pelanggaran).

Metode: baca 6 file + Models.kt + MainActivity.kt, audit kontra contract & preferensi user, audit comp via script PIL (coverage vermillion comp = 0.58% total, 5.49% di band composer), 1 subagent reviewer independen (glm-5.3) — temuannya kuverifikasi ulang: 2 digugurkan (border composer ✗ karena comp approved memakainya; `s.model` ✗ karena field gak ada), 2 ditembus sendiri (stub dashedBorder, unresolved SimpleDateFormat).

## Temuan (urut dampak persepsi)

| # | file:baris | masalah | perbaikan konkret |
|---|---|---|---|
| 1 | `SessionsScreen.kt:121` (dipakai di :103) | `dashedBorder()` adalah stub no-op murni (`this.then(Modifier)`) — kontrak literal "NEW GIG = dashed border vermillion" gak jalan sama sekali; row-nya polos, user nampil kek list biasa. | Implementasi beneran: `Modifier.drawBehind { val w=1.5.dp.toPx(); drawPath(Path().apply{ addRoundRect(RoundRect(0f,0f,size.width,size.height,CornerRadius(12.dp.toPx()))) }, color=F.Vermillion, style=Stroke(width=w, pathEffect=PathEffect.dashPathEffect(floatArrayOf(10f,8f),0f))) }` — dash 10px / gap 8px, stroke 1.5dp vermillion, radius 12dp |
| 2 | `SessionsScreen.kt:104,:126` (import :27 unused) | Motion token `pressClickable` (scale 0.97, no ripple — kontrak motion dunia Fillmore) gak dipakai: NEW GIG row & session row pakai `.clickable` default = ripple Material. | Ganti `.clickable { ... }` → `.pressClickable { ... }` di kedua row (import-nya udah ada, tinggal pakai); hapus import `androidx.compose.foundation.clickable` kalau tak terpakai lagi |
| 3 | `SessionsScreen.kt:149` | `SimpleDateFormat` tanpa `import java.text.SimpleDateFormat` → unresolved reference, gak akan compile. `LocalDate`+`DateTimeFormatter` diimport (:30-31) tapi nganggur. | `private fun billDate(): String = DateTimeFormatter.ofPattern("EEE · d MMM", Locale("id")).format(LocalDate.now()).uppercase()` — dan hapus 2 import nganggur |
| 4 | `SessionsScreen.kt:64,:116` | Tracking dobel: `"H E R M E S"` dan `"+  N E W   G I G"` pakai spasi manual DI ATAS `labelLarge` yang udah `letterSpacing = 3.sp` — jarak antar huruf jadi liar, wordmark keliatan rusak di layar utama. | `Text("HERMES", ...)` dan `Text("+ NEW GIG", ...)` — biarkan letterSpacing 3.sp style yang kerjain tracking |
| 5 | `ChatScreen.kt:330` | Glyph teks `"▲"` dipakai sebagai icon send — kontra brand "tanpa emoji-icon; Material icons vector", dan glyph ini beda bentuk/weight per device. | `Icon(Icons.AutoMirrored.Filled.Send, null, tint = F.Cream, modifier = Modifier.size(18.dp))` + import `androidx.compose.material.icons.Icons` dan `androidx.compose.material.icons.automirrored.filled.Send` |
| 6 | `Theme.kt:71` | `labelSmall = 10.sp` vs kontrak meta line 11sp — SEMUA meta (baris session, tool label, notice) kekecilan 1sp dari spec. | `fontSize = 11.sp, lineHeight = 16.sp` (letterSpacing 1.6.sp dipertahankan) |
| 7 | `SessionsScreen.kt:137` | Meta `"N MSG · 2h"` nyimpang dari kontrak `"model · N pesan · 2h"` — model gak muncul, "MSG" english pas UI-nya Indonesia. CATATAN: field-nya `s.profile` (bukan `s.model` — itu gak ada di SessionRow, bakal compile error). | `"${s.profile ?: "hermes"} · ${s.messageCount} pesan · ${Fmt.timeAgo(s.updatedAt ?: s.startedAt)}"` dan hapus `.uppercase()` biar literal ke kontrak |
| 8 | `SessionsScreen.kt:143` (dot 8dp) + `ChatScreen.kt:162` (dot 7dp) + `Color.kt:30` | Running dot: ukuran inkonsisten antar screen (8 vs 7dp, kontrak 6dp) dan glow 8% gak ada. Token `VermillionSoft` dibikin "15%" tapi gak pernah dipakai. | Dua lokasi: `Box(Modifier.size(18.dp).clip(CircleShape).background(F.Vermillion.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) { Box(Modifier.size(6.dp).clip(CircleShape).background(F.Vermillion)) }`; samakan `VermillionSoft = Color(0x14E05000)` (8%) |
| 9 | `ChatScreen.kt:308` | Send button `.size(44.dp)` — di bawah touch target minimum Material 3 (48dp); ini tombol paling sering dipencet di app. | `.size(48.dp)` (tinggi composer 64dp masih muat, `padding(horizontal = 24.dp)` → `20.dp` biar proporsional) |
| 10 | `ConnectScreen.kt:30-31` | Gak ada `.statusBarsPadding()` (edge-to-edge, konten nempel status bar) + gutter `horizontal = 24.dp` beda dari 20.dp di Sessions/Chat. | `Modifier.fillMaxSize().background(F.Bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)` (background eksplisit biar konsisten idiom dua screen lain) |
| 11 | `ConnectScreen.kt:61-90` | `Button` filled vermillion full-width `height(52.dp)` = "tombol gede" yang user benci + ripple default, bukan motion token. | `OutlinedButton(modifier = Modifier.fillMaxWidth().height(48.dp).pressClickable {...}, border = BorderStroke(1.5.dp, F.Vermillion), colors = ButtonDefaults.outlinedButtonColors(contentColor = F.Cream))` — teks "HUBUNGKAN" labelLarge, vermillion tinggal outline (aksi), bukan blok |
| 12 | `SessionsScreen.kt:84-86` | Empty state `"tonight"` + `"belum ada gig — mulai dari bawah"` vs kontrak literal `"TONIGHT"` + `"belum ada gig"` — ini layar pertama user baru, presisi literal = audit point. | `Text("TONIGHT", style = MaterialTheme.typography.titleLarge)` + `Text("belum ada gig", style = MaterialTheme.typography.bodySmall, color = F.LavenderDim)` |
| 13 | `Theme.kt:102` | `onError = Color.White` — satu-satunya hardcode warna di luar object `F.*` (kontras-nya juga kejam banget di ground indigo). | `onError = F.Cream` |
| 14 | `SessionsScreen.kt:61,:101` vs `ChatScreen.kt:150,:175` | Spacing off-grid & inkonsisten antar screen: header Sessions `vertical = 14.dp` vs Chat `12.dp`; NEW GIG `vertical = 14.dp`; chat list `spacedBy(18.dp)`. | Samakan: header & NEW GIG `vertical = 12.dp`, chat list `Arrangement.spacedBy(16.dp)` — semua kelipatan 4dp |
| 15 | `Color.kt:14` | Token `BgDeep` diberi komentar "statusbar/scrim bawah" tapi gak pernah direferensikan — status bar area cuma kena `F.Bg`, scrim composer juga gak ada. | Pakai di `MainActivity.kt:41`: `Box(Modifier.fillMaxSize().background(F.BgDeep))` (atau hapus token biar gak menyesatkan) |

## Yang udah BETUL (jangan diutak-atik)
- Ground indigo `#131A33` + tone steps `#1B2A52/#24305C/#2E3B6E`, depth tanpa shadow — persis kontrak.
- Dua voice type: prose serif italic (assistant) vs sans tracked (chrome) — signature Fillmore jalan.
- Vermillion coverage implementasi tetap jauh di bawah 10% (dots + caret + send + border composer), dan border vermillion composer ternyata SESUAI comp approved.
- Tanpa FAB, NEW GIG = baris teks — hormat ke "benci tombol gede".
- Composer ticket (notch + morph send→stop satu slot) — ide bagus, on-contract.
- System back via NavHost default — gak perlu BackHandler custom.

## Verdict
Kerangka dunia Fillmore udah 80% benar dan on-contract — sisanya presisi literal: dashed border yang ternyata stub, motion token yang lupa dipasang, tracking dobel, dan 1 compile error — semua fix kecil, gak ada perombakan arah.
