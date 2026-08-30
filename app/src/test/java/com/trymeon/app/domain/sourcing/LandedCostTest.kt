package com.trymeon.app.domain.sourcing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The landed price is the only number in this feature a user cannot get
 * anywhere else, so it is the one that must not be quietly wrong. Each case
 * below is a rule that costs real money if it breaks.
 */
class LandedCostTest {

    private val line = ShippingLine(
        id = "air-econ",
        name = "Air economy",
        firstWeightPriceCny = 60.0,
        firstWeightGrams = 500,
        additionalPriceCny = 12.0,
        additionalStepGrams = 100,
        volumetricDivisor = 6000,
        estimatedDays = 8..16
    )

    private val agent = DaigouAgent(
        id = "generic",
        name = "Agent",
        serviceFeePercent = 5.0,
        minServiceFeeCny = 10.0,
        paymentFeePercent = 3.0
    )

    /** A folded jacket: light, but it takes up a box. */
    private val jacket = Parcel(lengthCm = 30.0, widthCm = 25.0, heightCm = 10.0, actualGrams = 400)

    private fun input(
        priceCny: Double = 89.0,
        quantity: Int = 1,
        domestic: Double = 8.0,
        parcel: Parcel = jacket,
        gst: GstTreatment = GstTreatment.LOW_VALUE_COLLECTED,
        fx: Double = 0.21,
        dutyPercent: Double = 5.0,
        processing: Double = 0.0,
        cardPercent: Double = 0.0
    ) = LandedCostInput(
        itemPriceCny = priceCny,
        quantity = quantity,
        domesticShippingCny = domestic,
        parcel = parcel,
        agent = agent,
        line = line,
        cnyToAud = fx,
        gst = gst,
        dutyPercent = dutyPercent,
        importProcessingAud = processing,
        cardSettlementPercent = cardPercent
    )

    @Test
    fun `bulky light parcels are billed on volume, not weight`() {
        val result = LandedCostCalculator.calculate(input())
        // 30 × 25 × 10 / 6000 = 1.25 kg volumetric against 0.4 kg on the scale.
        assertEquals(1250, result.volumetricGrams)
        assertEquals(400, result.actualGrams)
        assertEquals(1250, result.chargeableGrams)
        assertTrue(result.billedOnVolume)
    }

    @Test
    fun `a dense parcel is billed on actual weight`() {
        val boots = Parcel(lengthCm = 20.0, widthCm = 15.0, heightCm = 12.0, actualGrams = 1800)
        val result = LandedCostCalculator.calculate(input(parcel = boots))
        assertEquals(600, result.volumetricGrams)
        assertEquals(1800, result.chargeableGrams)
        assertFalse(result.billedOnVolume)
    }

    @Test
    fun `freight rounds up to whole steps`() {
        // 1250 g = 500 g first weight + 750 g over = 8 steps of 100 g (not 7.5).
        // 60 + 8 × 12 = ¥156.
        val freight = LandedCostCalculator.calculate(input())
            .lines.first { it.label.startsWith("Freight") }
        assertEquals(156.0, freight.amountCny!!, 0.001)
    }

    @Test
    fun `a parcel inside the first weight pays only the first weight`() {
        val sock = Parcel(lengthCm = 10.0, widthCm = 10.0, heightCm = 5.0, actualGrams = 120)
        val freight = LandedCostCalculator.calculate(input(parcel = sock))
            .lines.first { it.label.startsWith("Freight") }
        assertEquals(60.0, freight.amountCny!!, 0.001)
    }

    @Test
    fun `service fee respects its floor`() {
        // 5% of ¥97 is ¥4.85, below the ¥10 minimum.
        val fee = LandedCostCalculator.calculate(input())
            .lines.first { it.label.contains("service fee") }
        assertEquals(10.0, fee.amountCny!!, 0.001)
    }

    @Test
    fun `service fee scales past the floor`() {
        // 5% of ¥1008 = ¥50.40.
        val fee = LandedCostCalculator.calculate(input(priceCny = 1000.0))
            .lines.first { it.label.contains("service fee") }
        assertEquals(50.4, fee.amountCny!!, 0.001)
    }

