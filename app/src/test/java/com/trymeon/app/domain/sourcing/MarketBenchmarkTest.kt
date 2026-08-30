package com.trymeon.app.domain.sourcing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * This number is a claim about the market printed next to a price, so it is
 * worth more caution than accuracy: a saving invented by one silly listing
 * would make the whole app look like it is guessing.
 */
class MarketBenchmarkTest {

    @Test
    fun `takes the middle of the market`() {
        val b = MarketBenchmark.from(listOf(70.0, 80.0, 90.0, 100.0))!!
        assertEquals(85.0, b.typicalAud, 0.001)
        assertEquals(4, b.sampleSize)
    }

    @Test
    fun `one absurd listing cannot move it`() {
        // A shopping search for a blazer also returns a hanger and a designer
        // piece. A mean here would read 236; the median holds.
        val withOutliers = listOf(5.0, 70.0, 80.0, 90.0, 100.0, 1200.0)
        val b = MarketBenchmark.from(withOutliers)!!
        assertEquals(85.0, b.typicalAud, 0.001)
    }

    @Test
    fun `trims both tails once the sample can afford it`() {
        val prices = listOf(1.0, 60.0, 70.0, 80.0, 90.0, 100.0, 110.0, 2000.0)
        val b = MarketBenchmark.from(prices)!!
        // With 1 dropped from each end the middle is 85, not dragged by either extreme.
        assertEquals(85.0, b.typicalAud, 0.001)
        // Sample size still reports what was actually seen.
        assertEquals(8, b.sampleSize)
    }

    @Test
    fun `too few listings is not a market`() {
        assertNull(MarketBenchmark.from(listOf(80.0, 90.0, 100.0)))
        assertNull(MarketBenchmark.from(emptyList()))
    }

    @Test
    fun `ignores unusable prices`() {
        assertNull(MarketBenchmark.from(listOf(0.0, -5.0, Double.NaN, 80.0)))
        val b = MarketBenchmark.from(listOf(0.0, 70.0, 80.0, 90.0, 100.0))!!
        assertEquals(4, b.sampleSize)
    }

    @Test
    fun `reports the saving against a landed price`() {
        val b = MarketBenchmark(typicalAud = 85.0, sampleSize = 10)
        assertEquals(68, b.savingPercentAgainst(27.21))
        assertEquals(41, b.savingPercentAgainst(50.0))
    }

    @Test
    fun `says nothing when it is not actually cheaper`() {
        val b = MarketBenchmark(typicalAud = 85.0, sampleSize = 10)
        assertNull(b.savingPercentAgainst(85.0))
        assertNull(b.savingPercentAgainst(120.0))
    }

    @Test
    fun `a trivial saving is not worth claiming`() {
        val b = MarketBenchmark(typicalAud = 85.0, sampleSize = 10)
        // 5% is inside the noise of shipping estimates and FX; saying it would
        // spend the reader's trust on nothing.
        assertNull(b.savingPercentAgainst(81.0))
        assertEquals(12, b.savingPercentAgainst(75.0))
    }

    @Test
    fun `refuses to compute against nonsense`() {
        assertNull(MarketBenchmark(0.0, 10).savingPercentAgainst(27.0))
        assertNull(MarketBenchmark(85.0, 10).savingPercentAgainst(0.0))
    }
}
