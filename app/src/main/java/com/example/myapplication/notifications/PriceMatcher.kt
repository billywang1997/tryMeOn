package com.example.myapplication.notifications

import com.example.myapplication.data.remote.EbayItem

/**
 * Decides whether a search result is the same product as a saved wishlist item.
 *
 * The original rule was an exact title match, which never fires: a listing
 * saved as "Nike Air Force 1" comes back from a shopping search as
 * "Nike Air Force 1 07 Men's" or "Nike Air Force 1 '07 LV8 Men's". Price watch
 * therefore ran, reported success, and silently never noticed a price change.
 *
 * The rule here is containment — every meaningful word of the saved title must
 * appear in the candidate — which is deliberately conservative in one
 * direction. A missed price drop is a shame; a false one tells someone their
 * item is cheap when a different product is, which is worse than saying nothing.
 */
object PriceMatcher {

    /** Words that appear in half of all listings and carry no identity. */
    private val NOISE = setOf(
        "the", "a", "an", "and", "or", "for", "with", "new", "men", "mens", "men's",
        "women", "womens", "women's", "shoe", "shoes", "size", "au", "us", "uk"
    )

    internal fun tokens(title: String): Set<String> = title
        .lowercase()
        .replace(Regex("[^a-z0-9\\u4e00-\\u9fff]+"), " ")
        .trim()
        .split(" ")
        // Stripping punctuation turns "men's" into "men" and a stray "s".
        // A lone letter carries no identity, and leaving it in let a title made
        // entirely of noise match anything that happened to contain an "s".
        .filter { it.length > 1 || it.first().isDigit() }
        .filter { it !in NOISE }
        .toSet()

    /**
     * The best current price for [savedTitle] among [candidates], or null when
     * nothing is confidently the same product.
     *
     * An exact URL match wins outright — that is the same listing, whatever it
     * is called this week.
     */
    fun bestPrice(
        savedTitle: String,
        savedUrl: String,
        candidates: List<EbayItem>
    ): Pair<EbayItem, Double>? {
        if (candidates.isEmpty()) return null

        fun priced(item: EbayItem) = item.price.parsePrice()?.takeIf { it > 0 }

        if (savedUrl.isNotBlank()) {
            candidates.firstOrNull { it.itemWebUrl == savedUrl }?.let { same ->
                priced(same)?.let { return same to it }
            }
        }

        val wanted = tokens(savedTitle)
        // Nothing distinctive to match on; refusing beats guessing.
        if (wanted.isEmpty()) return null

        return candidates
            .mapNotNull { c -> priced(c)?.let { c to it } }
            .filter { (c, _) -> tokens(c.title).containsAll(wanted) }
            // The point of watching is the best price available for the item.
            .minByOrNull { it.second }
    }

    /** Prices arrive as "$136.00", "136", "A$1,299.00". */
    private fun String.parsePrice(): Double? =
        Regex("""\d[\d,]*(\.\d+)?""").find(this)?.value?.replace(",", "")?.toDoubleOrNull()
}
