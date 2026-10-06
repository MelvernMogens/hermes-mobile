# v23–v25 verification (emulator-5572, 6 Oct 2026)

All on Medium_Phone_API_36.1 against the live Mac backend (proxy 8790 / preview 8791).
Screenshots: /tmp/polish/m23/*.png (not committed — contain chat content).

| Feature | Result | Evidence |
|---|---|---|
| Task board (todo.updated) | PASS — strip "Delegate subagent… 1/3", sheet checklist live → 3/3 Done | 06-workstrip, 07-worksheet |
| Subagent monitor | PASS — "1 running · terminal · GLM 5.3", log/steer/stop controls, disappears when done | 08-subagent-open |
| Steer (session.steer) | PASS — "change of plan: BANANA" mid-turn → agent answered BANANA, bubble tagged "Steered" | 09-steered, 10-steer-result |
| Permission inbox | PASS — "1 request waiting for you" banner → answered Kiwi from inbox → agent replied KIWI | 11-inbox-banner, 12-inbox |
| Unread markers | PASS — white dot + bold time + bright preview after reply arrived while away | 21-unread |
| Mac panel | PASS — battery/CPU/disk/uptime, Keep awake/Lock/Display off/Close bot tabs/Restart | 01-mac |
| Web preview | PASS — reel-studio (localhost:4545) rendered in-app via single-use ticket | 02-preview |
| Code changes (diff) | PASS — hermes-mobile 39 files +680 −33, per-file coloured hunks | 19-diff |
| Edit & resend | PASS — row 64979 rewound (active=0), new 65000 "…reply TWO" → agent TWO | 18-edit-ok + state.db |
| Share chat / Share message | PASS — Android chooser opened with chat text | 20-share |
| Schedules (cron) | PASS — created "Every Monday 9:00" job on Mac, Pause → Paused, Delete → gone | 03/04 + /api/cron/jobs |
| Search in messages | PASS — "tailscale" → 7 hits with highlighted snippets | 05-search |
| Saved prompts | PASS (unit) — save from message sheet, list in + sheet | V23to25LogicTest |
| Offline queue | PASS — airplane mode → "Will send when your Mac is reachable" → reconnect → auto-sent, agent QUEUEDOK | 22-offline, 23-offline-sent |

Bugs found + fixed during verification:
- Shared login session (SharedAuth): every HTTP repo did a fresh password-login → server 10/min limit → "Couldn't reach your Mac".
- Preview over http (emulator/LAN) used https URL; cookies don't cross ports in WebView → single-use ticket flow.
- Steer rows showed raw "[OUT-OF-BAND USER MESSAGE …]" wrapper → unwrapped in app + server preview.
- user-tail returned rewound (active=0) rows → stale row_id → edit failed. Server filters active rows; app re-resolves the active row before editing.
- Edit of the FIRST message needs confirm_empty_truncate.
- Composer not focused after "Edit & resend" → now auto-focus.
- Send disabled while offline → offline text goes to the queue.
- Search snippets had leftover JSON backslashes → cleaned.
- Notification skipped for the last-opened chat while app in background (v22).

Tests: 171 unit (app) + 13 server, 0 failures.
