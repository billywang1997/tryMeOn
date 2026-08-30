package com.trymeon.app.data.sourcing

import com.trymeon.app.data.remote.FxRate
import com.trymeon.app.data.remote.TaobaoItem
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.sourcing.MarketBenchmark
import com.trymeon.app.domain.sourcing.Parcel
import com.trymeon.app.domain.sourcing.PriceExpectation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The ranker is the difference between "what Taobao returned" and "what this
 * person asked for". These run it across hundreds of generated pools so the
 * band logic is checked on shapes no hand-written fixture would think of:
 * pools that are all expensive, all cheap, three items, forty items, no
 * benchmark at all.
 */
class ListingRankerTest {

    private val fx = FxRate(0.21, 1L, "test")

    private fun pool(
        pricesCny: List<Double>,
        titles: List<String> = pricesCny.map { "女款 单品 $it" },
        sold: List<Int> = pricesCny.map { 100 },
        category: ClothingCategory = ClothingCategory.SHOES
    ): List<SourcedItem> {
        val listings = pricesCny.mapIndexed { i, p ->
            SourcedListing(
                TaobaoItem(itemId = "$i", title = titles[i], price = "$p", sold = sold[i]),
                p
            )
        }
        val query = SourcingQuery(
            chineseQueries = listOf("测试"), englishSummary = "test",
            category = category, parcel = Parcel(30.0, 20.0, 10.0, 500)
        )
        return SourcingQuoter.quote(SourcingResult(query, listings, "测试", fx))
    }

    // ── benchmark bands ─────────────────────────────────────────────────────

    @Test
    fun `far below local keeps only a fraction of the local price`() {
        // Landed ≈ CNY × 0.21 + freight. Local shoes A$120 → cap A$54.
        val items = pool(listOf(40.0, 80.0, 120.0, 160.0, 400.0, 600.0, 900.0, 1200.0))
        val out = ListingRanker.rank(items, PriceExpectation.FAR_BELOW_LOCAL, MarketBenchmark(120.0, 10))
        assertTrue(out.note, out.items.all { it.bestTotalAud <= 120.0 * 0.45 })
        assertTrue("should have found the cheap ones", out.items.size >= 3)
    }

    @Test
    fun `above local has no ceiling but drops the junk floor`() {
        val items = pool(listOf(5.0, 8.0, 12.0, 300.0, 600.0, 900.0, 1500.0))
        val out = ListingRanker.rank(items, PriceExpectation.ABOVE_LOCAL_OK, MarketBenchmark(120.0, 10))
        assertTrue(out.note, out.items.all { it.bestTotalAud >= 120.0 * 0.50 })
        assertTrue("expensive items must survive", out.items.any { it.bestTotalAud > 120.0 })
    }

    @Test
    fun `near local prefers the price closest to the local typical`() {
        val items = pool(listOf(60.0, 200.0, 450.0, 560.0, 900.0, 2500.0))
        val out = ListingRanker.rank(items, PriceExpectation.NEAR_LOCAL, MarketBenchmark(120.0, 10))
        val top = out.items.first().bestTotalAud
        val closest = items.minByOrNull { kotlin.math.abs(it.bestTotalAud - 120.0) }!!.bestTotalAud
        assertEquals("top result should sit nearest the local price", closest, top, 1e-6)
    }

    @Test
    fun `an all-expensive pool is relaxed to the nearest rather than emptied`() {
        val items = pool(listOf(800.0, 900.0, 1000.0, 1200.0, 2000.0))
        val out = ListingRanker.rank(items, PriceExpectation.FAR_BELOW_LOCAL, MarketBenchmark(120.0, 10))
        assertEquals(ListingRanker.MIN_RESULTS, out.items.size)
        assertTrue(out.note.contains("relaxed"))
        // Nearest to the band means the cheapest of the expensive ones.
        assertEquals(items.minOf { it.bestTotalAud }, out.items.minOf { it.bestTotalAud }, 1e-6)
    }

    // ── no benchmark ────────────────────────────────────────────────────────

    @Test
    fun `without a benchmark far-below keeps the cheapest share of the pool`() {
        val items = pool((1..20).map { it * 50.0 })
        val out = ListingRanker.rank(items, PriceExpectation.FAR_BELOW_LOCAL, null, limit = 20)
        assertEquals(8, out.items.size) // 40% of 20
        val cutoff = items.map { it.bestTotalAud }.sorted()[7]
        assertTrue(out.items.all { it.bestTotalAud <= cutoff + 1e-6 })
    }

