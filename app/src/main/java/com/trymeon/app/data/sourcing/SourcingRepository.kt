package com.trymeon.app.data.sourcing

import com.trymeon.app.data.remote.FxRate
import com.trymeon.app.data.remote.ProductSearch
import com.trymeon.app.data.remote.SearchUnavailable
import com.trymeon.app.data.remote.TaobaoItem
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.sourcing.DaigouAgent
import com.trymeon.app.domain.sourcing.GstTreatment
import com.trymeon.app.domain.sourcing.LandedCost
import com.trymeon.app.domain.sourcing.LandedCostCalculator
import com.trymeon.app.domain.sourcing.LandedCostInput
import com.trymeon.app.domain.sourcing.Parcel
import com.trymeon.app.domain.sourcing.ShippingLine
import com.trymeon.app.domain.sourcing.ShippingRoute
import com.trymeon.app.domain.sourcing.SourcingDefaults
import com.trymeon.app.domain.sourcing.VolumetricEscape
import com.trymeon.app.util.Daigou

/** A listing with a usable price. Quoting happens separately so it can be redone for free. */
data class SourcedListing(
    val listing: TaobaoItem,
    val priceCny: Double
) {
    val key: String get() = listing.itemId.ifEmpty { listing.title.take(24) }
}

/** What one shipping line costs for one listing. */
data class RouteQuote(
    val line: ShippingLine,
    val cost: LandedCost,
    /** CNY still needed to unlock this line's free-shipping threshold, if any. */
    val addToFreeShippingCny: Double?,
    /** Extra grams that would drop this parcel off volumetric billing, if any. */
    val volumetricEscape: VolumetricEscape?
) {
    val isOfficial: Boolean get() = line.route == ShippingRoute.OFFICIAL_DIRECT
}

/**
 * One listing priced on every route we know.
 *
 * Quoting all of them rather than making the user pick is deliberate. A listing
 * page tells an overseas buyer nothing about which route is open to them or what
 * it costs, and the spread here is not marginal — Taobao sea freight charges ¥7
 * per extra half kilo against air's ¥53. The answer should be on screen before
 * anyone opens a settings menu.
 */
data class SourcedItem(
    val listing: TaobaoItem,
    val priceCny: Double,
    /** Every route, cheapest first. */
    val quotes: List<RouteQuote>,
    val orderUrl: String?
) {
    val best: RouteQuote get() = quotes.first()
    val bestTotalAud: Double get() = best.cost.totalAud
    /** What the most expensive route would have cost — the spread worth showing. */
    val worstTotalAud: Double get() = quotes.last().cost.totalAud
    val spreadAud: Double get() = worstTotalAud - bestTotalAud
}

data class SourcingResult(
    val query: SourcingQuery,
    val listings: List<SourcedListing>,
    /** Which of the query's phrases actually returned results. */
    val usedQuery: String,
    /** The rate every conversion uses, so the UI can show its age. */
    val fxRate: FxRate
)

/**
 * Turns listings into landed prices. Pure and cheap, so changing the agent
 * re-prices instantly instead of re-running a paid search.
 */
object SourcingQuoter {

