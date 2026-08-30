package com.trymeon.app.data.sourcing

import android.content.Context
import android.util.Log
import com.trymeon.app.AppSettings
import com.trymeon.app.data.remote.AliExpressApiService
import com.trymeon.app.data.remote.ClaudeApiService
import com.trymeon.app.data.remote.EbayItem
import com.trymeon.app.data.remote.ProductSearch
import com.trymeon.app.data.remote.ScraperProductSearch
import com.trymeon.app.data.remote.TaobaoApiService
import com.trymeon.app.data.remote.TaobaoItem
import com.trymeon.app.data.remote.TaobaoSource
import com.trymeon.app.data.remote.TaobaoUnionApiService
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.sourcing.DaigouAgent
import com.trymeon.app.domain.sourcing.PriceExpectation
import com.trymeon.app.domain.sourcing.SourcingDefaults
import com.trymeon.app.util.Daigou

/**
 * The one place the app shops.
 *
 * Every surface used to fan out across eBay, SerpAPI, Amazon, ASOS and Vinted,
 * which meant five price conventions, five sets of credentials, and a figure on
 * screen that was never what the buyer would actually pay. They all come
 * through here now: one English phrase in, Taobao listings priced to an
 * Australian door out.
 *
 * Results are shaped as [EbayItem] deliberately. It is already the app's
 * generic product row, so existing screens keep working — but `price` now
 * carries the landed cost rather than a sticker in a foreign currency, which
 * is the number the rest of this feature exists to produce.
 */
class ShoppingCatalog(
    private val sources: List<ProductSearch>,
    private val queryBuilder: SourcingQueryBuilder,
    private val fxRates: FxRateRepository? = null,
    private val agent: DaigouAgent = SourcingDefaults.defaultAgent,
    private val cardSettlementPercent: Double = SourcingDefaults.DEFAULT_CARD_SETTLEMENT_PERCENT,
    /** Null simply omits the local comparison. */
    private val auMarket: AuMarketPrices? = null,
    /** Read per search, so a change in Profile applies to the next strip. */
    private val expectation: () -> PriceExpectation = { PriceExpectation.DEFAULT }
) {

    /** What comparable items cost locally, for the strip currently on screen. */
    suspend fun localBenchmark(englishQuery: String) = auMarket?.benchmark(englishQuery)

    /**
     * Search for [englishQuery] and return listings with landed prices.
     *
     * Never throws: a shopping strip that fails is an empty strip, not a
     * screen that will not open.
     */
    suspend fun search(
        englishQuery: String,
        gender: String = "",
        categoryHint: ClothingCategory? = null,
        limit: Int = 12,
        /**
         * A Chinese phrase the caller already has, which skips translation.
         *
         * Worth passing wherever a model wrote the recommendation in the first
         * place: it reads Chinese, so asking it for the search phrase in the
         * same breath turns N+1 chat calls into one. The cost is the parcel
         * estimate, which then comes from the category preset rather than the
         * specific garment — a slightly rougher freight figure in exchange for
         * a strip that loads in seconds instead of half a minute.
         */
        chineseQuery: String? = null
    ): List<EbayItem> = runCatching {
        val query = chineseQuery?.takeIf { it.isNotBlank() }?.let { cn ->
            SourcingQuery(
                chineseQueries = listOf(cn),
                englishSummary = englishQuery,
                category = categoryHint ?: ClothingCategory.INNER,
                parcel = com.trymeon.app.domain.sourcing.ParcelPresets
                    .forCategory(categoryHint ?: ClothingCategory.INNER)
            )
        } ?: queryBuilder.build(englishQuery, gender, categoryHint).getOrThrow()

        // Ask for more than we show: the ranker needs a pool to choose from,
        // and a page of twelve leaves nothing to drop.
        val pool = (limit * 3).coerceIn(POOL_MIN, POOL_MAX)
        var listings: List<TaobaoItem> = emptyList()
        outer@ for (phrase in query.chineseQueries) {
            for (source in sources.filter { it.available }) {
                val hit = source.search(phrase, pool).getOrNull().orEmpty()
                if (hit.isNotEmpty()) { listings = hit; break@outer }
            }
        }
        if (listings.isEmpty()) return@runCatching emptyList()

        val priced = listings.mapNotNull { l ->
            SourcingRepository.parsePriceCny(l.price)?.let { SourcedListing(l, it) }
        }
        if (priced.isEmpty()) return@runCatching emptyList()

        val fx = fxRates?.current() ?: com.trymeon.app.data.remote.FxRate(
            SourcingDefaults.FALLBACK_CNY_TO_AUD, 0L, "indicative", isFallback = true
        )
        val quoted = SourcingQuoter.quote(
            SourcingResult(query, priced, query.primaryQuery, fx),
            agent = agent,
            cardSettlementPercent = cardSettlementPercent
        )
        val want = expectation()
        val category = categoryHint ?: query.category
        val ranked = ListingRanker.rank(
            pool = quoted,
            expectation = want,
            benchmark = auMarket?.benchmark(englishQuery),
            gender = gender,
            category = category,
            limit = limit
        )
        Log.d(TAG, "'$englishQuery' [${want.name}] ${quoted.size} → ${ranked.items.size}: ${ranked.note}")
        ranked.items.map { it.asProductRow() }
    }.onFailure { Log.w(TAG, "search '$englishQuery' failed: ${it.message}") }
        .getOrDefault(emptyList())

    private companion object {
        const val TAG = "ShoppingCatalog"
        const val POOL_MIN = 24
        const val POOL_MAX = 40
    }
}

