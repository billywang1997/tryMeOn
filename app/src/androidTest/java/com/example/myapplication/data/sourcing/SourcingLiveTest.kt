package com.example.myapplication.data.sourcing

import com.example.myapplication.BuildConfig
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.remote.ScraperProductSearch
import com.example.myapplication.data.remote.SearchQuotaExceeded
import com.example.myapplication.data.remote.TaobaoApiService
import com.example.myapplication.data.remote.TaobaoUnionApiService
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the real pipeline: English phrase → model-written Chinese search terms →
 * live Taobao listings → landed price.
 *
 * Every other test here uses saved payloads. This one is the only proof that the
 * translation actually returns phrases Taobao matches, which is the assumption
 * the whole feature rests on and the one no unit test can check.
 *
 * It spends real API credit, so it is skipped when keys are absent.
 */
class SourcingLiveTest {

    private val openAiKey = BuildConfig.CLAUDE_API_KEY
    private val rapidKey = BuildConfig.RAPID_API_KEY

    private fun repo() = SourcingRepository(
        sources = listOf(
            TaobaoUnionApiService(),
            ScraperProductSearch(TaobaoApiService(), rapidKey)
        ),
        queryBuilder = ClaudeSourcingQueryBuilder(
            ClaudeApiService(InstrumentationRegistry.getInstrumentation().targetContext),
            openAiKey
        ),
        fxRates = FxRateRepository(
            com.example.myapplication.AppSettings(
                InstrumentationRegistry.getInstrumentation().targetContext
            )
        )
    )

    @Test
    fun englishPhraseReachesRealTaobaoListings() = runBlocking {
        assumeTrue("no API keys configured", openAiKey.isNotBlank() && rapidKey.isNotBlank())

        val phrase = "cropped linen blazer"
        println("=== SOURCING: \"$phrase\" ===")

        val result = repo().source(phrase, gender = "Female")
        result.exceptionOrNull()?.let { println("FAILED: ${it.message}") }
        // A spent search quota is an environment limit, not a defect. Failing
        // here would train everyone to ignore this suite.
        assumeTrue("search quota exhausted", result.exceptionOrNull() !is SearchQuotaExceeded)
        assertTrue("pipeline failed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val res = result.getOrThrow()
        println("Chinese queries : ${res.query.chineseQueries}")
        println("Query that hit  : ${res.usedQuery}")
        println("Understood as   : ${res.query.englishSummary}")
        println("Buyer note      : ${res.query.buyerNote}")
        println("Parcel estimate : ${res.query.parcel}")
        println("FX              : ${res.fxRate.rate} from ${res.fxRate.source}")
        println("Listings        : ${res.listings.size}")

        // The translation has to produce Han characters — an English phrase
        // passed through unchanged would return nothing on Taobao.
        assertTrue(
            "no Chinese generated: ${res.query.chineseQueries}",
            res.query.chineseQueries.any { q -> q.any { it.code in 0x4E00..0x9FFF } }
        )
        assertTrue("no listings returned", res.listings.isNotEmpty())
        assertTrue("FX rate should be live, not the compiled-in fallback", !res.fxRate.isFallback)

        val quoted = SourcingQuoter.quote(res)
        println()
        println("--- landed prices ---")
        quoted.take(5).forEach { item ->
            println("¥%-8.0f → A$%-8.2f  %s".format(item.priceCny, item.bestTotalAud, item.best.line.name))
            println("            ${item.listing.title.take(46)}")
        }
        println()
        println("cheapest overall: A$%.2f".format(quoted.minOf { it.bestTotalAud }))

        assertTrue("every quote must beat zero", quoted.all { it.bestTotalAud > 0 })
        // Landed always exceeds the sticker: freight and GST cannot be negative.
        assertTrue(
            "landed price must exceed the converted sticker",
            quoted.all { it.bestTotalAud > it.priceCny * res.fxRate.rate }
        )
    }

    @Test
    fun aSecondPhraseAlsoResolves() = runBlocking {
        assumeTrue(openAiKey.isNotBlank() && rapidKey.isNotBlank())
        val result = repo().source("chunky leather loafers", gender = "Female")
        println("=== \"chunky leather loafers\" ===")
        result.onSuccess {
            println("used: ${it.usedQuery} · ${it.listings.size} listings")
            it.listings.take(3).forEach { l -> println("  ¥${l.priceCny}  ${l.listing.title.take(40)}") }
        }.onFailure { println("FAILED: ${it.message}") }
        assumeTrue("search quota exhausted", result.exceptionOrNull() !is SearchQuotaExceeded)
        assertTrue(result.isSuccess)
    }

    @Test
    fun theSecondSearchOfAPhraseSkipsTheModel() = runBlocking {
        assumeTrue(openAiKey.isNotBlank())

        val cache = SourcingReplyCache()
        val builder = ClaudeSourcingQueryBuilder(
            ClaudeApiService(InstrumentationRegistry.getInstrumentation().targetContext),
            openAiKey,
            cache = cache
        )

        val phrase = "ribbed cotton tank top"
        val firstStart = System.currentTimeMillis()
        val first = builder.build(phrase, gender = "Female")
        val firstMs = System.currentTimeMillis() - firstStart
        assertTrue("first call failed: ${first.exceptionOrNull()?.message}", first.isSuccess)

        val secondStart = System.currentTimeMillis()
        val second = builder.build(phrase, gender = "Female")
        val secondMs = System.currentTimeMillis() - secondStart

        println("=== translation cache ===")
        println("first  : ${firstMs} ms  ${first.getOrThrow().chineseQueries}")
        println("second : ${secondMs} ms  ${second.getOrThrow().chineseQueries}")

        assertEquals(
            "a cache hit must return the same translation",
            first.getOrThrow().chineseQueries, second.getOrThrow().chineseQueries
        )
        // A real chat call cannot come back in a few milliseconds.
        assertTrue("second call took ${secondMs} ms — it did not hit the cache", secondMs < 200)
    }
}
