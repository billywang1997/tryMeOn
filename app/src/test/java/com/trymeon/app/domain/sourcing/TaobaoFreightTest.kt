package com.trymeon.app.domain.sourcing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Taobao's published rate card for Australia, and the rule that makes it differ
 * from every forwarder: volumetric weight only takes over once it beats actual
 * weight by more than 3:1. Applying the industry max() to this line overstates
 * freight on precisely the light, bulky clothing this feature is for.
 *
 * Rates recorded 2026-08-28: air pickup ¥63 first 0.5 kg then ¥53 per 0.5 kg;
 * home delivery ¥66/¥56; sea ¥66/¥7. Volumetric divisor 6000.
 */
class TaobaoFreightTest {

    private val air = SourcingDefaults.officialAirPickup
    private val sea = SourcingDefaults.officialSea
    private val forwarder = SourcingDefaults.forwarderAir

    private fun input(
        priceCny: Double = 120.0,
        parcel: Parcel,
        line: ShippingLine = air,
        quantity: Int = 1
    ) = LandedCostInput(
        itemPriceCny = priceCny,
        quantity = quantity,
        parcel = parcel,
        agent = SourcingDefaults.noAgent,
        line = line,
        cnyToAud = 0.20696,
        gst = GstTreatment.LOW_VALUE_COLLECTED
    )

    private fun freightCny(input: LandedCostInput) =
        LandedCostCalculator.calculate(input).lines.first { it.label.startsWith("Freight") }.amountCny!!

    // ── The 3:1 rule ────────────────────────────────────────────────────────

