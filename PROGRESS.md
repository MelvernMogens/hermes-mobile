# M9 — UX pass dogfood Melvern (28 Sep) — PROGRESS

Repo: ~/Code/hermes-mobile. Brief 6 item, verify di verify/m9/.
Baseline: commit 11c39ec, working tree clean.

- [ ] 1. Tabel markdown (Markdown.kt parse Table + render Row/weight, hscroll >4 kolom, tap cell → sheet)
- [ ] 2. Reasoning effort di ModelSheet (4 chip Low/Med/High/Max; config.set/get reasoning; createSession reasoning_effort)
- [ ] 3. Video player (regex .mp4/.mov/.webm/.mkv/.avi; media3 ExoPlayer; proxy mime video; fallback chip)
- [ ] 4. SelectionContainer per-message (partial copy) + long-press full-copy tetap
- [ ] 5. Bot Chat render audit (gap 8dp konsisten, nested bullet, spasi ganda, emoji utuh)
- [ ] 6. TranscriptCache LRU-8 + rememberSaveable input + delta resume events.since

## Temuan contract (sumber: openrpc.json + tui_gateway)
- reasoning kata valid server: minimal|low|medium|high|xhigh|max|ultra (VALID_REASONING_EFFORTS).
  Brief minta chip Low/Medium/High/Max → kirim kata `low|medium|high|max` (sama dgn desktop).
- config.get key=reasoning (+session_id) → {value, display}; value bisa "none".
- session.create params reasoning_effort (string|null) — contract OK.
- session.events.since {session_id, last_seen?} → {events, latest_seq, truncated, ...} — dipakai delta resume.
- session.resume omit_messages:true → messages:[] tapi runtime tetap attach (dipakai refresh cepat cache-hit).
- Emulator: emulator-5572 Medium_Phone_API_36.1 (diclaim di EMULATOR-LOCKS.md).

## Log
- 02 Oct: mulai. Baseline build+test GREEN sebelum edit (hasil dicatat di bawah).
