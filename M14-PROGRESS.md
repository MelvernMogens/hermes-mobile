# M14 — Notifikasi background (foreground service + polling) — PROGRESS

Repo: ~/Code/hermes-mobile. Brief M14, verify di verify/m14/. Baseline: 695891c (71 test PASS).

## Status: DONE + terverifikasi emulator 5572 (3 Okt) + review fixes applied

## Temuan desain fundamental (riset, mengubah pendekatan)
Brief asumsi notif bisa listen event ring inbound seperti ChatScreen. FAKTA SERVER
(dibuktikan E2E via probe non-viewer WS): `hermes serve` / tui_gateway hanya mengirim
event frame ke TRANSPORT yang attach ke session itu (viewer). Koneksi WS app yang tidak
membuka chat session X hanya menerima `gateway.ready` + `sessions.changed`. Jadi deteksi
aktivitas agent TIDAK bisa lewat event — sesuai judul brief ("polling service"):
- **Poller**: `session.active_list` tiap 20s → diff `status` (working/waiting/idle) +
  `message_count` + `preview` + `title` + `session_key` (server sudah resolve semua).
- `working→idle` atau count naik (saat !working) → notif "Agent replied — <title>"
  preview 60 char. `waiting` baru → notif high "Agent needs your approval".
- Approval asli dites: `approvals.mode` di-set manual via `config.set` RPC (default
  Melvern `off` → bypass), prompt "rm -rf /tmp/..." → status `waiting` terverifikasi
  di active_list → notif muncul. Mode direvert ke `off` setelah test.

## Apa yang dibangun
- core/notify/NotifPolicy.kt — pure logic: onPollDiff + onMessageComplete (jalur event,
  utk unit test) + dedup 30s + foreground gate + open-chat gate + preview 60c.
- core/notify/NotifRouter.kt — poller (GatewayClient.startNotifPoller, loop 20s di
  appScope); snapshot keyed by sessionKey (stored id — runtime sid berganti tiap resume
  surface lain, terbukti 650ad4c2→49fada2d); baseline reset saat WS gak OPEN.
- core/notify/AppNotifier.kt — channel "agent" (high + sound) + "connection" (low,
  silent); ongoingConnection() utk FGS; postAgent + PendingIntent deep-link extra
  open_chat; cancelSessionNotifications.
- HermesLiveService — FGS dataSync + NOTIF_ONGOING "Connected to your Mac"; partial
  wake lock 4 jam + thread reaper re-acquire (synchronized, volatile, stopped flag);
  onTimeout(int)/(int,int) utk limit 6 jam Android 14+ (mati rapi, bukan crash);
  stop() via stopService (bukan startService — IllegalState di background 12+).
  Koneksi WS TETAP di HermesApp — service gak pernah bikin koneksi kedua.
- HermesApp — ActivityLifecycleCallbacks startedActivities → isForeground;
  openChatStoredId; openChatRequests flow (onNewIntent); heartbeat 15s fg / 25s bg
  (via heartbeatIntervalMs lambda di GatewayClient — dispatch logic tak disentuh);
  disconnect() (service stop + notif clear + client stop); poller job di-cancel di
  buildClient (review H1: retry connect gak boleh numpuk poller).
- MainActivity — POST_NOTIFICATIONS request (33+) di onCreate pertama; intent extra
  open_chat → AppNav navigate; onNewIntent → offerOpenChat.
- ChatScreen — DisposableEffect set/clear app.openChatStoredId (notif utk chat yang
  dibuka di-mute).
- SessionsScreen — banner "Notifications disabled — tap to enable" → pengaturan notif
  app (recall: dialog sistem hanya bisa 2x per install).
- Manifest — FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC + WAKE_LOCK +
  POST_NOTIFICATIONS; service exported=false foregroundServiceType=dataSync.
- ic_notification.xml — vector lingkaran + petir putih di atas transparan.

## Bukti verify (verify/m14/, emulator 5572, server 10.0.2.2:8790, login melvern/ted)
- 00-ongoing-notif.png — shade: "Hermes · Connected to your Mac / Hermes is listening
  in the background", label Silent (channel connection low) ✓.
- 01-notif-perm.png — dialog sistem "Allow Hermes to send you notifications?"
  (muncul di first launch; utk fresh install. Emulator ini pernah deny → grant via
  pm grant utk test lanjut).
- 02-notif-agent-reply.png — probe kirim prompt ke session "Save & link test" yang
  TIDAK dibuka di app (app di HOME): notif "Agent replied — Save & link test / Siap."
  di section non-silent ✓.
- 03-notif-approval.png — approvals.mode=manual + prompt rm -rf → status waiting di
  active_list → notif "Agent needs your approval — Save & link test / Approval is
  waiting for your response." high-priority section ✓ (mode direvert ke off).
- 04-tap-notif.png — tap notif → app buka chat "Save & link test" dengan seluruh
  transcript probe (sampai reply terakhir) ✓.
- 05-background-alive.png + 05-logcat-bukti.txt + 05-batterystats.txt +
  05-dumpsys-power.txt — app background 2+ menit: logcat HermesNotifRouter "notif:
  Agent replied ... Hujan di Jakarta" (event turn di background TETAP menghasilkan
  notif); batterystats UID u0a227: fgs 31m29s, CPU total 0.0192% (hemat);
  wake lock hermes:live PARTIAL ACQ LONG; dumpsys power isFrozen=false.
- Unit test: NotifPolicyTest 25 test → total suite 96/96 PASS
  (71 baseline + 25 baru; termasuk regresi review H2: waiting baseline kosong).

## Review subagent (glm-5.3, 1 reviewer fresh) — temuan & fix
- H1 poller numpuk saat retry connect (RPC + notif dobel) → FIXED: notifPollerJob
  di-cancel di buildClient/disconnect.
- H2 notif approval palsu saat baseline kosong (reconnect) → FIXED: guard
  `before != null` + unit test regresi.
- M1 FGS gak mati saat koneksi gagal → NOTE: disconnect() tersedia; ConnectScreen
  retry path sekarang cancel poller; FGS stop eksplisit di-hook disconnect() (belum
  ada UI logout — di luar scope brief, service mati saat proses mati / user stop).
- M2 dataSync 6h limit Android 14+ crash → FIXED: override onTimeout(int) +
  onTimeout(int,int) → stopClean + stopSelf.
- M3 race reaper vs onDestroy (wake lock re-acquire setelah release) → FIXED:
  synchronized + @Volatile + stopped flag dicek dalam lock.
- M4 stop() pakai startService dari background (IllegalState 12+) → FIXED: stopService.
- L1 hashCode collision notif id/PendingIntent → diterima (app personal, 1 device).
- L2 dedup window shared antar jenis → diterima (desirable).
- L3 banner stale setelah balik dari settings → diterima (minor, recall dialog limit).
- L4 dead code onEvent → FIXED: dihapus (poller satu-satunya jalur).
- L5 turn error/interrupt di background → "Agent replied" dgn preview lama →
  KNOWN LIMITATION polling (server hanya expose status/count; gak ada turn.error).
- Bonus fix dari verify sendiri: snapshot poller keyed by sessionKey bukan runtime sid.

## Build/test (final, setelah review fixes)
- :app:testDebugUnitTest = 96 tests, 0 failures.
- :app:assembleDebug PASS. APK → ~/Desktop/hermes-mobile.apk + release/.
- E2E ulang post-fix: notif "Agent replied — Save & link test | **Gunung**..." muncul
  (logcat 04:16:27) — jalur tetap hijau setelah semua fix review.
