# M20 — attach / files / clarify — RELEASED v20

## Done (built, 144 unit tests green, NOT released)
- Attach sheet: Camera / Gallery (photo+video picker) / Files / Location. Limit 25 MB (was 8 MB).
- Upload video from phone (file.attach), thumbnail chip, MEDIA: line in the message so bubble renders it.
- File cards in chat for MEDIA:/path docs (md, pdf, zip, …): tap text files → full-screen reader
  (markdown rendered, code mono); download icon → Download/Hermes/.
- Save pill on chat videos (was photo-only).
- Clarify: choices as tappable rows, Other…, Skip, multi-select + Done, batch "1 of N" answered at once.
- Server proxy: downstream WS max_msg_size=0 (aiohttp 4 MB default broke uploads >3 MB);
  /api/mobile-media whitelist + js/java/xml/log/toml/sql/docx/xlsx/pptx/mp3/wav/m4a. Proxy restarted.

## Verified on emulator-5572
- Agent reply with MEDIA video/photo/md/zip: video plays, photo inline, file cards render.
- md reader OK; save: bundle.zip → Download/Hermes (272 B), demo-photo.jpg → Pictures/Hermes,
  demo-clip.mp4 → Movies/Hermes (1640241 B = original).

## TODO tomorrow
1. Re-test phone video upload: gallery picker → tap thumbnail → tap Done (previous run missed Done).
   Check the chip, send, then confirm the agent sees the file (@file ref + MEDIA line) and the bubble renders the video.
2. Attach sheet polish: tiles are invisible (Surface2 ≈ sheet bg) → lighter tile (Surface3/hairline),
   icon→label gap 8dp, tighter vertical padding.
3. Test clarify live: /tmp/media_e2e.py clarify <stored_id> → card with 3 choices on phone, tap, agent replies "picked …".
4. Test camera + location (emulator has a virtual camera / geo fix).
5. Release v20 (Desktop + release/ + gh release), kill emulator, remove lock.

Test session: "Media Test" 20261005_234600_411e88 (delete after). Fixtures: ~/.hermes/cache/mobile-e2e/.
Scripts: /tmp/media_e2e.py, /tmp/polish/ui.py (tmp — may vanish on reboot).
