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
     * The rule is not "every query word appears in the title". Measured against
     * real Google Shopping AU results, that produced a benchmark for one query
     * in five: retail titles lead with a brand and a model name and rarely
     * repeat the colour, so "black chunky sneakers" threw away all twenty
     * results — every one of them a chunky sneaker.
     *
     * What identifies the kind of thing is the head noun, and what narrows it
     * to something comparable is the modifiers. So: the last word of the query
     * must appear, and at least half of the rest. On the same results that
     * finds a benchmark for all five while still keeping a plain court shoe out
     * of the price of a chunky one.
     *
     * The currency filter is separate and load-bearing: an Australian search
     * still returns US sellers, whose prices are a number of a different kind
     * and would move the median the card publishes.
     */
    internal fun comparablePrices(results: List<EbayItem>, englishQuery: String): List<Double> {
        val words = queryWords(englishQuery)
        val head = words.lastOrNull()
        val modifiers = words.dropLast(1)

        return results
            .filter { head == null || matches(head, modifiers, PriceMatcher.tokens(it.title)) }
            .filter { it.currency.isBlank() || it.currency == "AUD" }
            .mapNotNull { parsePrice(it.price) }
    }

    /** Query words in the order written, which is where the head noun is. */
    private fun queryWords(query: String): List<String> {
        val noise = PriceMatcher.tokens(query)
        return query.lowercase()
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fff]+"), " ")
            .trim().split(" ")
            .filter { it in noise }
    }

    private fun matches(head: String, modifiers: List<String>, title: Set<String>): Boolean {
        val stems = title.map(::stem).toSet()
        if (stem(head) !in stems) return false
        if (modifiers.isEmpty()) return true
        val hit = modifiers.count { stem(it) in stems }
        return hit * 2 >= modifiers.size
    }

    /**
     * Enough of a stem to survive a plural. Retail writes "Sneaker" as often as
     * "Sneakers" and nothing here needs to be cleverer than that.
     */
    private fun stem(word: String) = if (word.length > 3 && word.endsWith("s")) word.dropLast(1) else word

    private fun parsePrice(raw: String): Double? =
        Regex("""\d[\d,]*(\.\d+)?""").find(raw)?.value?.replace(",", "")?.toDoubleOrNull()

    private companion object { const val TAG = "AuMarketPrices" }
}
