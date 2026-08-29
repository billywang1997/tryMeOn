package com.example.myapplication.data.sourcing

import android.util.Log
import com.example.myapplication.data.remote.SerpApiService
import com.example.myapplication.domain.sourcing.MarketBenchmark
import com.example.myapplication.notifications.PriceMatcher
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
            val wanted = PriceMatcher.tokens(englishQuery)
            val comparable = results
                .filter { wanted.isEmpty() || PriceMatcher.tokens(it.title).containsAll(wanted) }
                .mapNotNull { parsePrice(it.price) }
            MarketBenchmark.from(comparable)
        }.onFailure { Log.w(TAG, "benchmark '$englishQuery' failed: ${it.message}") }
            .getOrNull()

        synchronized(cache) { cache[key] = Entry(benchmark, now()) }
        return benchmark
    }

    private fun parsePrice(raw: String): Double? =
        Regex("""\d[\d,]*(\.\d+)?""").find(raw)?.value?.replace(",", "")?.toDoubleOrNull()

    private companion object { const val TAG = "AuMarketPrices" }
}
