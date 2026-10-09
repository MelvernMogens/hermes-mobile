# V28 data layer: report

Branch `v28-features` (worktree /Users/melvernmogens/Code/hermes-mobile-v28), base f884aea. Not merged and not pushed.
Commits: 1d648d5 A · 5dd86d9 B · c1134e6 C · ef5e8fe D · 46b0b45 E · 769456c review fixes.
I did not touch the main checkout or the production proxy (PID 22965 on :8790 stayed up the whole time).

## A. Bot task history: `GET /api/mobile-tasks?limit=60`
- Files: server/mobile_tasks.py (new); route in desktop_gateway_proxy.py (`_authed_or_401`, executor);
  mobile_insights._flatten gained an optional `limit` arg; app core/repo/TasksRepo.kt (BotTask, list(): List<BotTask>?, durationLabel).
- Shape: `{tasks:[{profile,id,title,started_at,ended_at,message_count,last_activity,status,result}]}`.
- Status rules:
  - `running`: the profile has a live bot-run process (mobile_mac.parse_bot_procs) AND the session is that profile's newest one. Also running: any session with no ended_at that was active in the last 10 min (covers parallel runs). If `ps` fails, only that "active in the last 10 min" rule applies.
  - `done`: the last active row is an assistant message with content and no tool_calls.
  - `failed`: end_reason matches error/fail/crash.
  - `stopped`: everything else.
  - All rows are filtered with `COALESCE(active,1)=1`.
- Tests: test_running_done_stopped_and_order, test_no_process_tool_last_is_stopped_and_active0_ignored,
  test_profile_without_db_skipped, test_failed_end_reason, test_review_fix_mid_turn_prose_is_not_done,
  test_review_fix_parallel_and_ps_unknown_stay_running. Kotlin: TasksRepoTest (4).

## B. Rich "bot finished" notification
- Files: core/notify/TaskNotifs.kt (new). NotifRouter.kt (calls TasksRepo.list() under `withTimeoutOrNull(4_000)`, at most every 60 s).
  AppNotifier.postTaskDone. NotifActionReceiver gets EXTRA_PROFILE, which is passed to SessionRepo for Reply and settle.
- Notification content:
  - Title: "<Bot> finished", or "<Bot> stopped" when status is failed or stopped.
  - Text: "<title> · <duration>"; BigText: result.
  - Tap opens route `id|t=…|p=<profile>` through the existing EXTRA_OPEN_CHAT, so MainActivity needed no change.
  - Reply uses the same RemoteInput → NotifActionReceiver → prompt.submit path, on that profile.
- Persistence: SharedPreferences `tasks_notified` stores `epoch|id` entries, pruned by age (3 days) with a cap of 400.
- Pure function `decideTaskNotifs(prev, now, notified, nowEpoch)` notifies when:
  - a task goes running→done/failed/stopped, or
  - a task is first seen already finished and started less than 2 h ago.
  - A "stopped" task first seen this way waits until it has been quiet for 2 min.
  - Tasks older than 2 h that were never seen running are skipped, so there is no flood on first install.
- Tests: TaskNotifsTest (9), including no_flood_on_first_run_after_install and review_fix_partial_response_does_not_erase_dedupe.

## C. All-files gallery: `GET /api/mobile-files?limit=120&kind=all|image|video|audio|doc`
- Files: server/mobile_files.py (new), route (kind validated, limit clamped to 500), app core/repo/FilesRepo.kt (AgentFile, list(kind)).
- Shape: `{files:[{path,name,kind,size,at,profile,session_id,session_title}]}`.
- What it scans and returns:
  - The 400 newest sessions per DB (default + bots), assistant rows with active=1.
  - Paths are resolved with strict=True, must be under $HOME, must use the same suffix allowlist as `_IMG_MIME` (/api/mobile-media), missing files are skipped, deduped by resolved path, newest first, cached 30 s.
- The matcher is wider than "own line". Real data also has `**Label:** MEDIA:/x.png` and `> MEDIA:/x`. Backtick examples are deliberately not matched.
- Tests: test_newest_first_dedup_missing_skipped (3 messages, one missing file, one duplicate path), test_kind_filter_and_cache,
  test_kind_of, test_media_paths_inline_and_quoted_not_backticked. Kotlin: FilesRepoTest (2).