    @Test
    fun `without a benchmark above-local drops the bottom third`() {
        val items = pool((1..30).map { it * 50.0 })
        val out = ListingRanker.rank(items, PriceExpectation.ABOVE_LOCAL_OK, null, limit = 30)
        val floor = items.map { it.bestTotalAud }.sorted()[9]
        assertTrue(out.items.all { it.bestTotalAud >= floor - 1e-6 })
    }

    // ── sanity filters ──────────────────────────────────────────────────────

    @Test
    fun `a woman's strip drops men's-only titles but keeps unisex`() {
        val items = pool(
            listOf(100.0, 100.0, 100.0, 100.0),
            titles = listOf("男士皮鞋 商务", "女士小白鞋", "男女同款 板鞋", "情侣款 运动鞋")
        )
        val out = ListingRanker.rank(items, PriceExpectation.BELOW_LOCAL, null, gender = "Female")
        val titles = out.items.map { it.listing.title }
        assertFalse(titles.any { it.startsWith("男士") })
        assertTrue(titles.any { it.contains("男女同款") })
        assertTrue(titles.any { it.contains("情侣") })
    }

    @Test
    fun `a shoes strip drops insoles and laces`() {
        val items = pool(
            listOf(10.0, 12.0, 150.0, 160.0, 170.0),
            titles = listOf("鞋垫 增高", "鞋带 圆", "女 小白鞋", "女 乐福鞋", "女 短靴"),
            category = ClothingCategory.SHOES
        )
        val out = ListingRanker.rank(items, PriceExpectation.FAR_BELOW_LOCAL, null, category = ClothingCategory.SHOES)
        assertTrue(out.items.none { it.listing.title.contains("鞋垫") || it.listing.title.contains("鞋带") })
    }

    @Test
    fun `sales volume breaks ties on price`() {
        val items = pool(listOf(100.0, 100.0, 100.0), sold = listOf(3, 30_000, 300))
        val out = ListingRanker.rank(items, PriceExpectation.BELOW_LOCAL, null)
        assertEquals(30_000, out.items.first().listing.sold)
    }

    // ── many rounds ─────────────────────────────────────────────────────────

    @Test
    fun `two hundred random pools never violate their band or come back empty`() {
        val rnd = Random(20260829)
        var bandChecks = 0
        repeat(200) { round ->
            val size = rnd.nextInt(3, 40)
            val scale = listOf(30.0, 100.0, 300.0, 1000.0).random(rnd)
            val prices = List(size) { (rnd.nextDouble(0.1, 3.0) * scale).coerceAtLeast(5.0) }
            val benchmark = if (rnd.nextBoolean()) MarketBenchmark(rnd.nextDouble(40.0, 250.0), 10) else null
            val expectation = PriceExpectation.entries.random(rnd)
            val gender = listOf("Female", "Male", "").random(rnd)
            val category = ClothingCategory.entries.random(rnd)

            val out = ListingRanker.rank(pool(prices), expectation, benchmark, gender, category, limit = 12)

            assertTrue("round $round returned nothing (${out.note})", out.items.isNotEmpty())
            assertTrue("round $round exceeded limit", out.items.size <= 12)

            if (benchmark != null && !out.note.contains("relaxed")) {
                bandChecks++
                val max = expectation.maxRatio?.let { benchmark.typicalAud * it } ?: Double.MAX_VALUE
                val min = expectation.minRatio?.let { benchmark.typicalAud * it } ?: 0.0
                assertTrue(
                    "round $round: ${expectation.name} left band [$min,$max]: ${out.items.map { it.bestTotalAud }} (${out.note})",
                    out.items.all { it.bestTotalAud in min..max }
                )
            }
        }
        assertTrue("random rounds should exercise the benchmark band", bandChecks > 30)
    }

    @Test
    fun `stricter expectations never return a dearer cheapest item than looser ones`() {
        val rnd = Random(7)
        repeat(50) {
            val prices = List(30) { rnd.nextDouble(10.0, 1500.0) }
            val benchmark = MarketBenchmark(rnd.nextDouble(50.0, 200.0), 12)
            val items = pool(prices)
            val far = ListingRanker.rank(items, PriceExpectation.FAR_BELOW_LOCAL, benchmark).items.minOf { it.bestTotalAud }
            val below = ListingRanker.rank(items, PriceExpectation.BELOW_LOCAL, benchmark).items.minOf { it.bestTotalAud }
            assertTrue("far-below ($far) should not start dearer than below ($below)", far <= below + 1e-6)
        }
    }
}