    fun quote(
        result: SourcingResult,
        agent: DaigouAgent = SourcingDefaults.defaultAgent,
        quantity: Int = 1,
        gst: GstTreatment = GstTreatment.LOW_VALUE_COLLECTED,
        cardSettlementPercent: Double = SourcingDefaults.DEFAULT_CARD_SETTLEMENT_PERCENT,
        lines: List<ShippingLine> = SourcingDefaults.lines
    ): List<SourcedItem> = result.listings.map { sourced ->
        // Decided per listing, not per search: a result can mix a marketplace
        // that delivers with one that quotes a domestic price, and pricing the
        // first with the second's freight would invent a cost nobody pays.
        val effectiveLines =
            if (sourced.listing.deliveredPrice) listOf(SourcingDefaults.platformQuoted) else lines
        val quotes = effectiveLines.map { line ->
            // Taobao's own route has no forwarder in it, so it carries no
            // service or payment fee whatever agent the user has picked.
            val effectiveAgent =
                if (line.route == ShippingRoute.OFFICIAL_DIRECT) SourcingDefaults.noAgent else agent
            val input = LandedCostInput(
                itemPriceCny = sourced.priceCny,
                quantity = quantity,
                parcel = result.query.parcel,
                agent = effectiveAgent,
                line = line,
                // A source that quotes in the buyer's own currency needs no
                // conversion; applying the CNY rate would cut the price to a
                // fifth of what the seller actually charges.
                cnyToAud = if (sourced.listing.currency == "AUD") 1.0 else result.fxRate.rate,
                gst = gst,
                cardSettlementPercent = cardSettlementPercent
            )
            RouteQuote(
                line = line,
                cost = LandedCostCalculator.calculate(input),
                addToFreeShippingCny = LandedCostCalculator.addToFreeShippingCny(input),
                volumetricEscape = LandedCostCalculator.volumetricEscape(input)
            )
        }.sortedBy { it.cost.totalAud }

        SourcedItem(
            listing = sourced.listing,
            priceCny = sourced.priceCny,
            quotes = quotes,
            // A seller that ships to the buyer is ordered from directly. Wrapping
            // its link in a forwarding agent's template would send the buyer to an
            // agent they do not need, and would destroy the affiliate attribution
            // the link carries.
            orderUrl = if (sourced.listing.deliveredPrice) {
                sourced.listing.itemUrl.ifBlank { null }
            } else {
                Daigou.orderUrl(sourced.listing.itemUrl)
            }
        )
    }
}

/**
 * English wish → Chinese search → listings priced to an Australian door.
 */
class SourcingRepository(
    /**
     * Sources in preference order. The affiliate API goes first because it is
     * official and pays commission; the scraper is what keeps the feature
     * working until an Alimama account exists.
     */
    private val sources: List<ProductSearch>,
    private val queryBuilder: SourcingQueryBuilder,
    /** Null falls back to the compiled-in rate — acceptable in tests, not in the app. */
    private val fxRates: FxRateRepository? = null
) {

    suspend fun source(
        englishDescription: String,
        gender: String = "",
        categoryHint: ClothingCategory? = null,
        quantity: Int = 1
    ): Result<SourcingResult> {
        val query = queryBuilder.build(englishDescription, gender, categoryHint)
            .getOrElse { return Result.failure(it) }

        val fx = fxRates?.current() ?: FxRate(
            rate = SourcingDefaults.FALLBACK_CNY_TO_AUD,
            fetchedAtMillis = 0L,
            source = "indicative",
            isFallback = true
        )

        // Try each phrase in turn: the best-match phrase is also the narrowest,
        // and an empty grid is worse than a looser match. Sources are tried in
        // order per phrase so a configured affiliate API always wins.
        var used = ""
        var listings: List<TaobaoItem> = emptyList()
        var blocked: Throwable? = null
        val usable = sources.filter { it.available }
        // Nothing to search with is a different answer from nothing found, and
        // telling a shopper their words did not match when we never looked
        // sends them off rewording a query that was never the problem.
        if (usable.isEmpty()) {
            return Result.failure(
                SearchUnavailable("Product search is not set up on this build yet")
            )
        }
        outer@ for (phrase in query.chineseQueries) {
            for (source in usable) {
                val attempt = source.search(phrase)
                // Remember why we could not search, so an exhausted quota is not
                // reported to the user as an unlucky search.
                attempt.exceptionOrNull()?.let { if (blocked == null) blocked = it }
                val hit = attempt.getOrNull().orEmpty()
                if (hit.isNotEmpty()) {
                    used = phrase
                    listings = hit
                    break@outer
                }
            }
        }
        if (listings.isEmpty()) {
            return Result.failure(
                blocked ?: NoSuchElementException("Nothing matched that — try fewer words")
            )
        }

        val priced = listings.mapNotNull { l ->
            parsePriceCny(l.price)?.let { SourcedListing(l, it) }
        }
        if (priced.isEmpty()) {
            return Result.failure(NoSuchElementException("No listings had a readable price"))
        }
        return Result.success(SourcingResult(query, priced, used, fx))
    }

    companion object {
        /**
         * Listing prices arrive as "89.00", "¥89", or a "89.00-129.00" range.
         * A range quotes the low end, which is what the grid thumbnail shows.
         */
        fun parsePriceCny(raw: String): Double? =
            Regex("""\d+(\.\d+)?""").find(raw)?.value?.toDoubleOrNull()?.takeIf { it > 0 }
    }
}
