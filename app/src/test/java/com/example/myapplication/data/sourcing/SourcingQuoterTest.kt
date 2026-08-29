package com.example.myapplication.data.sourcing

import com.example.myapplication.data.remote.FxRate
import com.example.myapplication.data.remote.TaobaoItem
import com.example.myapplication.domain.model.ClothingCategory
import com.example.myapplication.domain.sourcing.Parcel
import com.example.myapplication.domain.sourcing.ShippingRoute
import com.example.myapplication.domain.sourcing.SourcingDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quoting every route and naming the cheapest is the screen's whole claim. The
 * failures that matter are a wrong ordering and charging a forwarder's fee on a
 * route that has no forwarder in it.
 */
class SourcingQuoterTest {

    private fun result(priceCny: Double = 120.0, actualGrams: Int = 400) = SourcingResult(
        query = SourcingQuery(
            chineseQueries = listOf("亚麻小西装"),
            englishSummary = "Linen blazer",
            category = ClothingCategory.OUTERWEAR,
            parcel = Parcel(30.0, 25.0, 10.0, actualGrams)
        ),
        listings = listOf(
            SourcedListing(TaobaoItem(itemId = "1", title = "亚麻小西装外套", price = "$priceCny"), priceCny)
        ),
        usedQuery = "亚麻小西装",
        fxRate = FxRate(0.20696, 1L, "ECB")
    )

    @Test
    fun `quotes every configured route`() {
        val item = SourcingQuoter.quote(result()).single()
        assertEquals(SourcingDefaults.lines.size, item.quotes.size)
    }

    @Test
    fun `cheapest route comes first and drives the headline`() {
        val item = SourcingQuoter.quote(result()).single()
        val totals = item.quotes.map { it.cost.totalAud }
        assertEquals(totals.sorted(), totals)
        assertEquals(totals.first(), item.bestTotalAud, 1e-9)
        assertEquals(totals.last(), item.worstTotalAud, 1e-9)
        assertEquals(totals.last() - totals.first(), item.spreadAud, 1e-9)
    }

    @Test
    fun `sea freight wins on a bulky item`() {
        // ¥7 per extra half kilo against air's ¥53 — the spread the picker exists to expose.
        val item = SourcingQuoter.quote(result()).single()
        assertEquals("Taobao sea", item.best.line.name)
        assertTrue("route spread should be worth showing", item.spreadAud > 5.0)
    }

    @Test
    fun `Taobao routes never carry a forwarder fee`() {
        val item = SourcingQuoter.quote(result(), agent = SourcingDefaults.agents.last()).single()
        item.quotes.filter { it.isOfficial }.forEach { q ->
            assertTrue(
                "${q.line.name} charged a service fee",
                q.cost.lines.none { it.label.contains("service fee") || it.label == "Payment fee" }
            )
        }
    }

    @Test
    fun `forwarder routes do carry the chosen agent's fee`() {
        val fullService = SourcingDefaults.agents.first { it.id == "full-service" }
        val item = SourcingQuoter.quote(result(), agent = fullService).single()
        val forwarder = item.quotes.first { it.line.route == ShippingRoute.FORWARDER }
        assertTrue(forwarder.cost.lines.any { it.label.contains(fullService.name) })
    }

    @Test
    fun `changing agent re-prices forwarders but leaves Taobao alone`() {
        val cheap = SourcingQuoter.quote(result(), agent = SourcingDefaults.agents[1]).single()
        val dear = SourcingQuoter.quote(result(), agent = SourcingDefaults.agents.last()).single()

        fun officialTotal(i: com.example.myapplication.data.sourcing.SourcedItem) =
            i.quotes.first { it.isOfficial }.cost.totalAud
        fun forwarderTotal(i: com.example.myapplication.data.sourcing.SourcedItem) =
            i.quotes.first { it.line.route == ShippingRoute.FORWARDER }.cost.totalAud

        assertEquals(officialTotal(cheap), officialTotal(dear), 1e-9)
        assertTrue(forwarderTotal(dear) > forwarderTotal(cheap))
    }

    @Test
    fun `a listing without a readable price is dropped, not quoted at zero`() {
        assertEquals(null, SourcingRepository.parsePriceCny("面议"))
    }
}
