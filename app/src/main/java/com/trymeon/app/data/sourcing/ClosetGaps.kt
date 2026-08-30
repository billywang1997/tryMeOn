package com.trymeon.app.data.sourcing

import com.trymeon.app.data.remote.ClaudeApiService
import com.trymeon.app.domain.model.ClothingItem

/** One thing the wardrobe is missing, ready to be searched for. */
data class ClosetGap(
    val query: String,
    val reason: String,
    /** Written alongside the suggestion, so searching it costs no extra call. */
    val chineseQuery: String = ""
)

/**
 * Reads the wardrobe and proposes what to buy.
 *
 * This is what makes the sourcing tab worth opening with nothing in mind: the
 * app already knows what the user owns, so the blank search box is a wasted
 * question. Suggestions are cached per wardrobe so browsing back and forth does
 * not re-spend on the same answer.
 */
class ClosetGapService(
    private val claude: ClaudeApiService?,
    private val apiKey: String,
    private val priceHint: () -> String = { "" },
    /**
     * How the advice is fetched. Injectable so the caching behaviour can be
     * tested without a Context or a paid call — the thing worth checking here
     * is how often this runs, not what it returns.
     */
    private val ask: suspend (List<ClothingItem>, String, String) -> String =
        { clothes, gender, hint ->
            claude?.closetGapQueries(apiKey, clothes, gender, hint).orEmpty()
        }
) {

    suspend fun gaps(clothes: List<ClothingItem>, gender: String = ""): List<ClosetGap> {
        if (clothes.isEmpty() || apiKey.isBlank()) return emptyList()

        // Keyed on the wardrobe's contents: adding an item should change the advice.
        val hint = priceHint()
        val signature = (clothes.map { it.id to it.category } + hint).hashCode()
        cache[signature]?.let { return it }

        val parsed = runCatching { parse(ask(clothes, gender, hint)) }
            .getOrDefault(emptyList())
        if (parsed.isNotEmpty()) synchronized(cache) {
            // Bounded: a wardrobe has a handful of meaningful states, and this
            // never needs to outlive the process.
            if (cache.size >= MAX_CACHED) cache.remove(cache.keys.first())
            cache[signature] = parsed
        }
        return parsed
    }

    internal companion object {
        /**
         * Shared across instances, and so across navigation.
         *
         * The service used to hold its own cache, but it is constructed inside a
         * `composable { remember(...) }`: leaving the tab disposes it, and coming
         * back builds a new one with an empty cache. Every visit to the shopping
         * tab therefore paid for the same advice again — visibly so, since the
         * model returns a different four items each time.
         */
        private val cache = LinkedHashMap<Int, List<ClosetGap>>()
        private const val MAX_CACHED = 16

        /** Process-wide by design, so a test must be able to start from empty. */
        @androidx.annotation.VisibleForTesting
        fun clearCache() = synchronized(cache) { cache.clear() }

        fun parse(raw: String): List<ClosetGap> = raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split("|")
                if (parts.firstOrNull()?.uppercase() != "GAP") return@mapNotNull null
                val query = parts.getOrNull(1)?.trim().orEmpty()
                if (query.isEmpty()) return@mapNotNull null
                ClosetGap(
                    query = query,
                    reason = parts.getOrNull(2)?.trim().orEmpty(),
                    chineseQuery = parts.getOrNull(3)?.trim().orEmpty()
                )
            }
            .take(4)
            .toList()
    }
}
