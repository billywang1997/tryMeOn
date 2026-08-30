package com.trymeon.app.domain.sourcing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A source that delivers to the buyer and quotes the price in their currency has
 * already charged the freight and, as a registered platform, the tax. Running
 * the full model over it would invent costs the buyer never pays — and would do
 * so invisibly, since the total would still look like a plausible number.
 */
class PlatformQuotedTest {

    private fun input(priceAud: Double, card: Double = 3.0, quantity: Int = 1) = LandedCostInput(
        // The source quotes in the buyer's currency, so the rate is 1:1 here.
        itemPriceCny = priceAud,
        quantity = quantity,
        parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400),
        agent = SourcingDefaults.defaultAgent,
        line = SourcingDefaults.platformQuoted,
        cnyToAud = 1.0,
        gst = GstTreatment.LOW_VALUE_COLLECTED,
        cardSettlementPercent = card
    )

    @Test
    fun `only the card margin is added to a delivered price`() {
        val cost = LandedCostCalculator.calculate(input(48.64))
        assertEquals(2, cost.lines.size)
        assertEquals(48.64, cost.lines[0].amountAud, 0.01)
        assertEquals(48.64 * 0.03, cost.lines[1].amountAud, 0.01)
        assertEquals(48.64 * 1.03, cost.totalAud, 0.01)
    }

    @Test
    fun `no freight is invented`() {
        val cost = LandedCostCalculator.calculate(input(48.64))
        assertTrue(
            "the seller already charged shipping",
            cost.lines.none { it.label.startsWith("Freight") }
        )
    }

    @Test
    fun `no tax is added on top of the platform's own`() {
        val cost = LandedCostCalculator.calculate(input(48.64))
        // The platform collects GST at checkout on low-value consignments;
        // adding ours would double-charge it in the figure shown.
        assertTrue(cost.lines.none { it.label.startsWith("GST") })
        assertTrue(cost.lines.none { it.label.startsWith("Customs duty") })
    }

    @Test
    fun `no forwarder fee is added when there is no forwarder`() {
        val cost = LandedCostCalculator.calculate(input(48.64))
        assertTrue(cost.lines.none { it.label.contains("service fee") })
        assertTrue(cost.lines.none { it.label == "Payment fee" })
    }

    @Test
    fun `a no-fee card leaves the quoted price untouched`() {
        val cost = LandedCostCalculator.calculate(input(48.64, card = 0.0))
        assertEquals(1, cost.lines.size)
        assertEquals(48.64, cost.totalAud, 0.01)
    }

    @Test
    fun `no chargeable weight is reported, because none was billed to us`() {
        val cost = LandedCostCalculator.calculate(input(48.64))
        // Reporting one would describe a calculation that never happened.
        assertEquals(0, cost.chargeableGrams)
        assertTrue(!cost.billedOnVolume)
    }

    @Test
    fun `quantity multiplies the quoted price`() {
        val cost = LandedCostCalculator.calculate(input(20.0, card = 0.0, quantity = 3))
        assertEquals(60.0, cost.totalAud, 0.01)
    }

    @Test
    fun `the multiplier stays honest on a delivered price`() {
        val cost = LandedCostCalculator.calculate(input(48.64))
        // Nothing is hidden here, so the landed figure should sit just above the
        // sticker rather than at the 2-3x a forwarder route produces.
        assertTrue("multiplier ${cost.multiplier}", cost.multiplier in 1.0..1.1)
    }
}
