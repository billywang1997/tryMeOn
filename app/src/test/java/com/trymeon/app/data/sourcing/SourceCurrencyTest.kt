package com.trymeon.app.data.sourcing

import com.trymeon.app.data.remote.FxRate
import com.trymeon.app.util.Daigou
import com.trymeon.app.data.remote.TaobaoItem
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.sourcing.Parcel
import com.trymeon.app.domain.sourcing.ChargeableWeightPolicy
import com.trymeon.app.domain.sourcing.SourcingDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A source that quotes in the buyer's own currency must not be converted again.
 * At a CNY rate near 0.2 the mistake is not subtle: the price shown would be a
 * fifth of what the seller charges, and every one of them would look like a
 * bargain against the local market.
 */
class SourceCurrencyTest {

    private fun result(currency: String, price: String) = SourcingResult(
        query = SourcingQuery(
            chineseQueries = listOf("亚麻西装外套"),
            englishSummary = "Linen blazer",
            category = ClothingCategory.OUTERWEAR,
            parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400)
        ),
        listings = listOf(
            SourcedListing(
                TaobaoItem(itemId = "1", title = "Linen Blazer", price = price, currency = currency),
                price.toDouble()
            )
        ),
        usedQuery = "亚麻西装外套",
        fxRate = FxRate(0.207, System.currentTimeMillis(), "ECB")
    )

    @Test
    fun aPriceAlreadyInTheBuyersCurrencyIsNotConvertedAgain() {
        val item = SourcingQuoter.quote(
            result("AUD", "48.64"),
            agent = SourcingDefaults.defaultAgent,
            lines = listOf(SourcingDefaults.platformQuoted)
        ).single()

        // 48.64 plus the 3% card margin — not 48.64 × 0.207.
        assertEquals(50.10, item.bestTotalAud, 0.02)
    }

    @Test
    fun aYuanPriceIsStillConverted() {
        val item = SourcingQuoter.quote(
            result("CNY", "48.64"),
            agent = SourcingDefaults.defaultAgent,
            lines = listOf(SourcingDefaults.platformQuoted)
        ).single()

        assertTrue(
            "a yuan price must still pass through the rate, got ${item.bestTotalAud}",
            item.bestTotalAud < 15.0
        )
    }

    @Test
    fun theDefaultCurrencyIsYuanSoExistingSourcesAreUnaffected() {
        assertEquals("CNY", TaobaoItem(itemId = "1").currency)
    }

    @Test
    fun `a delivered listing is priced without our freight even on a default search`() {
        // The bug this pins: both production callers use the default route list,
        // so a delivered listing was being charged Taobao freight and GST on top
        // of a price that already included the seller's own.
        val item = SourcingQuoter.quote(
            SourcingResult(
                query = SourcingQuery(
                    chineseQueries = listOf("亚麻西装外套"),
                    englishSummary = "Linen blazer",
                    category = ClothingCategory.OUTERWEAR,
                    parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400)
                ),
                listings = listOf(
                    SourcedListing(
                        TaobaoItem(
                            itemId = "1", title = "Linen Blazer", price = "48.64",
                            currency = "AUD", deliveredPrice = true
                        ),
                        48.64
                    )
                ),
                usedQuery = "亚麻西装外套",
                fxRate = FxRate(0.207, System.currentTimeMillis(), "ECB")
            ),
            agent = SourcingDefaults.defaultAgent
            // no `lines` argument: exactly what the app does
        ).single()

        assertEquals(1, item.quotes.size)
        assertEquals(50.10, item.bestTotalAud, 0.02)
        assertTrue(
            "no freight or tax may be added to a delivered price, got ${item.best.cost.lines.map { it.label }}",
            item.best.cost.lines.none {
                it.label.contains("Freight") || it.label.contains("GST") ||
                    it.label.contains("service", ignoreCase = true)
            }
        )
    }

    @Test
    fun `a domestic listing in the same result still gets every route`() {
        // Sources are merged, so the two shapes have to coexist in one list.
        val items = SourcingQuoter.quote(
            SourcingResult(
                query = SourcingQuery(
                    chineseQueries = listOf("亚麻西装外套"),
                    englishSummary = "Linen blazer",
                    category = ClothingCategory.OUTERWEAR,
                    parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400)
                ),
                listings = listOf(
                    SourcedListing(
                        TaobaoItem(itemId = "1", title = "Delivered", price = "48.64",
                            currency = "AUD", deliveredPrice = true), 48.64
                    ),
                    SourcedListing(
                        TaobaoItem(itemId = "2", title = "Domestic", price = "128"), 128.0
                    )
                ),
                usedQuery = "亚麻西装外套",
                fxRate = FxRate(0.207, System.currentTimeMillis(), "ECB")
            ),
            agent = SourcingDefaults.defaultAgent
        )

        assertEquals(1, items[0].quotes.size)
        assertEquals(SourcingDefaults.lines.size, items[1].quotes.size)
        // The domestic one is converted; the delivered one is not.
        assertTrue(items[1].best.cost.lines.any { it.label.contains("Freight") })
    }

    @Test
    fun `a delivered listing is ordered from the seller, not through an agent`() {
        // The promotion link carries our affiliate tracking. Encoding it into a
        // forwarding agent's template both sends the buyer somewhere they do not
        // need to go and destroys the attribution the link exists for.
        Daigou.init("superbuy|Superbuy|https://superbuy.com/order?url={url}&ref={code}|abc123")
        try {
            val promo = "https://s.click.aliexpress.com/e/_ABCDEF"
            val item = SourcingQuoter.quote(
                SourcingResult(
                    query = SourcingQuery(
                        chineseQueries = listOf("亚麻西装外套"),
                        englishSummary = "Linen blazer",
                        category = ClothingCategory.OUTERWEAR,
                        parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400)
                    ),
                    listings = listOf(
                        SourcedListing(
                            TaobaoItem(
                                itemId = "1", title = "Linen Blazer", price = "48.64",
                                itemUrl = promo, currency = "AUD", deliveredPrice = true
                            ),
                            48.64
                        )
                    ),
                    usedQuery = "亚麻西装外套",
                    fxRate = FxRate(0.207, System.currentTimeMillis(), "ECB")
                ),
                agent = SourcingDefaults.defaultAgent
            ).single()

            assertEquals(promo, item.orderUrl)
        } finally {
            Daigou.init(emptyList())
        }
    }

    @Test
    fun `the delivered line offers no advice about freight it does not control`() {
        // Both nudges rely on line fields the platform line leaves unset. If a
        // default ever changes, the card would tell a buyer to add items for
        // free shipping the seller never offered.
        assertEquals(null, SourcingDefaults.platformQuoted.freeOverCny)
        assertEquals(
            ChargeableWeightPolicy.GREATER_OF,
            SourcingDefaults.platformQuoted.weightPolicy
        )
    }
}
