package com.example.myapplication.domain.sourcing

/**
 * How the price of a recommendation should sit against what the same kind of
 * thing costs in Australia.
 *
 * This is the user's own word on the matter, set once in Profile. Without it
 * the shop strips showed whatever Taobao returned first, and Taobao's first
 * page is sorted for Chinese shoppers on a Chinese salary — which is how a
 * "complete the look" for a t-shirt ended up recommending A$180 shoes. Every
 * ranking and every stylist prompt reads this, so the four surfaces that shop
 * agree on what "affordable" means.
 *
 * Ratios are landed AUD over the local typical price from [MarketBenchmark].
 * When no benchmark exists the ranker falls back to a share of the result
 * pool instead, so the setting still does something offline.
 */
enum class PriceExpectation(
    val label: String,
    val blurb: String,
    /** Landed / local may not exceed this. Null means no ceiling. */
    val maxRatio: Double?,
    /** Landed / local should not fall below this — a floor against junk. Null means none. */
    val minRatio: Double?,
    /** Without a benchmark: keep this share of the pool, cheapest first. */
    val keepCheapestShare: Double,
    /** Without a benchmark: drop this share from the bottom first, as a junk floor. */
    val dropCheapestShare: Double,
    /** Vocabulary Taobao sellers use at this price point, fed into the search phrase. */
    val sellerVocabulary: String,
    /** One line for the stylist prompts, so the *kind* of piece suggested fits the budget. */
    val stylistHint: String
) {
    FAR_BELOW_LOCAL(
        label = "Far below local",
        blurb = "A fraction of Australian prices — basics and dupes, not labels",
        maxRatio = 0.45, minRatio = null,
        keepCheapestShare = 0.40, dropCheapestShare = 0.0,
        sellerVocabulary = "平价 基础款 百搭 学生党",
        stylistHint = "Budget: far below Australian retail. Suggest simple, well-made basics in plain fabrics — no designer cuts, leather, or anything that only looks right when expensive."
    ),
    BELOW_LOCAL(
        label = "Below local",
        blurb = "Clearly cheaper than buying here, still decent quality",
        maxRatio = 0.80, minRatio = null,
        keepCheapestShare = 0.60, dropCheapestShare = 0.05,
        sellerVocabulary = "性价比 高品质 百搭",
        stylistHint = "Budget: noticeably below Australian retail. Favour good-value staples and everyday pieces; avoid items that depend on premium materials."
    ),
    NEAR_LOCAL(
        label = "Around local",
        blurb = "Similar to Australian prices — pay for the right piece",
        maxRatio = 1.20, minRatio = 0.35,
        keepCheapestShare = 0.85, dropCheapestShare = 0.20,
        sellerVocabulary = "品质 精选 正品",
        stylistHint = "Budget: around Australian retail. Quality and fit matter more than price; suggest pieces worth paying properly for."
    ),
    ABOVE_LOCAL_OK(
        label = "Above local is fine",
        blurb = "Happy to pay more for the exact right thing",
        maxRatio = null, minRatio = 0.50,
        keepCheapestShare = 1.0, dropCheapestShare = 0.30,
        sellerVocabulary = "高端 优质 真皮 设计感",
        stylistHint = "Budget: not a constraint. Suggest the best-looking, best-made option — premium fabrics, considered cuts, standout pieces."
    );

    companion object {
        /** Sourcing from Taobao only makes sense if it is cheaper, so that is the default. */
        val DEFAULT = BELOW_LOCAL

        fun fromName(name: String?): PriceExpectation =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
