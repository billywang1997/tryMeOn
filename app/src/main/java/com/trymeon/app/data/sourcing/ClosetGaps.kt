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
    private val claude: ClaudeApiService,
    private val apiKey: String,
    private val priceHint: () -> String = { "" }
) {
    private var cachedFor: Int? = null
    private var cached: List<ClosetGap> = emptyList()

    suspend fun gaps(clothes: List<ClothingItem>, gender: String = ""): List<ClosetGap> {
        if (clothes.isEmpty() || apiKey.isBlank()) return emptyList()

        // Keyed on the wardrobe's contents: adding an item should change the advice.
        val hint = priceHint()
        val signature = (clothes.map { it.id to it.category } + hint).hashCode()
        if (signature == cachedFor) return cached

        val parsed = runCatching { parse(claude.closetGapQueries(apiKey, clothes, gender, hint)) }
            .getOrDefault(emptyList())
        if (parsed.isNotEmpty()) {
            cachedFor = signature
            cached = parsed
        }
        return parsed
    }

    internal companion object {
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
