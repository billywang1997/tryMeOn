package com.trymeon.app.data.sourcing

import com.trymeon.app.data.remote.EbayItem
import com.trymeon.app.data.remote.SerpApiService
import com.trymeon.app.domain.sourcing.MarketBenchmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
            listing("Gordon Smith Danielle Cropped Linen Blazer", "189.00", "AUD"),
            listing("Cropped Structured Linen Blazer", "199.00", "AUD"),
            listing("IDEALSANXUN Womens Cropped Linen Blazer", "95.00", "USD")
        )
        assertEquals(listOf(189.0, 199.0), prices.comparablePrices(results, "cropped linen blazer"))
    }

    @Test
    fun `a retail title that never repeats the colour still counts`() {
        // Every one of these is a chunky sneaker and every one was thrown away,
        // because requiring all three query words of "black chunky sneakers"
        // asks a brand-led retail title to describe itself. Real titles from
        // Google Shopping AU.
        val results = listOf(
            listing("Women EVERAU Chunky Sneakers Como", "65.00", "AUD"),
            listing("Windsor Smith Women's Chunky Leather Sneakers", "189.95", "AUD"),
            listing("Men H&M Beige Chunky Sneakers", "69.99", "AUD"),
            listing("LUCKY STEP Women Chunky Platform Dad Black Sneakers", "59.99", "AUD")
        )
        val kept = prices.comparablePrices(results, "black chunky sneakers")
        assertEquals("a benchmark needs these four", 4, kept.size)
        assertNotNull(MarketBenchmark.from(kept))
    }

    @Test
    fun `a plain court shoe is not the price of a chunky one`() {
        // The looser rule of "head noun only" would price chunky sneakers off
        // these, which are a different product at a different price.
        val results = listOf(
            listing("Lacoste Men's Carnaby Set Sneaker", "98.99", "AUD"),
            listing("Reebok Club C 85 Sneakers", "111.99", "AUD")
        )
        assertEquals(emptyList<Double>(), prices.comparablePrices(results, "black chunky sneakers"))
    }

    @Test
    fun `a singular title matches a plural query`() {
        val results = listOf(
            listing("Marcs Men's Varsity Leather Sneaker", "127.46", "AUD"),
            listing("Aquila Men's Deco 2.0 Leather Sneakers", "259.00", "AUD")
        )
        assertEquals(2, prices.comparablePrices(results, "black leather sneakers").size)
    }

    @Test
    fun `an unrelated product never sets the price of a blazer`() {
        val results = listOf(
            listing("Gordon Smith Danielle Cropped Linen Blazer", "189.00", "AUD"),
            listing("Phone case clear", "12.00", "AUD")
        )
        assertEquals(listOf(189.0), prices.comparablePrices(results, "cropped linen blazer"))
    }

    @Test
    fun `a listing with no stated currency is trusted, since the search was local`() {
        val results = listOf(listing("Cropped Linen Blazer", "189.00", ""))
        assertEquals(listOf(189.0), prices.comparablePrices(results, "cropped linen blazer"))
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

    @Test
    fun `an australian shop calling it a jumper still counts`() {
        // Real measurement: on the same twenty results, "grey wool sweater"
        // kept two listings and produced no benchmark, while "grey wool jumper"
        // kept ten. The photo lookup writes American English; the shops here
        // do not, and the shopper pays for the mismatch in a missing comparison.
        val results = listOf(
            listing("Uniqlo Merino Wool Jumper Grey", "79.90", "AUD"),
            listing("Country Road Wool Knit Jumper", "149.00", "AUD"),
            listing("Sportscraft Grey Wool Pullover", "179.00", "AUD"),
            listing("Jac + Jack Wool Jumper Charcoal", "220.00", "AUD")
        )
        val kept = prices.comparablePrices(results, "grey wool sweater")
        assertEquals("the local word for it was thrown away", 4, kept.size)
        assertNotNull(MarketBenchmark.from(kept))
    }

    @Test
    fun `a synonym does not open the filter to anything`() {
        // The head noun is still required to be one of a known set; a jumper is
        // not a pair of jeans however the shop words it.
        val results = listOf(listing("Levi's 501 Straight Jeans Blue", "129.00", "AUD"))
        assertEquals(emptyList<Double>(), prices.comparablePrices(results, "grey wool sweater"))
    }
}
