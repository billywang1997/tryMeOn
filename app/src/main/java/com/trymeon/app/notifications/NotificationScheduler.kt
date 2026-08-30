package com.trymeon.app.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.trymeon.app.AppSettings
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

object NotificationScheduler {

    private const val PRICE_WORK = "wardrobe_price_watch"
    private const val STREAK_WORK = "wardrobe_streak_reminder"

    fun apply(context: Context) {
        val settings = AppSettings(context)
        val wm = WorkManager.getInstance(context)

        if (settings.notifyPriceDrops) {
            val req = PeriodicWorkRequestBuilder<PriceWatchWorker>(Duration.ofHours(24))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(Duration.ofHours(6))
                .build()
            wm.enqueueUniquePeriodicWork(PRICE_WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork(PRICE_WORK)
        }

        if (settings.notifyStreakReminder) {
            val req = PeriodicWorkRequestBuilder<StreakReminderWorker>(Duration.ofHours(24))
                .setInitialDelay(initialDelayUntil(LocalTime.of(20, 0)))
                .build()
            wm.enqueueUniquePeriodicWork(STREAK_WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork(STREAK_WORK)
        }
    }

    /** Re-apply work — call after a settings toggle change to update WorkManager state. */
    fun reapply(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(PRICE_WORK)
        wm.cancelUniqueWork(STREAK_WORK)
        apply(context)
    }

    private fun initialDelayUntil(time: LocalTime): Duration {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.ofMillis(ChronoUnit.MILLIS.between(now, next))
    }
}
