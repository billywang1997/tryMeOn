package com.trymeon.app.docs

import com.trymeon.app.domain.sourcing.*
import org.junit.Assert.assertEquals
import java.io.File
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

        writeLedger("sourced", cost)
        println("  sticker A$%.2f · %.1fx · billed %d g against %d g actual"
            .format(cost.stickerAud, cost.multiplier, cost.chargeableGrams, cost.actualGrams))

        // Pinned so the page cannot drift from the engine unnoticed.
        assertEquals(26.49, cost.stickerAud, 0.01)
        assertEquals(1500, cost.chargeableGrams)
        assertEquals(400, cost.actualGrams)
    }

    @Test
    fun `the delivered-price example is what the engine actually produces`() {
        // The other shape the page shows: a marketplace that ships to the buyer
        // itself and quotes a price with its freight and tax already in it. The
        // only thing left to add is what the card takes.
        val input = LandedCostInput(
            itemPriceCny = 48.64,
            parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400),
            agent = SourcingDefaults.defaultAgent,
            line = SourcingDefaults.platformQuoted,
            cnyToAud = 1.0,
            gst = GstTreatment.LOW_VALUE_COLLECTED,
            cardSettlementPercent = 3.0
        )
        val cost = LandedCostCalculator.calculate(input)

        writeLedger("delivered", cost)

        // Two lines, and nothing invented on top of a price the seller honours.
        assertEquals(2, cost.lines.size)
        assertEquals(50.10, cost.totalAud, 0.01)
    }

    /** Emits the table rows the page publishes, so they are generated not typed. */
    private fun writeLedger(name: String, cost: LandedCost) {
        val rows = buildString {
            // Labels carry percent signs ("Card FX & fees (3%)"), so they are
            // concatenated, never interpolated into a format string.
            cost.lines.forEach {
                append("      <tr><td>" + it.label + "</td><td>A$" +
                    "%.2f".format(it.amountAud) + "</td></tr>\n")
            }
            append("      <tr><td>Landed at your door</td><td>A$" +
                "%.2f".format(cost.totalAud) + "</td></tr>\n")
        }
        File("build/landing-ledger-$name.html").apply {
            parentFile.mkdirs()
            writeText(rows)
        }
    }
}
