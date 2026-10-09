# Hermes Mobile — DESIGN.md ("Control Room", v28)

The phone is the control room for a studio that never stops: every chat and
bot is a live feed, status is a tally lamp, time is timecode. Black and white
only; colour is reserved for status (and user-picked chat-group colours).
Brightness = activity (dark-cockpit rule): idle things sit dim, live things
are bright. One lit (white) key per screen at most.

Code source of truth: `app-android/app/src/main/java/id/melvern/hermesmobile/ui/theme/`
(`Color.kt` Ink, `Type.kt` Type, `Dimens.kt` Dim/Radius/hairline, `Motion.kt`) and the
atoms in `ui/components/ControlRoom.kt`. **No hex / sp / dp literals in screens** —
everything goes through tokens. `Ink` surface colours are getters (Black / Graphite theme).

## 1. Colour (`Ink`) — Black theme values (Graphite one step lighter)

| Token | Hex | Use |
|---|---|---|
| Bg | #000000 | canvas |
| Surface1 | #0E0E10 | rack units, composer well, signal line |
| Surface2 / Surface3 / Raised | #17171A / #212125 / #2A2A2F | bubbles, pressed, selected key face |
| Frame | #1A1A1F | monitor body (bezel around screen + label strip) |
| Glass / GlassSheen | #030304 / #0C0C0F | recessed monitor screen (vertical gradient) |
| Bezel | #232327 | 1dp outline of monitors, rack units, strips |
| KeyFace / KeyBezel | #151518 / #2F2F35 | key caps |
| LampOff | #1E1E22 | unlit lamp slot, unlit meter cell |
| Text / Text2 / Text3 / Text4 | #F5F5F4 / #9E9EA4 / #7C7C83 / #5E5E65 | primary → faintest |
| Live | #3DD68C | running / on air |
| Warn | #F5A524 | waiting for the user, connecting, stopped |
| Danger | #F2555A | offline, failed, stop key square |

Colour appears ONLY as: a tally lamp, a status word next to its lamp, a monitor
bezel for WAIT/FAULT, meter cells past 80% / 95%, the group colour exception.

## 2. Type (`Type`) — Geist + Geist Mono (OFL)

Display 30/36 SemiBold (screen titles) · Title 17/22 · RowTitle 16/22 Medium ·
Body 15.5/24 · Callout 15/20 · Preview 14/19 · Meta 13/18 · Caption 12/16 ·
Section 13/18 Medium (sentence case, never uppercase+tracking).
Mono only for code and MEASUREMENTS: `Timecode` 12/16 Medium (H:MM:SS, always —
reads as elapsed, never as a clock), `TimecodeLarge` 15/20, `Catalog` 11/14
(slate labels, model tags), `Mono` 13/20 code.
Control Room additions: `Key` 13/16 Medium (key legends), `RackLabel` 13/18
SemiBold, `Umd` 12/16 Medium, `MonitorTitle` 14/18 SemiBold.

## 3. Atoms (`ui/components/ControlRoom.kt`)

- **TallyLamp** — 11×5dp LED segment (corner 1.5dp). Unlit = dark slot with 1dp
  KeyBezel outline; lit = status colour + soft radial bloom. LIVE breathes
  (alpha, 1.2s) via a deferred read — redraws its layer, never recomposes. The
  ONLY status atom app-wide (replaces dots/pills). Tally: OFF / LIVE / WAIT / FAULT / CUE (white = unread / selected).
- **TallyLabel** — lamp + status word in the lamp colour; the word changes with a
  split-flap drop (FlipText, 180ms).
- **LiveTimecode** — ticks every second from a real start epoch; null start →
  renders nothing (never a fake 0:00:00).
- **SegmentMeter** — 24 cells, 2dp gaps, lit cells white, amber > 80%, red > 95%;
  fill animates 450ms.
