package com.trymeon.app.data.sourcing

import android.util.Log
import com.trymeon.app.data.remote.EbayItem
import com.trymeon.app.data.remote.SerpApiService
import com.trymeon.app.domain.sourcing.MarketBenchmark
import com.trymeon.app.notifications.PriceMatcher
import java.util.concurrent.TimeUnit

/**
 * What comparable items cost in Australia.
 *
 * A local shopping search is the wrong tool for re-pricing a specific Taobao
 * listing — that compares two markets and calls the difference a price drop.
 * It is the right tool for the question actually being asked here: what would
 * this kind of thing cost if you bought it locally.
 *
 * Cached per query, because the answer moves slowly and the search is metered.
 */
class AuMarketPrices(
    private val serp: SerpApiService,
    private val serpApiKey: String,
    private val ttlMillis: Long = TimeUnit.DAYS.toMillis(7),
    private val now: () -> Long = System::currentTimeMillis
) {
    private data class Entry(val benchmark: MarketBenchmark?, val at: Long)

    private val cache = HashMap<String, Entry>()

    val available: Boolean get() = serpApiKey.isNotBlank()

    /** Null when there is no key, no signal, or too thin a sample to be a market. */
    suspend fun benchmark(englishQuery: String): MarketBenchmark? {
        if (!available || englishQuery.isBlank()) return null

        val key = englishQuery.trim().lowercase().replace(Regex("\\s+"), " ")
        synchronized(cache) {
            cache[key]?.takeIf { now() - it.at < ttlMillis }?.let { return it.benchmark }
        }

        val benchmark = runCatching {
            val results = serp.search(serpApiKey, englishQuery, limit = 20).getOrNull().orEmpty()
            // Only listings that are plausibly the same kind of thing. Without
            // this a search for a blazer prices itself against phone cases.
            MarketBenchmark.from(comparablePrices(results, englishQuery))
        }.onFailure { Log.w(TAG, "benchmark '$englishQuery' failed: ${it.message}") }
            .getOrNull()

        synchronized(cache) { cache[key] = Entry(benchmark, now()) }
        return benchmark
    }

    /**
     * The prices from [results] that belong in a median for [englishQuery].
     *
     * Two filters, both load-bearing. Without the title filter a search for a
     * blazer prices itself against phone cases. Without the currency filter a
     * US seller on the Australian results page contributes a number of a
     * different kind, pulling the median down and the claimed saving with it.
     */
    internal fun comparablePrices(results: List<EbayItem>, englishQuery: String): List<Double> {
        val wanted = PriceMatcher.tokens(englishQuery)
        return results
            .filter { wanted.isEmpty() || PriceMatcher.tokens(it.title).containsAll(wanted) }
            .filter { it.currency.isBlank() || it.currency == "AUD" }
            .mapNotNull { parsePrice(it.price) }
    }

    private fun parsePrice(raw: String): Double? =
        Regex("""\d[\d,]*(\.\d+)?""").find(raw)?.value?.replace(",", "")?.toDoubleOrNull()

    private companion object { const val TAG = "AuMarketPrices" }
}
