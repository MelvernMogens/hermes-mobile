---
version: 1
slug: "app-src-main-java-id-melvern-hermesmobile-ff5b4941"
primary_target: "app-android/app/src/main/java/id/melvern/hermesmobile"
related_targets: []
---

---
version: 2
slug: "app-src-main-java-id-melvern-hermesmobile-ff5b4941"
primary_target: "app-android/app/src/main/java/id/melvern/hermesmobile"
related_targets: []
---

# Surface brief — whole app (v28 "Control Room" redesign)

## Scope
Replacement visual world for the whole Hermes Mobile app (Chats, Chat, Agents,
Files, Mac, Settings, sheets, Connect). Mode: Operate. Platform: android (M3
structure, 48dp targets, sp units). Visitor = Melvern, alone, on an S23 Ultra /
Tab S8, away from his Mac, checking on an agent + 5 bots that keep working on
the Mac while he is out.

Pinned by the user (binding, beats the roll): black & white only; colour only
for status (green running, amber waiting/warning, red error) + user-picked
group colours on group dots/monograms; English UI copy; no big buttons, no
spaced-out uppercase labels, real icons (no emoji); chat must still read like
WhatsApp (bubbles right, prose left); dark theme; "unique, not a basic app".

## Grounded candidates (by resonance)
1 video-edit timeline (CapCut/Premiere tracks + playhead) · 2 Nothing OS
dot-matrix glyphs · 3 broadcast control room (multiview wall, tally lamps,
under-monitor labels, timecode, as-run log, level meters) · 4 dark-cockpit
avionics · 5 film contact sheet · 6 Teenage Engineering field gear · 7 Braun
device face. Rut kept out: WhatsApp clone (category default) and neon hacker
terminal (predictable opposite).

## Direction contract (Control Room — roll bd9c466e, assigned #3)

THESIS: The phone is the control room for a studio that never stops — every
chat and bot is a live feed, status is a tally lamp, time is timecode.
Refuses the generic chat-app list with green dots and a stats dashboard of
same-size cards.

OWN-WORLD: Black glass. Pure-black canvas, monitor tiles = near-black glass
with a 1px bezel hairline and a darker under-monitor label strip. Status is a
TALLY LAMP (12x4dp pill, unlit = dark slot; lit = green/amber/red with soft
bloom) — the one status atom app-wide, replacing dots. Durations and clocks in
Geist Mono tabular timecode (00:32:14) that ticks live while running. Level
meters = segmented bars (lit white, amber/red only inside warning zones).
Controls = small key caps (1px border, white "lit" fill only for THE primary
action of a screen). Geist sans for everything else, sentence case.
Brightness = activity (dark cockpit rule): idle things sit dim, live things
are bright.

STORY: Open app -> the rundown: chats in order, and when anything is working a
Live-now rail of mini monitors shows exactly what each is saying right now.
Agents is the multiview wall: one monitor per bot, live tiles lit, idle tiles
dim, the as-run log of every task below. Mac is the equipment rack: lamps,
meters, keys. Files is the clip bin. Nothing to decode; you glance and know.

FIRST VIEWPORT (Chats): top bar = profile avatar + "Chats" title + a signal
cluster at right (Mac lamp + search + new). If anything runs: a Live-now rail
(160x92dp mini monitors, horizontally scrolling, lamp + live timecode in each
label strip). Then the rundown rows: avatar 48 (group colour exception), title
+ preview, right column time in mono tabular + lamp when running. Offline /
connecting states replace the signal cluster with a full-width signal line
(lamp + plain words). No FAB.

FORM: broadcast control room, position 3 of 7 grounded candidates, seed key
bd9c466e.

RAISES (named, from declined/competitive challengers):
- From the split-flap concourse (competitive): status changes animate as a
  short per-character flip on the status word + timecode, rows ranked by time.
- From the Saville factory catalog: every bot task gets a running catalog
  number (#041) used in the log, the chat header and the "finished"
  notification.
- From the doujin event catalog: the Files bin packs thumbnails edge to edge,
  3-up, no cards, tiny code labels.
- From the Crouwel grid: one fixed column grid for every log/rundown list
  (time column, lamp column) so columns line up across screens.
- From the monochrome product canon: white fill is rationed — one lit key per
  screen at most.
- From the transit diagram: focus the chosen feed while keeping the others
  legible (tablet two-pane and multiview never shrink to unreadable).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
