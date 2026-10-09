package id.melvern.hermesmobile.core.notify

import android.content.Context
import android.util.Log
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.BotFleet
import id.melvern.hermesmobile.core.repo.BotTask
import id.melvern.hermesmobile.core.repo.TasksRepo

/**
 * v28 item B: notif "bot selesai" untuk tugas bot (bot-run / Give task).
 * Sumber = /api/mobile-tasks (maks tiap 60 dtk dari poller M14). Id tugas yang sudah dinotif
 * disimpan persist (SharedPreferences `tasks_notified`) → restart app tidak menotif ulang.
 */
object TaskNotifs {
    const val WINDOW_SECS = 2 * 3600.0
    const val CHECK_EVERY_MS = 60_000L
    private const val PREF = "v28_task_notifs"
    private const val KEY = "tasks_notified"
    private const val MAX_KEPT = 400
    private const val TAG = "HermesTaskNotifs"

    /**
     * Pure. Tugas yang harus dinotif sekarang:
     *  - transisi running → done/failed/stopped (prev = status poll sebelumnya), atau
     *  - pertama kali terlihat SUDAH selesai (tidak pernah terlihat running) dan dimulai < 2 jam lalu.
     * Tugas lebih tua dari 2 jam yang belum pernah terlihat → tidak dinotif (anti-banjir sesudah install).
     * Tiap id maksimal sekali (notified).
     */
    fun decideTaskNotifs(
        prev: Map<String, String>,
        now: List<BotTask>,
        notified: Set<String>,
        nowEpoch: Double,
    ): List<BotTask> = now.filter { t ->
        if (t.isRunning || t.id in notified) return@filter false
        if (prev[t.id] == "running") return@filter true
        if (t.startedAt < nowEpoch - WINDOW_SECS) return@filter false
        // "stopped" tanpa pernah terlihat running bisa berarti tugas paralel kedua di profile yang
        // sama (server hanya menandai session terbaru running) — tunggu sampai sepi 2 menit.
        !(t.status == "stopped" && t.lastActivity > nowEpoch - QUIET_SECS)
    }

    const val QUIET_SECS = 120.0

    /** Pure: judul notif — "<Bot> finished" / "<Bot> stopped". */
    fun title(botName: String, t: BotTask): String =
        if (t.status == "done") "$botName finished" else "$botName stopped"

    fun text(t: BotTask, nowEpoch: Double): String = "${t.title} · ${TasksRepo.durationLabel(t, nowEpoch)}"

    /** Nama tampilan bot dari fleet (displayName) — fallback nama profile berhuruf besar. */
    fun botName(profile: String): String =
        id.melvern.hermesmobile.ui.components.Pretty.profile(
            BotFleet.bots.value?.firstOrNull { it.name == profile }?.label?.takeIf { it.isNotBlank() } ?: profile,
        )

    private fun sp(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private const val KEEP_SECS = 3 * 86400L

    /** Disimpan sebagai "<epochDetik>|<id>" → {id: epoch}. */
    fun loadNotified(c: Context): Map<String, Long> = decode(sp(c).getStringSet(KEY, emptySet()).orEmpty())

    fun saveNotified(c: Context, m: Map<String, Long>, nowSecs: Long) {
        sp(c).edit().putStringSet(KEY, encode(prune(m, nowSecs))).apply()
    }

    fun decode(raw: Set<String>): Map<String, Long> = raw.mapNotNull { e ->
        val i = e.indexOf('|')
        if (i <= 0) null else e.substring(i + 1) to (e.substring(0, i).toLongOrNull() ?: return@mapNotNull null)
    }.toMap()

    fun encode(m: Map<String, Long>): Set<String> = m.map { (id, at) -> "$at|$id" }.toSet()

    /**
     * Pure. Pangkas berdasar UMUR (3 hari > jendela 2 jam) + batas jumlah — BUKAN berdasar "ada di respons
     * terakhir": satu respons parsial (DB satu profile terkunci) tidak boleh menghapus memori dedupe.
     */
    fun prune(m: Map<String, Long>, nowSecs: Long): Map<String, Long> =
        m.filterValues { it >= nowSecs - KEEP_SECS }.entries.sortedByDescending { it.value }
            .take(MAX_KEPT).associate { it.key to it.value }

    /** Pure: status poll baru di atas yang lama — id yang hilang sementara dari respons tetap diingat. */
    fun mergePrev(prev: Map<String, String>, list: List<BotTask>): Map<String, String> {
        val cur = list.associate { it.id to it.status }
        val kept = prev.filterKeys { it !in cur }.entries.take(MAX_KEPT).associate { it.key to it.value }
        return kept + cur
    }

    /** State poller (satu instance per loop poller). */
    class Watcher(private val app: HermesApp) {
        private var prev: Map<String, String> = emptyMap()
        private var lastCheck = 0L

        fun due(nowMs: Long): Boolean = nowMs - lastCheck >= CHECK_EVERY_MS

        /** v28 D: long-poll melaporkan event tugas → cek di putaran berikut (tetap maks 1x per bangun). */
        fun forceDue() { lastCheck = 0L }

        /** [list] = hasil TasksRepo.list() (null = gagal → baseline dipertahankan). */
        fun onList(list: List<BotTask>?, nowMs: Long = System.currentTimeMillis()) {
            lastCheck = nowMs
            if (list == null) return
            val nowEpoch = nowMs / 1000.0
            val notified = loadNotified(app)
            val fire = decideTaskNotifs(prev, list, notified.keys, nowEpoch)
            prev = mergePrev(prev, list)
            if (fire.isEmpty()) return
            for (t in fire) {
                // chat tugas sedang terbuka di layar → tidak perlu notif (tetap ditandai)
                if (app.isForeground && app.openChatStoredId == t.id) continue
                try {
                    app.notifier.postTaskDone(t, title(botName(t.profile), t), text(t, nowEpoch))
                    Log.i(TAG, "task notif: ${t.profile}/${t.id} ${t.status}")
                } catch (e: Throwable) { Log.w(TAG, "post task notif gagal: ${e.message}") }
            }
            val nowSecs = nowMs / 1000
            saveNotified(app, notified + fire.associate { it.id to nowSecs }, nowSecs)
        }
    }
}
