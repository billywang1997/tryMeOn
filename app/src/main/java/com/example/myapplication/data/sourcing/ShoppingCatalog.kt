package com.example.myapplication.data.sourcing

import android.content.Context
import android.util.Log
import com.example.myapplication.AppSettings
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.remote.EbayItem
import com.example.myapplication.data.remote.ProductSearch
import com.example.myapplication.data.remote.ScraperProductSearch
import com.example.myapplication.data.remote.TaobaoApiService
import com.example.myapplication.data.remote.TaobaoItem
import com.example.myapplication.data.remote.TaobaoSource
import com.example.myapplication.data.remote.TaobaoUnionApiService
import com.example.myapplication.domain.model.ClothingCategory
import com.example.myapplication.domain.sourcing.DaigouAgent
import com.example.myapplication.domain.sourcing.SourcingDefaults
import com.example.myapplication.util.Daigou

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
    private val auMarket: AuMarketPrices? = null
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
        limit: Int = 12
    ): List<EbayItem> = runCatching {
        val query = queryBuilder.build(englishQuery, gender, categoryHint).getOrThrow()

        var listings: List<TaobaoItem> = emptyList()
        outer@ for (phrase in query.chineseQueries) {
            for (source in sources.filter { it.available }) {
                val hit = source.search(phrase, limit).getOrNull().orEmpty()
                if (hit.isNotEmpty()) { listings = hit; break@outer }
            }
        }
        if (listings.isEmpty()) return@runCatching emptyList()

        val priced = listings.mapNotNull { l ->
            SourcingRepository.parsePriceCny(l.price)?.let { SourcedListing(l, it) }
        }
        if (priced.isEmpty()) return@runCatching emptyList()

        val fx = fxRates?.current() ?: com.example.myapplication.data.remote.FxRate(
            SourcingDefaults.FALLBACK_CNY_TO_AUD, 0L, "indicative", isFallback = true
        )
        SourcingQuoter
            .quote(
                SourcingResult(query, priced, query.primaryQuery, fx),
                agent = agent,
                cardSettlementPercent = cardSettlementPercent
            )
            .take(limit)
            .map { it.asProductRow() }
    }.onFailure { Log.w(TAG, "search '$englishQuery' failed: ${it.message}") }
        .getOrDefault(emptyList())

    private companion object { const val TAG = "ShoppingCatalog" }
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
            sources = listOf(
                TaobaoUnionApiService(),
                ScraperProductSearch(TaobaoApiService(), rapidApiKey)
            ),
            auMarket = AuMarketPrices(
                com.example.myapplication.data.remote.SerpApiService(),
                settings.serpApiKey
            ),
            queryBuilder = ClaudeSourcingQueryBuilder(
                service = claude,
                apiKey = apiKey,
                // Shared across surfaces: the same phrase searched from OOTD and
                // from try-on should not be translated, or paid for, twice.
                cache = SourcingReplyCache(),
                store = PrefsSourcingReplyStore(context)
            ),
            fxRates = FxRateRepository(settings)
        )
    }
}
