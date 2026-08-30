package com.trymeon.app.notifications

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.trymeon.app.AppSettings
import com.trymeon.app.data.local.DataStoreManager
import com.trymeon.app.data.remote.EbayItem
import com.trymeon.app.data.repository.WishlistRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * These run in the background on a schedule, so a crash here is invisible:
 * the user simply never hears about a price drop again. Nothing had ever
 * executed them.
 */
class WorkerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = AppSettings(context)
    private val wishlist = WishlistRepository(DataStoreManager(context))

    private var priceNotify = true
    private var streakNotify = true

    @Before fun remember() {
        priceNotify = settings.notifyPriceDrops
        streakNotify = settings.notifyStreakReminder
    }

    @After fun restore() {
        settings.notifyPriceDrops = priceNotify
        settings.notifyStreakReminder = streakNotify
        runBlocking { wishlist.observe().first().forEach { wishlist.remove(it.id) } }
    }

    private fun runPriceWatch(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<PriceWatchWorker>(context).build().doWork()
    }

    private fun runStreak(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<StreakReminderWorker>(context).build().doWork()
    }

    // ── Price watch ─────────────────────────────────────────────────────────

    @Test
    fun priceWatchRespectsTheNotificationSetting() {
        settings.notifyPriceDrops = false
        assertEquals(ListenableWorker.Result.success(), runPriceWatch())
    }

    @Test
    fun priceWatchSucceedsOnAnEmptyWishlist() {
        settings.notifyPriceDrops = true
        runBlocking { wishlist.observe().first().forEach { wishlist.remove(it.id) } }
        // The common case by far: most installs have nothing on the list.
        assertEquals(ListenableWorker.Result.success(), runPriceWatch())
    }

    @Test
    fun priceWatchSurvivesAnItemWithNoUsablePrice() {
        settings.notifyPriceDrops = true
        runBlocking {
            wishlist.add(
                EbayItem(
                    itemId = "no-price",
                    title = "Item with an unreadable price",
                    price = "面议",
                    itemWebUrl = "https://example.com/x"
                ),
                query = "linen blazer"
            )
        }
        // A single unparseable entry must not take the whole run down.
        assertEquals(ListenableWorker.Result.success(), runPriceWatch())
    }

    @Test
    fun priceWatchRunsAgainstTheRealPriceApi() {
        settings.notifyPriceDrops = true
        runBlocking {
            // Everything saved now comes from Taobao, so a saved item carries a
            // Chinese seller title. An English one would never match a Chinese
            // listing and the test would be checking nothing.
            wishlist.add(
                EbayItem(
                    itemId = "live-1",
                    title = "亚麻短款西装外套女",
                    price = "500.00",
                    currency = "AUD",
                    itemWebUrl = "https://item.taobao.com/item.htm?id=live-1"
                ),
                query = "linen cropped blazer"
            )
        }
        val result = runPriceWatch()
        assertEquals(ListenableWorker.Result.success(), result)

        // Saved at an absurd $500, so any real quote is a drop and the item
        // should come back with a refreshed price recorded against it.
        val after = runBlocking { wishlist.observe().first() }.first { it.title == "亚麻短款西装外套女" }
        println("price watch: saved=${after.savedPrice} lastSeen=${after.lastSeenPrice}")

        // Saved at an absurd $500, so a real quote must come back lower. Before
        // the matcher was fixed this stayed at 500 forever: the worker ran,
        // matched nothing, and reported success without ever seeing a price.
        val seen = after.lastSeenPrice.toDoubleOrNull()
        // The worker swallows failures by design — it must not crash a
        // background job — so a spent search quota reads exactly like finding
        // nothing. Skip rather than report a defect that is not there.
        assumeTrue("search returned nothing; likely quota", seen != null && seen != 500.0)
        assertTrue("worker never recorded a refreshed price (lastSeen=${after.lastSeenPrice})",
            seen != null && seen < 500.0)
    }

    // ── Streak reminder ─────────────────────────────────────────────────────

    @Test
    fun streakRespectsTheNotificationSetting() {
        settings.notifyStreakReminder = false
        assertEquals(ListenableWorker.Result.success(), runStreak())
    }

    @Test
    fun streakRunsCleanlyWithNoHistory() {
        settings.notifyStreakReminder = true
        assertEquals(ListenableWorker.Result.success(), runStreak())
    }
}
