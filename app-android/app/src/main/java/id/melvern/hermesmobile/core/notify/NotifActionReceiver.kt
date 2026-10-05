package id.melvern.hermesmobile.core.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import id.melvern.hermesmobile.HermesApp
import id.melvern.hermesmobile.core.repo.SessionRepo
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Aksi dari notifikasi (tanpa membuka app):
 *  - ACTION_APPROVE / ACTION_DENY → request.answer {choice} untuk approval yang menunggu.
 *  - ACTION_REPLY (RemoteInput) → kalau ada clarify menunggu: jawab clarify; selain itu
 *    kirim teks sebagai pesan baru ke session itu (prompt.submit).
 * Selesai → notif diganti status singkat ("Allowed", "Sent") lalu dihapus.
 */
class NotifActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_APPROVE = "id.melvern.hermesmobile.NOTIF_APPROVE"
        const val ACTION_DENY = "id.melvern.hermesmobile.NOTIF_DENY"
        const val ACTION_REPLY = "id.melvern.hermesmobile.NOTIF_REPLY"
        const val EXTRA_STORED = "stored_id"
        const val EXTRA_REQUEST = "request_id"
        const val EXTRA_KIND = "request_kind"     // approval | clarify | "" (plain reply)
        const val EXTRA_QID = "clarify_qid"
        const val KEY_REPLY = "reply_text"
        private const val TAG = "HermesNotifAction"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? HermesApp ?: return
        val stored = intent.getStringExtra(EXTRA_STORED) ?: return
        val requestId = intent.getStringExtra(EXTRA_REQUEST).orEmpty()
        val kind = intent.getStringExtra(EXTRA_KIND).orEmpty()
        val qid = intent.getStringExtra(EXTRA_QID)
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim()
        val pending = goAsync()
        app.appScope.launch {
            val status = try {
                val c = app.client ?: throw IllegalStateException("not connected")
                when (intent.action) {
                    ACTION_APPROVE, ACTION_DENY -> {
                        val choice = if (intent.action == ACTION_APPROVE) "once" else "deny"
                        val res = c.call("request.answer", buildJsonObject {
                            put("id", requestId)
                            put("result", buildJsonObject { put("choice", choice) })
                        })
                        if (res["status"]?.toString()?.contains("expired") == true) "Already answered"
                        else if (choice == "once") "Allowed" else "Denied"
                    }
                    ACTION_REPLY -> {
                        if (reply.isNullOrEmpty()) "Nothing sent"
                        else if (kind == "clarify" && requestId.isNotEmpty()) {
                            c.call("request.answer", buildJsonObject {
                                put("id", requestId)
                                put("result", buildJsonObject {
                                    if (qid != null) put("answers", buildJsonObject { put(qid, reply) })
                                    else put("answer", reply)
                                })
                            })
                            "Answered"
                        } else {
                            val repo = SessionRepo(c, app.profile.value)
                            val runtime = repo.resumeAttachLazy(stored) ?: stored
                            repo.sendPromptResilient(stored, runtime, reply)
                            "Sent"
                        }
                    }
                    else -> null
                }
            } catch (e: Throwable) {
                Log.w(TAG, "action failed: ${e.message}")
                "Couldn't reach your Mac — open the app"
            }
            status?.let { app.notifier.settle(stored, it, reply) }
            pending.finish()
        }
    }
}
