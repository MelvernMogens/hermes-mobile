package id.melvern.hermesmobile.core.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import id.melvern.hermesmobile.MainActivity
import id.melvern.hermesmobile.R

/**
 * M14: pengecer notifikasi — channel "agent" (high, sound) + "connection" (low, silent),
 * small icon ic_notification. Notif tap → MainActivity extra openChat=storedId.
 */
class AppNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_AGENT = "agent"
        const val CHANNEL_CONNECTION = "connection"
        const val EXTRA_OPEN_CHAT = "open_chat"
        private const val GROUP_AGENT = "agent"
    }

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_AGENT, "Agent activity", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Agent replied, needs approval, or errored" }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_CONNECTION, "Connection", NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        description = "Persistent connection status"
                        setSound(null, null)
                        setShowBadge(false)
                    }
            )
        }
    }

    /** NOTIF_ONGOING — syarat foreground service di API 29+. */
    fun ongoingConnection(): Notification {
        ensureChannels()
        return base(CHANNEL_CONNECTION)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW) // <26 fallback
            .setContentTitle("Connected to your Mac")
            .setContentText("Hermes is listening in the background")
            .setContentIntent(mainIntent(openChat = null))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Notif agent. [ask] terisi kalau agent sedang MENUNGGU (approval/clarify) — tombol
     * Allow/Deny atau Reply langsung dari shade. Notif balasan biasa juga dapat Reply.
     */
    fun postAgent(d: NotifPolicy.Decision.Notify, ask: PendingAsk? = null) {
        ensureChannels()
        val b = base(CHANNEL_AGENT)
            .setContentTitle(d.title)
            .setContentText(ask?.text ?: d.preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(ask?.text ?: d.preview))
            .setContentIntent(mainIntent(openChat = d.storedId))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // <26 fallback
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_AGENT)
        when (ask?.kind) {
            "approval" -> {
                b.addAction(0, "Deny", actionIntent(NotifActionReceiver.ACTION_DENY, d.storedId, ask))
                b.addAction(0, "Allow", actionIntent(NotifActionReceiver.ACTION_APPROVE, d.storedId, ask))
            }
            else -> b.addAction(replyAction(d.storedId, ask, label = if (ask?.kind == "clarify") "Answer" else "Reply"))
        }
        // id numerik per session: notif baru utk session sama nimpah yang lama.
        nm.notify(d.storedId.hashCode(), b.build())
    }

    /**
     * v28: notif "bot selesai". Tap → chat tugas (route `id|t=…|p=<profile>`); Reply → prompt.submit
     * ke session itu di profile bot (profile ikut di intent action).
     */
    fun postTaskDone(t: id.melvern.hermesmobile.core.repo.BotTask, title: String, text: String) {
        ensureChannels()
        val big = t.result.ifBlank { text }
        val b = base(CHANNEL_AGENT)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big).setSummaryText(t.title.take(60)))
            .setContentIntent(mainIntent(openChat = t.chatRoute))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_AGENT)
            .addAction(replyAction(t.id, null, label = "Reply", profile = t.profile))
        nm.notify(t.id.hashCode(), b.build())
    }

    /** Sesudah aksi dari shade: ganti notif dengan status singkat, lalu hilang sendiri. */
    fun settle(storedId: String, status: String, reply: String?, profile: String? = null) {
        ensureChannels()
        val n = base(CHANNEL_AGENT)
            .setContentTitle(status)
            .setContentText(reply ?: "")
            .setContentIntent(mainIntent(openChat = chatRoute(storedId, profile)))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(4_000)
            .setGroup(GROUP_AGENT)
            .build()
        nm.notify(storedId.hashCode(), n)
    }

    data class PendingAsk(val kind: String, val requestId: String, val text: String, val qid: String? = null)

    /** Route chat untuk deep link — chat milik profile bot membawa `|p=<profile>`. */
    private fun chatRoute(storedId: String, profile: String?): String =
        if (profile.isNullOrBlank() || profile == "default") storedId else "$storedId|p=$profile"

    private fun actionIntent(action: String, storedId: String, ask: PendingAsk?, profile: String? = null): PendingIntent {
        val i = Intent(context, NotifActionReceiver::class.java).apply {
            this.action = action
            putExtra(NotifActionReceiver.EXTRA_STORED, storedId)
            profile?.let { putExtra(NotifActionReceiver.EXTRA_PROFILE, it) }
            putExtra(NotifActionReceiver.EXTRA_REQUEST, ask?.requestId.orEmpty())
            putExtra(NotifActionReceiver.EXTRA_KIND, ask?.kind.orEmpty())
            ask?.qid?.let { putExtra(NotifActionReceiver.EXTRA_QID, it) }
        }
        return PendingIntent.getBroadcast(
            context, (action + storedId).hashCode(), i,
            // MUTABLE wajib untuk RemoteInput (sistem menyisipkan teks balasan)
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (action == NotifActionReceiver.ACTION_REPLY && Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE),
        )
    }

    private fun replyAction(storedId: String, ask: PendingAsk?, label: String, profile: String? = null): NotificationCompat.Action {
        val input = androidx.core.app.RemoteInput.Builder(NotifActionReceiver.KEY_REPLY).setLabel(label).build()
        return NotificationCompat.Action.Builder(0, label, actionIntent(NotifActionReceiver.ACTION_REPLY, storedId, ask, profile))
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .build()
    }

    fun cancelSessionNotifications() {
        nm.activeNotifications
            .filter { it.notification.channelId == CHANNEL_AGENT }
            .forEach { nm.cancel(it.id) }
    }

    private fun base(channel: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_hermes)
            .setColor(0xFFFFFFFF.toInt()) // putih — di atas background transparan
            // <26: sound default utk agent; 26+ channel yang pegang sound.
            .setSound(if (channel == CHANNEL_AGENT) android.media.RingtoneManager.getDefaultUri(
                android.media.RingtoneManager.TYPE_NOTIFICATION) else null)

    private fun mainIntent(openChat: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openChat != null) putExtra(EXTRA_OPEN_CHAT, openChat)
        }
        return PendingIntent.getActivity(
            context,
            openChat?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