## D. Long-poll wake-up: `GET /api/mobile-wait?since=<cursor>&timeout=25`
- Files: server/mobile_wait.py (new), route; app core/notify/WakePoll.kt (new), NotifRouter.kt loop.
- Shape: `{cursor, events:[{kind:"message"|"task",profile,session_id,title,at}]}`.
- Cursor: `"default:70235,qa:164,...;run=coder+sesi2tes"`, i.e. MAX(messages.id) per DB plus the set of bots with a live process.
  - If `since` is missing or garbled, the server answers immediately with a fresh baseline cursor.
- Server loop: checks every 1.5 s and keeps to the exact deadline. `ps` is cached for 5 s; if it fails, the last known value is kept.
  - "message" events: new final assistant rows (active=1, non-empty, no tool_calls) in non-tool sessions.
  - "task" events: a bot's running state flipped.
- App behaviour:
  - Long-poll only runs while the app is in the background and the WS is OPEN. It only wakes `pollOnce`, which is still the source of truth.
  - On events: poll at once, but at most one wake every 5 s.
  - On a short timeout: pad the wait to 20 s.
  - On error: back off 20 → 40 → 60 s, then fall back to the old 20 s delay.
  - OkHttp read timeout is 35 s, and the call is cancelled together with the coroutine.
  - A task event makes the 60 s task check due immediately.
- Tests: test_roundtrip, test_garbage_is_none, test_diff, test_baseline_then_events. Kotlin: WakePollTest (3: parse, backoff_20_40_60_cap,
  sleep_after_wake_never_tight_loops).

## E. Huge chats open instantly: `GET /api/mobile-tail?profile=&id=<stored>&limit=120`
- Files: server/mobile_tail.py (new), route (profile via mobile_insights.state_db, id regex); app core/repo/TailRepo.kt (new);
  ChatScreen.kt hunk (+16/−3, inside the existing load LaunchedEffect).
- Shape: `{messages:[...], has_more, total_active}`. `messages` copies tui_gateway `_history_to_messages`:
  - user/assistant: `{role,text,timestamp,row_id,reasoning?,display_kind?,display_metadata?}`
  - tool: `{role:"tool",name,context,args?}`
  - Hidden rows, `[System:` user rows and tool-call-only assistant rows are dropped.
  - It decodes with the same `TranscriptMessage` serializer.
- Parity check against the real gateway projection, on a copy of state.db (the real DB was not written to), session UTS Web Review:
  role/text/name/row_id/args/timestamp are identical for all 120 rows. Only `context` differs: the gateway uses
  build_tool_preview (e.g. "cmd + 5 commands"); mine is the first argument, 80 chars. That field is only a preview.
- ChatScreen flow on a TranscriptCache miss:
  1. Launch the tail fetch and render it with a "Loading earlier messages…" NoticeLine when has_more is true.
  2. Run session.resume as before. Its result replaces `items` wholesale, cancels the tail job and clears `tailOnly`.
  - The tail is applied only while `loading && items.isEmpty()`.
  - While `tailOnly` is set, sending the offline queue is blocked.
- Tests: test_shape_matches_resume, test_limit_returns_last_n_across_batches, test_bad_session_id. Kotlin: TailRepoTest (3, fixture string).

## Check outputs
1. `uv run --with pytest --with aiohttp python -m pytest -q tests/` → **33 passed** (16 existing + 17 new). The new test files also pass on /usr/bin/python3 3.9.
2. `./gradlew :app:assembleDebug :app:testDebugUnitTest` → **BUILD SUCCESSFUL**, from the XML: **204 tests, 0 failures, 0 errors**
   (baseline 183 + 21 new). APK: app-android/app/build/outputs/apk/debug/app-debug.apk.
3. Live probe: worktree proxy on :8795 (HERMES_PREVIEW_PORT=8796; that env var already existed, so no code change was needed). I killed it afterwards and 8795/8796 are free.
   - **Unauthenticated, live :8795**: mobile-tasks 401, mobile-files 401, mobile-tail 401, mobile-wait 401.
   - **Authenticated: NOT done against the live proxy.** ~/.hermes/mobile-serve.env holds only HERMES_DASHBOARD_SESSION_TOKEN.
     There is no password there; config.yaml only has a password_hash; the browser vault is empty and the automation browser on :9333 was down.
     So I ran the REAL handlers in a real aiohttp TestServer against the real ~/.hermes DBs, with only `_cookie_authed` stubbed to True
     (script /tmp/v28_probe2.py). Timings are from the client, end to end:
```
/api/mobile-tasks           200  101 ms  10 tasks
 [{"profile":"sesi2tes","id":"20261009_200220_9c54f4","title":"Pakai skill aturan-teknikal-kevin. Buatkan…",
   "started_at":1791550957.24,"ended_at":null,"message_count":21,"last_activity":1791551169.64,"status":"running","result":""},
  {"profile":"sesi2tes","id":"20261009_195629_f45087","title":"Pakai tool CoinGecko MCP (bukan web search)…",
   "started_at":1791550595.83,"ended_at":1791550612.16,"message_count":6,"status":"done","result":"Data dari CoinGecko (per 9 Okt 2026, ~19:56 WIB…"}]
/api/mobile-files?kind=image 200 258 ms cold / 2 ms cached, 4 files
 [{"path":"/Users/melvernmogens/Code/cybertron-spire/assets/art/pixel-test/px-optimus.png","name":"px-optimus.png","kind":"image",
   "size":9238,"at":1790528172.90,"profile":"default","session_id":"20260927_191932_04463e","session_title":"Cybertron Spire"},
  {"path":".../px-megatron.png","name":"px-megatron.png","kind":"image","size":10160, ...same session}]
/api/mobile-tail?id=20260922_230113_b173d6&limit=120  200  12 ms (2nd: 20 ms)  120 messages, has_more=true, total_active=1255
 [{"role":"tool","name":"browser_exec","context":"import time # masih streaming pelan. Tunggu sampai selesai & app boot for i in r"},
  {"role":"tool","name":"terminal","context":"lsof -ti:8797 | xargs kill 2>/dev/null; pkill -f browser-use 2>/dev/null; sleep "}]
/api/mobile-wait?timeout=3            200    58 ms  0 events (baseline) cursor "coder:77415,content:14814,default:70235,designer:0,qa:164,sesi2tes:38,video:1850;run=coder+sesi2tes"
/api/mobile-wait?timeout=3&since=<c>  200  3013 ms  0 events (no change → returned at timeout)
```
   - Mismatch with the brief: UTS Web Review has **1255 active** rows, not 3148. The largest active session on disk is this one;
     the 3148/7488 figures must include rewound rows (active=0). The tail is 120 rows in 9–20 ms, well under 300 ms.

## Reviewer (1 fresh subagent, opus, diff f884aea..HEAD): findings and fixes (commit 769456c)
1. MED false "finished" while a task still runs. Cause: an intermediate assistant row with tool_calls counted as done, and
   `live` came from a single ps snapshot plus "newest session only". Fixes:
   - done now requires no tool_calls;
   - an open session active in the last 10 min counts as running (covers parallel runs);
   - a `ps` failure means "unknown" instead of "nothing running".
2. MED re-notify flood: the notified set was pruned to "ids in the latest response", so one partial response (a locked profile DB) wiped it.
   Fix: prune by age (3 days) with a cap, and `mergePrev` remembers ids that are missing from a response.
3. MED battery: every assistant row in any session (bot tool sessions included) woke the phone about every 5 s during a bot run.
   Fix: message events only for final answers (no tool_calls) in non-tool sessions; bot runs report through "task" events.
4. LOW tail → loading=false opened a window where the offline queue was sent before resume. Fix: the `tailOnly` flag gates the queue effect.
5. LOW statedb_fixture.py failed on python 3.9 (`float | None`). Fix: `from __future__ import annotations`.
- Extra fix I found during the probe: mobile-wait returned at 1.5 s for timeout=3 (step rounding). It now sleeps `min(step, remaining)`.
- Reviewer found nothing in: SQL injection/path traversal (all queries parameterized, profile/id regex, files resolve+$HOME+allowlist),
  missing auth (all 4 routes use `_authed_or_401`), profile routing, event-loop blocking.
- After the fixes I re-ran checks 1 and 2: 33 passed; BUILD SUCCESSFUL, 204/0.

## Not done / caveats
- Authenticated live probe against :8795: no password available to this session (see check 3). Hermes or Melvern should run
  `/tmp/v28_probe.py` (reads the password from the env file and never prints it) once the env holds a password, or save a vault login.
- No emulator or device run (forbidden by the brief). Notification posting, deep-link tap, Reply, and the ChatScreen tail rendering are
  compile-verified and unit-tested only; they have not been seen on a screen.
- The proxy is not deployed. Production :8790 runs the main checkout, so the new endpoints only go live after merge + a proxy restart
  (that needs a rollback plan: revert the merge commit, then `launchctl kickstart -k gui/$UID/com.hermes.desktop-gateway-proxy`).
- Kotlin and server comments are in Indonesian per HERMES.md; UI strings are English.