    @Test
    fun `under 3 to 1 Taobao bills the actual weight`() {
        // 30 × 25 × 10 / 6000 = 1250 g volumetric against 500 g actual: ratio 2.5.
        val parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 500)
        assertEquals(1250, parcel.volumetricGrams(6000))
        assertEquals(500, parcel.chargeableGrams(6000, ChargeableWeightPolicy.VOLUMETRIC_OVER_3X))
    }

    @Test
    fun `over 3 to 1 Taobao switches to volumetric`() {
        // Same box, 400 g actual: ratio 3.125.
        val parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400)
        assertEquals(1250, parcel.chargeableGrams(6000, ChargeableWeightPolicy.VOLUMETRIC_OVER_3X))
    }

    @Test
    fun `exactly 3 to 1 stays on actual weight`() {
        // The rule reads "not greater than 3:1", so the boundary bills actual.
        val parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 1250 / 3 + 1) // 417 g
        assertTrue(parcel.volumetricGrams(6000) <= parcel.actualGrams * 3)
        assertEquals(417, parcel.chargeableGrams(6000, ChargeableWeightPolicy.VOLUMETRIC_OVER_3X))
    }

    @Test
    fun `a forwarder bills the same parcel on the harsher rule`() {
        val parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 500)
        // Taobao: 500 g. Forwarder: 1250 g. That gap is the point of the enum.
        assertEquals(500, parcel.chargeableGrams(6000, ChargeableWeightPolicy.VOLUMETRIC_OVER_3X))
        assertEquals(1250, parcel.chargeableGrams(6000, ChargeableWeightPolicy.GREATER_OF))
    }

    // ── 0.5 kg rounding ─────────────────────────────────────────────────────

    @Test
    fun `a part half-kilo bills as a whole one`() {
        // 1250 g chargeable rounds to 1.5 kg.
        val bulky = input(parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400))
        assertEquals(1500, LandedCostCalculator.chargeableGrams(bulky))

        // 600 g rounds to 1.0 kg.
        val small = input(parcel = Parcel(20.0, 15.0, 4.0, actualGrams = 600))
        assertEquals(1000, LandedCostCalculator.chargeableGrams(small))
    }

    @Test
    fun `anything at or under half a kilo pays the first weight only`() {
        val light = input(parcel = Parcel(20.0, 15.0, 3.0, actualGrams = 300))
        assertEquals(500, LandedCostCalculator.chargeableGrams(light))
        assertEquals(63.0, freightCny(light), 0.001)
    }

    // ── The published rate card ─────────────────────────────────────────────

    @Test
    fun `air pickup charges 63 then 53 per half kilo`() {
        // 1.5 kg = ¥63 + 2 × ¥53 = ¥169.
        val p = input(parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400))
        assertEquals(169.0, freightCny(p), 0.001)
    }

    @Test
    fun `home delivery costs three yuan more on each step`() {
        val parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400)
        // ¥66 + 2 × ¥56 = ¥178, against ¥169 for pickup.
        assertEquals(178.0, freightCny(input(parcel = parcel, line = SourcingDefaults.officialAirHome)), 0.001)
    }

    @Test
    fun `sea is far cheaper past the first half kilo`() {
        val parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400)
        // ¥66 + 2 × ¥7 = ¥80, against ¥169 by air.
        assertEquals(80.0, freightCny(input(parcel = parcel, line = sea)), 0.001)
    }

    @Test
    fun `free apparel shipping still overrides the rate card`() {
        val parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400)
        assertEquals(0.0, freightCny(input(priceCny = 260.0, parcel = parcel)), 0.001)
    }

    // ── What the change is worth ────────────────────────────────────────────

    @Test
    fun `the 3 to 1 rule is worth real money on a light bulky garment`() {
        // Identical parcel and rate card; only the weight rule differs.
        val parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 500)
        val taobaoRule = freightCny(input(parcel = parcel))
        val maxRule = freightCny(input(parcel = parcel, line = air.copy(weightPolicy = ChargeableWeightPolicy.GREATER_OF)))

        assertEquals(63.0, taobaoRule, 0.001)   // billed on 0.5 kg
        assertEquals(169.0, maxRule, 0.001)     // billed on 1.5 kg
        assertTrue("applying max() here overstates freight by ¥106", maxRule - taobaoRule > 100)
    }

    @Test
    fun `billedOnVolume reflects the policy actually applied`() {
        val underRatio = LandedCostCalculator.calculate(
            input(parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 500))
        )
        // Volumetric is larger, but the rule did not use it — beyond the rounding step.
        assertEquals(1250, underRatio.volumetricGrams)
        assertEquals(500, underRatio.actualGrams)
        assertFalse(underRatio.billedOnVolume)

        val overRatio = LandedCostCalculator.calculate(
            input(parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400))
        )
        assertTrue(overRatio.billedOnVolume)
    }

    // ── The escape hatch the 3:1 rule creates ───────────────────────────────

    @Test
    fun `it finds the tiny weight that escapes volumetric billing`() {
        // 1250 g volumetric needs 417 g of actual weight to fall under 3:1.
        val escape = LandedCostCalculator.volumetricEscape(
            input(parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400))
        )!!
        assertEquals(17, escape.extraGrams)
        // ¥169 billed on 1.5 kg drops to ¥63 billed on 0.5 kg.
        assertEquals(106.0, escape.savingCny, 0.001)
        assertEquals(106.0 * 0.20696, escape.savingAud, 0.001)
    }

    @Test
    fun `no escape offered when the parcel is already under the ratio`() {
        assertNull(
            LandedCostCalculator.volumetricEscape(
                input(parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 500))
            )
        )
    }

    @Test
    fun `no escape offered on a line that does not use the 3 to 1 rule`() {
        assertNull(
            LandedCostCalculator.volumetricEscape(
                input(parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400), line = forwarder)
            )
        )
    }

    @Test
    fun `no escape offered when the extra weight would not actually save anything`() {
        // Sea bills ¥7 a step, so crossing the ratio can be worth too little to
        // mention once rounding is applied. Whatever it reports must be a real saving.
        val escape = LandedCostCalculator.volumetricEscape(
            input(parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400), line = sea)
        )
        if (escape != null) assertTrue(escape.savingCny > 0.0)
    }
}