    @Test
    fun `the headline case - a 89 yuan listing is not 19 dollars`() {
        val result = LandedCostCalculator.calculate(input())
        // Item 18.69 + domestic 1.68 + service 2.10 + freight 32.76
        //   + payment 1.6569 + GST 5.313
        assertEquals(62.20, result.totalAud, 0.01)
        assertEquals(18.69, result.stickerAud, 0.01)
        assertEquals(3.33, result.multiplier, 0.01)
    }

    @Test
    fun `low value GST covers goods and freight`() {
        val gst = LandedCostCalculator.calculate(input())
            .lines.first { it.label.startsWith("GST") }
        // 10% of (¥97 + ¥156) × 0.21 = A$5.313
        assertEquals(5.313, gst.amountAud, 0.001)
    }

    @Test
    fun `no GST line when the forwarder does not collect it`() {
        val result = LandedCostCalculator.calculate(input(gst = GstTreatment.NOT_COLLECTED))
        assertTrue(result.lines.none { it.label.startsWith("GST") })
        assertEquals(56.89, result.totalAud, 0.01)
    }

    @Test
    fun `above the threshold it flips to border assessment even if not asked`() {
        // ¥6000 of goods at 0.21 is A$1260, past the A$1000 low-value threshold.
        val result = LandedCostCalculator.calculate(
            input(priceCny = 6000.0, domestic = 0.0, gst = GstTreatment.LOW_VALUE_COLLECTED, processing = 88.0)
        )
        val labels = result.lines.map { it.label }
        assertTrue("duty must be assessed above the threshold", labels.any { it.startsWith("Customs duty") })
        assertTrue(labels.any { it == "Import processing charge" })

        val goodsAud = 6000.0 * 0.21          // 1260.00
        val duty = goodsAud * 0.05            //   63.00
        val freightAud = 156.0 * 0.21         //   32.76
        assertEquals(duty, result.lines.first { it.label.startsWith("Customs duty") }.amountAud, 0.01)
        // GST base above the threshold includes duty and freight.
        assertEquals(
            (goodsAud + freightAud + duty) * 0.10,
            result.lines.first { it.label.startsWith("GST") }.amountAud,
            0.01
        )
    }

    @Test
    fun `just under the threshold stays on the low-value rule`() {
        // A$999.60 of goods — no duty, no processing charge.
        val result = LandedCostCalculator.calculate(input(priceCny = 4760.0, domestic = 0.0))
        assertTrue(result.lines.none { it.label.startsWith("Customs duty") })
        assertTrue(result.lines.any { it.label.startsWith("GST") })
    }

    @Test
    fun `quantity multiplies the goods but not the freight floor`() {
        val one = LandedCostCalculator.calculate(input(quantity = 1))
        val three = LandedCostCalculator.calculate(input(quantity = 3))
        val freightOne = one.lines.first { it.label.startsWith("Freight") }.amountCny!!
        val freightThree = three.lines.first { it.label.startsWith("Freight") }.amountCny!!
        // Freight follows the parcel, so ordering three of something is where
        // the per-item landed cost actually improves.
        assertEquals(freightOne, freightThree, 0.001)
        assertTrue(three.totalAud / 3 < one.totalAud)
    }

    @Test
    fun `a stricter volumetric divisor costs more`() {
        val express = line.copy(volumetricDivisor = 5000)
        val result = LandedCostCalculator.calculate(
            input().copy(line = express)
        )
        // 7500 cm³ / 5000 = 1.5 kg instead of 1.25 kg.
        assertEquals(1500, result.chargeableGrams)
        assertTrue(result.totalAud > LandedCostCalculator.calculate(input()).totalAud)
    }

    // ── Taobao's own route to Australia ─────────────────────────────────────

    private val direct = SourcingDefaults.officialDirect

    @Test
    fun `official direct ships free once the threshold is met`() {
        // ¥249 is the apparel threshold; ¥260 of goods clears it.
        val result = LandedCostCalculator.calculate(
            input(priceCny = 260.0, domestic = 0.0).copy(
                line = direct, agent = SourcingDefaults.noAgent
            )
        )
        val freight = result.lines.first { it.label.startsWith("Freight") }
        assertEquals(0.0, freight.amountAud, 0.0001)
        assertTrue(freight.label.contains("free"))
        // No forwarder means no service or payment fee either.
        assertTrue(result.lines.none { it.label.contains("service fee") })
        assertTrue(result.lines.none { it.label == "Payment fee" })
    }

