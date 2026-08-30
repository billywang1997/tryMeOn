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

    /** Morning: early enough to act on a price before it moves, late enough to be awake. */
    private val PRICE_ALERT_HOUR = LocalTime.of(9, 0)

    /** Evening: after the day it is asking you to log. */
    private val STREAK_REMINDER_HOUR = LocalTime.of(20, 0)

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
                // Anchored to a civil hour, not to whenever the app happened to
                // be installed. Six hours after a nine o'clock install is three
                // in the morning, and WorkManager keeps that offset for good —
                // so one unlucky install time meant a price alert at 3am every
                // day after.
                .setInitialDelay(initialDelayUntil(PRICE_ALERT_HOUR))
                .build()
            wm.enqueueUniquePeriodicWork(PRICE_WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork(PRICE_WORK)
        }

        if (settings.notifyStreakReminder) {
            val req = PeriodicWorkRequestBuilder<StreakReminderWorker>(Duration.ofHours(24))
                .setInitialDelay(initialDelayUntil(STREAK_REMINDER_HOUR))
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

    /** How long until the next [time] in the device's own timezone. */
    @androidx.annotation.VisibleForTesting
    internal fun initialDelayUntil(time: LocalTime, now: LocalDateTime = LocalDateTime.now()): Duration {
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.ofMillis(ChronoUnit.MILLIS.between(now, next))
    }
}