/**
 * A quoted listing as the app's generic product row.
 *
 * `price` is the landed total, not the ¥ sticker: showing the sticker is what
 * makes an overseas purchase look like a bargain right up until it arrives.
 */
fun SourcedItem.asProductRow(): EbayItem = EbayItem(
    itemId = listing.itemId,
    title = listing.title,
    price = "%.2f".format(bestTotalAud),
    currency = "AUD",
    condition = if (listing.source == TaobaoSource.AFFILIATE) "Taobao" else "Taobao · unofficial",
    imageUrl = listing.imageUrl,
    // The agent deep link when one is configured, else the listing itself —
    // better an honest product page than a link that pays nobody.
    itemWebUrl = orderUrl ?: listing.itemUrl,
    source = "Taobao"
)

/**
 * Builds a catalog wired the way the app expects, so no screen has to remember
 * the source order, the cache or where the FX rate comes from.
 */
object ShoppingCatalogFactory {
    fun create(context: Context, claude: ClaudeApiService, apiKey: String, rapidApiKey: String): ShoppingCatalog {
        val settings = AppSettings(context)
        Daigou.init(settings.daigouProviders, settings.preferredDaigouId)
        return ShoppingCatalog(
            // Order is preference. AliExpress first because it is the source
            // that can be switched on without a Chinese business presence;
            // Taobao Union outranks nothing until its credentials exist, and
            // each source reports itself unavailable rather than failing.
            sources = listOf(
                AliExpressApiService(),
                TaobaoUnionApiService(),
                ScraperProductSearch(TaobaoApiService(), rapidApiKey)
            ),
            auMarket = AuMarketPrices(
                com.trymeon.app.data.remote.SerpApiService(),
                settings.serpApiKey
            ),
            queryBuilder = ClaudeSourcingQueryBuilder(
                service = claude,
                apiKey = apiKey,
                // Shared across surfaces: the same phrase searched from OOTD and
                // from try-on should not be translated, or paid for, twice.
                cache = SourcingReplyCache(),
                store = PrefsSourcingReplyStore(context),
                priceHint = { settings.priceExpectation.sellerVocabulary }
            ),
            fxRates = FxRateRepository(settings),
            expectation = { settings.priceExpectation }
        )
    }
}
