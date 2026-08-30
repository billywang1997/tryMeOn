package com.trymeon.app.data.sourcing

import androidx.test.platform.app.InstrumentationRegistry
import com.trymeon.app.BuildConfig
import com.trymeon.app.data.remote.SerpApiService
import com.trymeon.app.notifications.PriceMatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Why a query gets no local benchmark.
 *
 * The comparison against Australian retail is the card's main claim, and a
 * quality run found it absent for two queries in three. This reports what the
 * search returned and how many listings survive each filter, so the answer is
 * a measurement rather than a guess.
 */
class BenchmarkDiagnosticTest {

    private val serpKey = BuildConfig.SERP_API_KEY
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun reportWhereListingsAreLost() = runBlocking {
        assumeTrue("no SerpApi key", serpKey.isNotBlank())
        val serp = SerpApiService()
        val market = AuMarketPrices(serp, serpKey)
        val out = StringBuilder()

        val queries = listOf(
            // Three-modifier phrases are what the photo lookup produces, and
            // they are the ones that fall below a usable sample. Each is paired
            // with the same phrase minus its colour, to test whether colour is
            // the modifier retail titles omit.
            "navy ribbed turtleneck sweater", "ribbed turtleneck sweater",
            "beige linen cropped blazer", "linen cropped blazer",
            "grey long-sleeve thermal shirt", "long-sleeve thermal shirt",
            "black chunky leather sneakers", "chunky leather sneakers"
        )
        for (q in queries) {
            val results = serp.search(serpKey, q, limit = 20).getOrNull().orEmpty()
            val wanted = PriceMatcher.tokens(q)

            val kept = market.comparablePrices(results, q).sorted()
            val b = com.trymeon.app.domain.sourcing.MarketBenchmark.from(kept)
            out.appendLine("%-30s returned %2d  kept %2d  %s".format(
                "\"$q\"", results.size, kept.size,
                if (b == null) "NO BENCHMARK" else "median A$%.0f from %d".format(b.typicalAud, b.sampleSize)
            ))
            if (kept.isNotEmpty()) out.appendLine("      kept: " + kept.joinToString(" ") { "%.0f".format(it) })
            out.appendLine()
        }
        File(ctx.filesDir, "benchmark-diag.txt").writeText(out.toString())
    }
}
