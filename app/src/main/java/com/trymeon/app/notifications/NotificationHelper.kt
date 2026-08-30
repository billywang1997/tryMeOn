package com.trymeon.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.trymeon.app.MainActivity
import com.trymeon.app.R

object NotificationHelper {

    const val CHANNEL_PRICE_DROPS = "price_drops"
    const val CHANNEL_STREAK = "streak_reminders"
    const val CHANNEL_GENERAL = "general"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PRICE_DROPS, "Price drops", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerts when items on your wishlist drop in price"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STREAK, "Streak reminders", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Daily reminder to log today's outfit"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_GENERAL, "General", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun show(
        context: Context,
        notificationId: Int,
        channelId: String,
        title: String,
        body: String,
        deepLinkRoute: String? = null
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (deepLinkRoute != null) putExtra("deep_link_route", deepLinkRoute)
        }
        val pending = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+
        }
    }
}
