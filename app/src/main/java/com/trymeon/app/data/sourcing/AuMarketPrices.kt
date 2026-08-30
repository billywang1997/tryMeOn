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
        val modifiers = words.dropLast(1).filterNot { it in COLOURS }

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
        if (namesFor(head).none { it in stems }) return false
        if (modifiers.isEmpty()) return true
        val hit = modifiers.count { stem(it) in stems }
        return hit * 2 >= modifiers.size
    }

    /**
     * Enough of a stem to survive a plural. Retail writes "Sneaker" as often as
     * "Sneakers" and nothing here needs to be cleverer than that.
     */
    private fun stem(word: String) = if (word.length > 3 && word.endsWith("s")) word.dropLast(1) else word

    /**
     * What Australian shops call the same garment.
     *
     * Measured, not guessed: on the same twenty results, "grey wool sweater"
     * kept two listings and found no benchmark while "grey wool jumper" kept
     * ten and priced it at A$105. The photo lookup and the stylist both write
     * American English, and the shops here do not.
     */
    /**
     * Colours, which are excluded from the comparison.
     *
     * Measured on real results, and the effect runs both ways. "navy ribbed
     * turtleneck sweater" kept three listings and produced no benchmark; the
     * same phrase without "navy" kept eleven and priced it at A$40. Worse,
     * "beige linen cropped blazer" kept eight — 67, 170, 180, 320, 592, 620,
     * 1310, 1430 — for a median of A$456, because the shops that bother to put
     * "beige" in a title are the expensive ones. Dropping the colour gave
     * eighteen listings and A$170.
     *
     * A wrong benchmark is worse than a missing one: it is the number the card
     * claims a saving against. Colour is also close to irrelevant to price,
     * which is what makes this safe as well as necessary.
     */
    private val COLOURS = setOf(
        "black", "white", "grey", "gray", "navy", "blue", "red", "green",
        "beige", "cream", "tan", "brown", "khaki", "olive", "charcoal",
        "pink", "purple", "yellow", "orange", "burgundy", "maroon", "teal",
        "ivory", "stone", "sand", "camel", "silver", "gold", "multicolour",
        "multicolor", "nude", "blush", "mint", "lilac", "coral"
    )

    private val ALSO_CALLED = listOf(
        setOf("sweater", "jumper", "pullover", "knit"),
        setOf("sneaker", "trainer", "runner"),
        setOf("pant", "trouser"),
        setOf("sweatpant", "trackpant", "jogger"),
        setOf("vest", "singlet"),
        setOf("purse", "handbag"),
        setOf("beanie", "bobblehat"),
        setOf("raincoat", "rainjacket"),
        setOf("swimsuit", "swimmer", "bather", "togs"),
        setOf("underwear", "undies"),
        setOf("sandal", "thong"),
        setOf("jumper dress", "pinafore")
    )

    /** The head noun and anything a local shop would call it instead. */
    private fun namesFor(head: String): Set<String> {
        val stemmed = stem(head)
        return ALSO_CALLED.firstOrNull { stemmed in it }?.map(::stem)?.toSet()
            ?: setOf(stemmed)
    }

    private fun parsePrice(raw: String): Double? =
        Regex("""\d[\d,]*(\.\d+)?""").find(raw)?.value?.replace(",", "")?.toDoubleOrNull()

    private companion object { const val TAG = "AuMarketPrices" }
}