    @Test
    fun `below the threshold official direct still charges freight`() {
        val result = LandedCostCalculator.calculate(
            input(priceCny = 120.0, domestic = 0.0).copy(
                line = direct, agent = SourcingDefaults.noAgent
            )
        )
        assertTrue(result.lines.first { it.label.startsWith("Freight") }.amountAud > 0.0)
    }

    @Test
    fun `it says how much more is needed to reach free shipping`() {
        val short = input(priceCny = 189.0, domestic = 0.0).copy(line = direct)
        assertEquals(60.0, LandedCostCalculator.addToFreeShippingCny(short)!!, 0.001)

        val met = input(priceCny = 249.0, domestic = 0.0).copy(line = direct)
        assertNull(LandedCostCalculator.addToFreeShippingCny(met))

        // A forwarder line has no threshold to chase.
        assertNull(LandedCostCalculator.addToFreeShippingCny(input()))
    }

    @Test
    fun `domestic shipping counts toward the free-shipping threshold`() {
        val withDomestic = input(priceCny = 240.0, domestic = 9.0).copy(line = direct)
        assertNull(LandedCostCalculator.addToFreeShippingCny(withDomestic))
    }

    @Test
    fun `official direct beats a forwarder on a qualifying order`() {
        val goods = 300.0
        val official = LandedCostCalculator.calculate(
            input(priceCny = goods, domestic = 0.0).copy(
                line = direct, agent = SourcingDefaults.noAgent
            )
        )
        val forwarded = LandedCostCalculator.calculate(input(priceCny = goods, domestic = 0.0))
        // The comparison the app exists to make: same item, two routes.
        assertTrue(
            "official A$${official.totalAud} should beat forwarder A$${forwarded.totalAud}",
            official.totalAud < forwarded.totalAud
        )
    }

    @Test
    fun `zero-dimension parcels fall back to actual weight`() {
        val unknown = Parcel(0.0, 0.0, 0.0, actualGrams = 300)
        val result = LandedCostCalculator.calculate(input(parcel = unknown))
        assertEquals(0, result.volumetricGrams)
        assertEquals(300, result.chargeableGrams)
        assertFalse(result.billedOnVolume)
    }

    // ── What the card actually settles ──────────────────────────────────────

    @Test
    fun `card settlement is charged on the yuan side of the bill`() {
        val without = LandedCostCalculator.calculate(input())
        val with = LandedCostCalculator.calculate(input(cardPercent = 3.0))

        // ¥97 goods+domestic, ¥10 service, ¥156 freight, ¥7.89 payment = ¥270.89
        val cnyBilled = 97.0 + 10.0 + 156.0 + 7.89
        val expected = cnyBilled * 0.21 * 0.03
        val line = with.lines.first { it.label.startsWith("Card FX") }
        assertEquals(expected, line.amountAud, 0.01)
        assertEquals(without.totalAud + expected, with.totalAud, 0.01)
    }

    @Test
    fun `a no-fee card adds no line at all`() {
        val result = LandedCostCalculator.calculate(input(cardPercent = 0.0))
        assertTrue(result.lines.none { it.label.startsWith("Card FX") })
    }

    @Test
    fun `an AUD-only charge is not converted twice`() {
        // The border processing charge is paid at home, so no card conversion
        // applies to it — including it would overstate the fee.
        val result = LandedCostCalculator.calculate(
            input(priceCny = 6000.0, domestic = 0.0, processing = 88.0, cardPercent = 3.0)
        )
        val cnyBilled = result.lines.sumOf { it.amountCny ?: 0.0 }
        val card = result.lines.first { it.label.startsWith("Card FX") }
        assertEquals(cnyBilled * 0.21 * 0.03, card.amountAud, 0.01)
        assertTrue("processing charge must stay out of the base", card.amountAud < 88.0 * 0.03 + cnyBilled * 0.21 * 0.03)
    }

    @Test
    fun `the label names the rate being applied`() {
        val line = LandedCostCalculator.calculate(input(cardPercent = 2.5))
            .lines.first { it.label.startsWith("Card FX") }
        assertTrue(line.label, line.label.contains("2.5%"))
    }
}
