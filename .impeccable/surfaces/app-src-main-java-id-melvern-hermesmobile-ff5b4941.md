---
version: 1
slug: "app-src-main-java-id-melvern-hermesmobile-ff5b4941"
primary_target: "app-android/app/src/main/java/id/melvern/hermesmobile"
related_targets: []
---

# Surface brief — Sessions & Chat (primary surface)

## Scope
Redesign visual world seluruh app Hermes Mobile: session list + chat + composer
+ connect. Mode: Operate (task-first, ekspresi lewat Material 3 theming).
Platform android (reference android.md: M3 rules, 48dp targets, sp units).

## Direction contract (Fillmore Handbill — chosen by user 27 Sep)

THESIS: Setiap session adalah gig satu malam — poster Fillmore 1966-71:
deep indigo ground, vermillion accent yang CUMA untuk aksi/hidup
(streaming, send, session aktif), cream paper untuk prosa assistant.
Refuse: template chat cool blue-grey + bubble SMS.

OWN-WORLD: Indigo-navy canvas `#131A33`→`#1B2A52` tone steps (bukan
abu netral), paper-cream assistant prose pakai display serif, UI chrome
sans grotesk condensed-tracking, list = kolom handbill: judul display
type besar + bill-date kecil, chat = belakang panggung yang tenang.
Ornamen: NO. Energi dari type + 2 warna + ink texture halus.

STORY: User buka app → session list yang kerasa seperti rak poster gig
bulan ini (judul besar, tanggal bill, dot vermillion kalau lagi jalan) →
tap → masuk "backstage": prosa cream serif nyaman dibaca, tool activity
= crew list kecil, composer = tiket strip di bawah.

FIRST VIEWPORT (Sessions): top bar tipis "HERMES" condensed-tracking
cream kecil + tanggal bill hari ini kanan; di bawahnya langsung 3-4
session card-type: judul session display serif italic-ish 22-24sp cream
(1 baris), baris meta sans 11sp tracking lebar indigo-muted "model · N
pesan · 2h", running dot vermillion 6dp + glow 8%. Divider hairline
indigo terang 1dp antar row. FAB baru: BUKAN — "New session" = baris
paling bawah list, dashed border vermillion, teks condensed "NEW GIG".
Empty state: judul display besar "TONIGHT" + sub "belum ada gig".

FORM: posisi 1 dari 7 kandidat grounded, seed key eeb367e8.

FINISH: unreviewed and undocumented is unfinished; this build ends with
the finish review, the verdict, DESIGN.md, and every shipping raster
carrying its provenance.
