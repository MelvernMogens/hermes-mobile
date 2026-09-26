# M2 — catatan desain UI (dari teardown Hermex + riset)

Sumber: teardown Hermex Android (subagent, 26 Sep) + preferensi Melvern
(dark, immersive, chrome minimal, benci tombol gede, presisi literal).

## Anti-pattern Hermex yang TIDAK boleh kebawa ke app ini
1. Glyph Unicode mentah (`⌕ ‹ ⋯ ⌂ ↑`) — ganti **Material Icons** vector di M2.
   (M1 masih pakai `↑ ‹ ⟳ +` — hutang teknis yang harus dibayar duluan di M2.)
2. Dua aksen bersaing (emas brand vs biru Material) — kita: SATU aksen
   (`Accent` nous-blue terang) + cream terbatas untuk aksen hangat mikro.
3. Composer 2 baris + pill pill-an — kita: 1 baris; kontrol sekunder
   (model/reasoning/workspace) masuk sheet, bukan chrome permanen.
4. Judul top bar padding hardcode — pakai layout slot beneran.
5. Glass/blur 18-24dp di atas hitam — boros GPU, efek nyaris nol. Skip.
6. Utility rows makan setengah layar pertama — session list langsung konten.
7. Bubble user `#1C1C1E` di atas bg hitam (kontras nyaris 0) — kita pakai
   `UserBubble #1D2735` (kebiruan, terlihat).
8. Monolit ChatRoute 6858 baris — pecah per-modul dari awal
   (components/Composer.kt, components/TranscriptItem.kt, dst).

## Yang layak diadopsi dari Hermex
- Slash-autocomplete + saran workspace (debounce ~160ms).
- Context-window ring + reasoning-effort pill (di sheet sekunder).
- Skeleton loading + status "viewing cached data / reconnect to send".
- Aksi pesan: copy / fork-from-here / regenerate / TTS listen.
- testTag + contentDescription di semua komponen (testing + a11y).
- Swipe action di session list.

## Target M2 (urutan)
1. Material Icons (hapus semua glyph Unicode).
2. Markdown render (bold/code/list) + code block copy button.
3. Reasoning collapsible (thinking.delta sudah mengalir, tinggal render).
4. Approval/clarify cards (server-ask `approval` — respond via RPC;
   UI: card inline dengan tombol Approve/Deny 36dp).
5. Session list: search bar collapse + swipe delete/pin.
6. Composer sheet sekunder (model/effort/context ring).
