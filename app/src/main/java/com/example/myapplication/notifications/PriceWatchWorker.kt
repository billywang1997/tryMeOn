package com.example.myapplication.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.AppSettings
import com.example.myapplication.data.local.DataStoreManager
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.sourcing.ShoppingCatalogFactory
import com.example.myapplication.data.repository.WishlistRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * Periodically refreshes wishlist prices via SerpAPI and posts a notification
 * if any item dropped by >= $1.
 */
class PriceWatchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = AppSettings(context)
        if (!settings.notifyPriceDrops) return Result.success()

        // Prices are re-checked where the items came from. Watching a Taobao
        // listing against an Australian shopping search compared two different
        // markets and would report a "drop" that was really a different product.
        val apiKey = settings.claudeApiKey
        if (apiKey.isBlank()) return Result.success()

        val store = DataStoreManager(context)
        val repo = WishlistRepository(store)
        val items = repo.observe().first()
        if (items.isEmpty()) return Result.success()

        val catalog = ShoppingCatalogFactory.create(
            context, ClaudeApiService(context), apiKey, settings.rapidApiKey
        )
        val drops = coroutineScope {
            items.map { item ->
                async {
                    val savedPrice = item.savedPrice.toDoubleOrNull() ?: return@async null
                    val q = item.query.ifBlank { item.title }
                    val results = catalog.search(q, limit = 12)
                    // Exact-title matching never fired against real search results;
                    // see PriceMatcher for what it does instead.
                    val newPrice = PriceMatcher.bestPrice(item.title, item.itemWebUrl, results)
                        ?.second
                        ?: return@async null
                    repo.update(item.copy(
                        lastSeenPrice = newPrice.toString(),
                        lastCheckedAt = System.currentTimeMillis()
                    ))
                    if (newPrice < savedPrice - 1.0) item to (savedPrice - newPrice) else null
                }
            }.awaitAll().filterNotNull()
        }

        if (drops.isNotEmpty()) {
            val top = drops.maxBy { it.second }
            val title = if (drops.size == 1)
                "💸 Price drop on ${top.first.title.take(30)}"
            else
                "💸 ${drops.size} wishlist items dropped"
            val body = "${top.first.title.take(40)} is down $%.0f — tap to view".format(top.second)
            NotificationHelper.show(
                context,
                notificationId = 4001,
                channelId = NotificationHelper.CHANNEL_PRICE_DROPS,
                title = title,
                body = body,
                deepLinkRoute = "wishlist"
            )
        }
        return Result.success()
    }
}
