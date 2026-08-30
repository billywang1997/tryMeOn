package com.trymeon.app.data.sourcing

import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.sourcing.MarketBenchmark
import com.trymeon.app.domain.sourcing.PriceExpectation
import kotlin.math.abs
import kotlin.math.ln

/**
 * Turns a page of Taobao results into the handful worth showing.
 *
 * Search returns whatever matched the phrase, in Taobao's order, which is
 * tuned for a different shopper. This is where the user's own constraints get
 * applied: the price band they asked for, their gender, and the category the
 * strip is meant to be — so a search for shoes does not surface shoe insoles,
 * and a woman's strip does not fill with men's sizes.
 *
 * Pure and synchronous on purpose: it runs after the paid search and before
 * the screen, and can be tested against any pool without spending a request.
 */
object ListingRanker {

    data class Outcome(
        val items: List<SourcedItem>,
        /** How the band was decided, for logs and tests. */
        val note: String
    )

    /** Fewer than this and the band is relaxed rather than the strip left empty. */
    const val MIN_RESULTS = 3

    fun rank(
        pool: List<SourcedItem>,
        expectation: PriceExpectation,
        benchmark: MarketBenchmark?,
        gender: String = "",
        category: ClothingCategory? = null,
        limit: Int = 12
    ): Outcome {
        if (pool.isEmpty()) return Outcome(emptyList(), "empty pool")

        val clean = pool
            .filter { !genderMismatch(it.listing.title, gender) }
            .filter { !categoryJunk(it.listing.title, category) }
            // By id, not title prefix: half of Taobao starts with "2026新款春季女装".
            .distinctBy { it.listing.itemId.ifEmpty { it.listing.title } }
            .ifEmpty { pool } // a filter that removes everything is a bad filter

        val (inBand, note) = band(clean, expectation, benchmark)

        val scored = inBand
            .map { it to score(it, expectation, benchmark, inBand) }
            .sortedByDescending { it.second }
            .map { it.first }

        return Outcome(scored.take(limit), note)
    }

    // ── band ────────────────────────────────────────────────────────────────

    private fun band(
        items: List<SourcedItem>,
        expectation: PriceExpectation,
        benchmark: MarketBenchmark?
    ): Pair<List<SourcedItem>, String> {
        val typical = benchmark?.typicalAud?.takeIf { it > 0 }
        if (typical != null) {
            val max = expectation.maxRatio?.let { typical * it } ?: Double.MAX_VALUE
            val min = expectation.minRatio?.let { typical * it } ?: 0.0
            val fit = items.filter { it.bestTotalAud in min..max }
            if (fit.size >= MIN_RESULTS) {
                return fit to "benchmark A$%.0f, band A$%.0f–%s".format(
                    typical, min, if (max == Double.MAX_VALUE) "∞" else "A$%.0f".format(max)
                )
            }
            // Not enough inside the band: take the nearest to it rather than nothing.
            val nearest = items.sortedBy { distanceToBand(it.bestTotalAud, min, max) }
                .take(maxOf(MIN_RESULTS, fit.size))
            return nearest to "benchmark A$%.0f, only ${fit.size} in band — relaxed to nearest".format(typical)
        }

        // No local benchmark: the pool itself is the only scale we have.
        val byPrice = items.sortedBy { it.bestTotalAud }
        val dropLow = (byPrice.size * expectation.dropCheapestShare).toInt()
        val keep = (byPrice.size * expectation.keepCheapestShare).toInt()
        val window = byPrice.drop(dropLow).take(maxOf(keep - dropLow, MIN_RESULTS))
        return window.ifEmpty { byPrice.take(MIN_RESULTS) } to
            "no benchmark, kept ${window.size}/${byPrice.size} by pool share"
    }

    private fun distanceToBand(price: Double, min: Double, max: Double): Double = when {
        price < min -> min - price
        price > max -> price - max
        else -> 0.0
    }

    // ── score ───────────────────────────────────────────────────────────────

    private fun score(
        item: SourcedItem,
        expectation: PriceExpectation,
        benchmark: MarketBenchmark?,
        peers: List<SourcedItem>
    ): Double {
        val price = item.bestTotalAud
        val lo = peers.minOf { it.bestTotalAud }
        val hi = peers.maxOf { it.bestTotalAud }
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val position = (price - lo) / span // 0 = cheapest peer, 1 = dearest

        val priceFit = when (expectation) {
            PriceExpectation.FAR_BELOW_LOCAL -> 1.0 - position
            PriceExpectation.BELOW_LOCAL -> 1.0 - position * 0.7
            PriceExpectation.NEAR_LOCAL -> {
                val typical = benchmark?.typicalAud
                if (typical != null && typical > 0) 1.0 - minOf(1.0, abs(price - typical) / typical)
                else 1.0 - abs(position - 0.5) * 2 // middle of the pool
            }
            PriceExpectation.ABOVE_LOCAL_OK -> 0.5 + position * 0.5 // dearer reads as better made
        }

        // Units sold is the one trust signal Taobao gives away; log so that a
        // listing with 30,000 sales does not drown one with 300.
        val trust = ln(1.0 + item.listing.sold) / ln(1.0 + 20_000.0)
        val coupon = if (item.listing.couponCny > 0) 1.0 else 0.0

        return priceFit * 0.55 + trust.coerceIn(0.0, 1.0) * 0.35 + coupon * 0.10
    }

    // ── sanity filters ──────────────────────────────────────────────────────

    internal fun genderMismatch(title: String, gender: String): Boolean {
        val unisex = title.contains("男女") || title.contains("情侣") || title.contains("中性")
        if (unisex) return false
        return when (gender.trim().lowercase()) {
            "female", "woman", "f" -> title.contains("男") && !title.contains("女")
            "male", "man", "m" -> title.contains("女") && !title.contains("男")
            else -> false
        }
    }

    private val junkByCategory: Map<ClothingCategory, List<String>> = mapOf(
        ClothingCategory.SHOES to listOf("鞋垫", "鞋带", "鞋套", "鞋撑", "鞋油", "鞋刷", "增高垫", "鞋柜", "鞋盒", "袜"),
        ClothingCategory.PANTS to listOf("皮带", "腰带", "裤架", "裤夹", "裤袜"),
        ClothingCategory.INNER to listOf("衣架", "收纳", "贴纸", "胸贴"),
        ClothingCategory.OUTERWEAR to listOf("衣架", "收纳", "防尘罩"),
        ClothingCategory.DRESS to listOf("衣架", "收纳", "裙撑"),
        ClothingCategory.BAG to listOf("包挂", "挂件", "内胆", "肩带", "收纳袋", "防尘袋"),
        ClothingCategory.ACCESSORY to listOf("收纳盒", "展示架")
    )

    internal fun categoryJunk(title: String, category: ClothingCategory?): Boolean {
        val junk = junkByCategory[category ?: return false] ?: return false
        return junk.any { title.contains(it) }
    }
}