- **KeyCap** — 36dp min height, radius 8, 1dp bezel. States: default (dim
  legend), `selected` (raised face, bright bezel — filters/segments), `lit`
  (white face, black legend — the ONE primary action), `armed` (amber bezel —
  waiting for a confirm tap), disabled.
- **MonitorTile** — Frame body (radius 12, 1dp bezel, 4dp inset) → recessed Glass
  screen (radius 8, top-edge shadow, faint scanlines) → label strip (UMD) on the
  frame: lamp · status word · timecode. Idle monitors dim to 55%.
- **SignalLine** — full-width Surface1 strip under a top bar: lamp + plain words
  + optional key word ("Retry", "Review", "Turn on"). Shown ONLY when something
  needs attention (offline, connecting, requests waiting, notifications off).
- **RackUnit** — full-width Surface1 panel, radius 12, 1dp bezel, label row
  (RackLabel) with trailing readout. Replaces floating cards.
- **LogHeader** — section label (Section, Text2) + mono count right beside it +
  optional key action at the right.

## 4. Screens

- **Chats (rundown)** — top bar: profile avatar + name, search, new chat. Signal
  line(s) under it when needed. Large title "Chats". **Live now**: one monitor
  per chat/bot task working right now (1 = full width, 2 = pair, 3+ = scrolling
  wall with a 48dp right fade); screen shows the chat name + latest words (or
  "You: …" while the agent hasn't answered yet), strip shows lamp · current tool
  verb (Running / Coding / Reading / Editing / Browsing / Looking / Delegating) ·
  on-air timecode (from server `turn_at`). Live chats move to the stage while
  they run and drop back into the list when done. Rundown rows: channel badge
  (2-letter code, first+last word, squared tile 48dp; group colour when grouped)
  · title (Pretty.preview-cleaned) + preview · right column: time in mono + lamp
  (LIVE / WAIT / CUE=unread; nothing when idle). Sections: Pinned, groups, All
  chats, Hidden — all LogHeaders with counts.
- **Chat (program)** — header: avatar, title, status line = TallyLabel ("On air" /
  "Waiting for you" / "Offline" / "Reconnecting…") + live timecode; idle shows the
  model selector. Work runs as an as-run strip (lamp · tool icon · current action
  · "N steps" mono · chevron). Day breaks = hairline · label · hairline.
  Bubbles stay WhatsApp-like (user right, prose left); bubble times in mono.
  Composer = console well (radius 14, 1dp bezel); send = square key lit white
  when sendable; stop = key with a red square; mic = key.
- **Agents (multiview)** — "Give task" = the lit key. Multiview wall 2-up: one
  monitor per agent (avatar + model tag on screen, task or last task as body;
  strip: lamp · name · timecode / Idle). Task log (as-run log): mono start time +
  duration column, lamp, agent + status word, title, one-line result; tap opens
  the task chat. Schedules (rack, lamps), Today readout, Usage.
- **Mac (rack)** — header key "Files". Rack units: the Mac (Online lamp, mono
  vitals line, action keys with armed-confirm), Web preview rows, Memory and
  plan limits as segmented meters with mono %.
- **Files (clip bin)** — selector keys (All / Images / Videos / Docs / Audio),
  day-grouped grid of 116dp+ thumbnails (radius 8) with a slate band (kind code +
  clock in Catalog mono); tap previews/saves, long-press → "Open the chat it came
  from" / "Save to phone".
- **Settings** — rack units per section; segments as selected keys; switches
  with a LampOff track when off.
- **Bottom bar** — hairline on top; the active tab lights an 18×3dp white
  segment above its icon (bus key). Badge = live agents count.

## 5. Motion

Nav shared-axis 220ms; new message fade + 8dp rise 180ms; status word and
timecode changes flip (180ms); meters fill 450ms; LIVE lamps breathe 1.2s.
`ANIMATOR_DURATION_SCALE == 0` → all motion off (flip = instant, lamps steady).

## 6. System chrome

Edge-to-edge, light icons; splash + adaptive icon on #0B0B0C; themed-icon
monochrome layer = the sheared H mark.
