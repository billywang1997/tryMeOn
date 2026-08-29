package com.example.myapplication.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.AppSettings
import com.example.myapplication.data.local.DataStoreManager
import com.example.myapplication.data.remote.SerpApiService
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

        val serpKey = settings.serpApiKey
        if (serpKey.isBlank()) return Result.success()

        val store = DataStoreManager(context)
        val repo = WishlistRepository(store)
        val items = repo.observe().first()
        if (items.isEmpty()) return Result.success()

        val service = SerpApiService()
        val drops = coroutineScope {
            items.map { item ->
                async {
                    val savedPrice = item.savedPrice.toDoubleOrNull() ?: return@async null
                    val q = item.query.ifBlank { item.title }
                    val newPrice = service.search(serpKey, q, limit = 5)
                        .getOrNull()
                        ?.firstOrNull { it.title.equals(item.title, ignoreCase = true) || it.itemWebUrl == item.itemWebUrl }
                        ?.price
                        ?.toDoubleOrNull()
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
