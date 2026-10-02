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

    fun postAgent(d: NotifPolicy.Decision.Notify) {
        ensureChannels()
        val n = base(CHANNEL_AGENT)
            .setContentTitle(d.title)
            .setContentText(d.preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(d.preview))
            .setContentIntent(mainIntent(openChat = d.storedId))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // <26 fallback
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_AGENT)
            .build()
        // id numerik per session: notif baru utk session sama nimpah yang lama.
        nm.notify(d.storedId.hashCode(), n)
    }

    fun cancelSessionNotifications() {
        nm.activeNotifications
            .filter { it.notification.channelId == CHANNEL_AGENT }
            .forEach { nm.cancel(it.id) }
    }

    private fun base(channel: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
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
