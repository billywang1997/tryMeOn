package com.trymeon.app.notifications

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a notification lands.
 *
 * WorkManager keeps the offset it is first given, so an initial delay measured
 * from installation is an initial delay measured from installation forever. The
 * price alert used to be "six hours from now": install at nine in the evening
 * and it arrives at three in the morning, every day, for as long as the app is
 * on the phone.
 */
class NotificationScheduleTest {

    private val nineAm = LocalTime.of(9, 0)

    @Test
    fun `an evening install still waits for the morning`() {
        val installedAt = LocalDateTime.of(2026, 8, 30, 21, 0)
        val delay = NotificationScheduler.initialDelayUntil(nineAm, installedAt)
        assertEquals(Duration.ofHours(12), delay)
    }

    @Test
    fun `an install before the hour waits only until it`() {
        val installedAt = LocalDateTime.of(2026, 8, 30, 7, 30)
        assertEquals(
            Duration.ofMinutes(90),
            NotificationScheduler.initialDelayUntil(nineAm, installedAt)
        )
    }

    @Test
    fun `installing exactly on the hour waits for tomorrow, not zero`() {
        // A zero delay would fire while the user is still in the installer.
        val installedAt = LocalDateTime.of(2026, 8, 30, 9, 0)
        assertEquals(
            Duration.ofHours(24),
            NotificationScheduler.initialDelayUntil(nineAm, installedAt)
        )
    }

    @Test
    fun `no install time produces a delay inside one day`() {
        listOf(LocalTime.of(9, 0), LocalTime.of(20, 0)).forEach { hour ->
            for (h in 0..23) {
                val d = NotificationScheduler.initialDelayUntil(
                    hour, LocalDateTime.of(2026, 8, 30, h, 17)
                )
                assertTrue("delay of $d for an install at $h:17", d <= Duration.ofHours(24))
                assertTrue("a notification must never be due immediately", d > Duration.ZERO)
            }
        }
    }
}
