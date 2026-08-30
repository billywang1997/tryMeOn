package com.trymeon.app.data.sourcing

import com.trymeon.app.data.remote.FxRate
import com.trymeon.app.data.remote.TaobaoItem
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.sourcing.Parcel
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
}
