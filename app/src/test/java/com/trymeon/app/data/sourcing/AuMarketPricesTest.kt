package com.trymeon.app.data.sourcing

import com.trymeon.app.data.remote.EbayItem
import com.trymeon.app.data.remote.SerpApiService
import com.trymeon.app.domain.sourcing.MarketBenchmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The benchmark is the card's main claim now, so what goes into the median
 * matters. A foreign-currency listing read as local money is not a small error:
 * it is a different number of a different kind averaged into the answer.
 */
class AuMarketPricesTest {

    private val prices = AuMarketPrices(SerpApiService(), serpApiKey = "unused")

    private fun listing(title: String, price: String, currency: String) =
        EbayItem(itemId = price, title = title, price = price, currency = currency)

    @Test
    fun `a foreign-currency listing on the local results page is left out`() {
        val results = listOf(
            listing("Linen blazer navy", "189.00", "AUD"),
            listing("Linen blazer cream", "199.00", "AUD"),
            // Same page, US seller: 95 of a different kind of dollar.
            listing("Linen blazer stone", "95.00", "USD")
        )
        assertEquals(listOf(189.0, 199.0), prices.comparablePrices(results, "linen blazer"))
    }

    @Test
    fun `an unrelated product never sets the price of a blazer`() {
        val results = listOf(
            listing("Linen blazer navy", "189.00", "AUD"),
            listing("Phone case clear", "12.00", "AUD")
        )
        assertEquals(listOf(189.0), prices.comparablePrices(results, "linen blazer"))
    }

    @Test
    fun `a listing with no stated currency is trusted, since the search was local`() {
        val results = listOf(listing("Linen blazer navy", "189.00", ""))
        assertEquals(listOf(189.0), prices.comparablePrices(results, "linen blazer"))
    }

    @Test
    fun `mixed currencies move the median the card publishes`() {
        val aud = listOf(180.0, 185.0, 190.0, 195.0, 200.0, 210.0)
        val withUsd = aud + listOf(90.0, 95.0)

        val honest = MarketBenchmark.from(aud)!!
        val polluted = MarketBenchmark.from(withUsd)!!

        assertEquals(192.5, honest.typicalAud, 0.01)
        // Tail trimming absorbs part of it — this is 187.50, not the 140-odd a
        // plain mean would give — so the distortion is quiet rather than
        // obvious. That is the argument for filtering rather than relying on
        // the trim: against a A$50 import it is three points of claimed saving.
        assertEquals(187.5, polluted.typicalAud, 0.01)
        assertEquals(74, honest.savingPercentAgainst(50.10))
        assertEquals(73, polluted.savingPercentAgainst(50.10))
    }

    @Test
    fun `too thin a sample is no benchmark at all`() {
        // Filtering to AUD can drop the sample below what is defensible, and
        // returning null is the honest outcome — the card then claims nothing.
        assertNull(MarketBenchmark.from(listOf(180.0, 190.0)))
    }

    @Test
    fun `a saving is only claimed against a real median`() {
        val b = MarketBenchmark(typicalAud = 189.0, sampleSize = 14)
        assertEquals(73, b.savingPercentAgainst(50.10))
        // Nothing to boast about when the import is not actually cheaper.
        assertNull(b.savingPercentAgainst(200.0))
    }
}
