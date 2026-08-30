package com.trymeon.app.docs

import com.trymeon.app.domain.sourcing.*
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prints, and pins, the worked example published on the landing page.
 *
 * Numbers shown to an affiliate reviewer have to come from the engine that
 * produces them, not from reading two different screenshots and adding a line
 * from one to the other. If the cost model changes, this fails and the page
 * gets corrected with it.
 */
class LandingPageFiguresTest {

    @Test
    fun `the published worked example is what the engine actually produces`() {
        // The listing from the live run: ¥128 linen blazer, 400 g in a 30x25x10 box,
        // ECB rate on the day, standard forwarder, typical Australian card.
        val input = LandedCostInput(
            itemPriceCny = 128.0,
            parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400),
            agent = SourcingDefaults.noAgent,
            line = SourcingDefaults.officialSea,
            cnyToAud = 0.20696,
            gst = GstTreatment.LOW_VALUE_COLLECTED,
            cardSettlementPercent = 3.0
        )
        val cost = LandedCostCalculator.calculate(input)

        println("=== landing page figures ===")
        cost.lines.forEach { println("  %-46s A$%.2f".format(it.label, it.amountAud)) }
        println("  %-46s A$%.2f".format("Landed at your door", cost.totalAud))
        println("  sticker A$%.2f · %.1fx · billed %d g against %d g actual"
            .format(cost.stickerAud, cost.multiplier, cost.chargeableGrams, cost.actualGrams))

        // Pinned so the page cannot drift from the engine unnoticed.
        assertEquals(26.49, cost.stickerAud, 0.01)
        assertEquals(1500, cost.chargeableGrams)
        assertEquals(400, cost.actualGrams)
    }
}
