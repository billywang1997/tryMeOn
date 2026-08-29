package com.example.myapplication.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.AppSettings
import com.example.myapplication.data.local.DataStoreManager
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Runs once a day (~8pm). If user hasn't logged today's outfit, posts a gentle reminder.
 */
class StreakReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = AppSettings(context)
        if (!settings.notifyStreakReminder) return Result.success()

        val store = DataStoreManager(context)
        val logs = store.outfitLogsFlow.first()
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (logs.any { it.date == today }) return Result.success()

        // Compute current streak ending yesterday
        val yesterdayLogged = logs.any { it.date == LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE) }
        val (title, body) = if (yesterdayLogged) {
            "🔥 Keep your streak alive" to "You haven't logged today's outfit yet. Tap to add it."
        } else {
            "✨ Today's outfit?" to "Log what you wore to start a fresh streak."
        }
        NotificationHelper.show(
            context,
            notificationId = 4002,
            channelId = NotificationHelper.CHANNEL_STREAK,
            title = title,
            body = body,
            deepLinkRoute = "streak"
        )
        return Result.success()
    }
}
