package com.trymeon.app.data.sourcing

import androidx.test.platform.app.InstrumentationRegistry
import com.trymeon.app.AppSettings
import com.trymeon.app.BuildConfig
import com.trymeon.app.data.remote.AliExpressApiService
import com.trymeon.app.data.remote.ClaudeApiService
import com.trymeon.app.data.remote.ScraperProductSearch
import com.trymeon.app.data.remote.SerpApiService
import com.trymeon.app.data.remote.TaobaoApiService
import com.trymeon.app.data.remote.TaobaoUnionApiService
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.model.UserProfile
import com.trymeon.app.domain.sourcing.BodyHints
import com.trymeon.app.domain.sourcing.PriceExpectation
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs the real pipeline and writes down what it produced, for a person to read.
 *
 * Not a pass/fail test of quality — quality is a judgement, and asserting it
 * would only pin today's answer. What this does is make the answer visible:
 * which sources are actually alive, what the stylist asked for given a wardrobe
 * and a body, what the search returned, and what it costs against the local
 * market. Assertions elsewhere check the pipeline is correct; this shows
 * whether it is any good.
 *
 * Output lands in the app's files dir as quality-report.txt.
 */
class QualityReportTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val openAiKey = BuildConfig.CLAUDE_API_KEY
    private val rapidKey = BuildConfig.RAPID_API_KEY
    private val serpKey = BuildConfig.SERP_API_KEY
    private val claude by lazy { ClaudeApiService(ctx) }

    private val out = StringBuilder()
    private fun say(line: String = "") { out.appendLine(line) }

    /** A wardrobe with a recognisable style, so a bad suggestion is obvious. */
    private val wardrobe = listOf(
        ClothingItem(1, "", ClothingCategory.INNER, "oversized crew tee", "black"),
        ClothingItem(2, "", ClothingCategory.INNER, "boxy shirt", "white"),
        ClothingItem(3, "", ClothingCategory.OUTERWEAR, "zip hoodie", "black"),
        ClothingItem(4, "", ClothingCategory.OUTERWEAR, "shell jacket", "charcoal"),
        ClothingItem(5, "", ClothingCategory.PANTS, "straight jeans", "grey"),
        ClothingItem(6, "", ClothingCategory.PANTS, "cargo pants", "black"),
        ClothingItem(7, "", ClothingCategory.SHOES, "canvas low tops", "white")
    )

    @Test
    fun writeQualityReport() = runBlocking {
        assumeTrue("no Claude key", openAiKey.isNotBlank())

        say("SOURCES")
        say("-".repeat(60))
        probeSources()

        say()
        say("STYLIST · same wardrobe, different person and budget")
        say("-".repeat(60))
        stylistRounds()

        say()
        say("SEARCH · what the shop half returns")
        say("-".repeat(60))
        searchRounds()

        say()
        say("LOCAL MARKET · what the benchmark thinks things cost here")
        say("-".repeat(60))
        benchmarkRounds()

        File(ctx.filesDir, "quality-report.txt").writeText(out.toString())
    }

    // ── sections ────────────────────────────────────────────────────────────

    private suspend fun probeSources() {
        val probes = listOf<Triple<String, String, suspend () -> Result<List<*>>>>(
            Triple("AliExpress", "affiliate API, needs app key + tracking id",
                { AliExpressApiService().search("linen blazer", 5) }),
            Triple("Taobao Union", "affiliate API, needs a PID",
                { TaobaoUnionApiService().search("亚麻西装外套", 5) }),
            Triple("Scraper", "RapidAPI taobao-datahub, metered",
                { ScraperProductSearch(TaobaoApiService(), rapidKey).search("亚麻西装外套", 5) })
        )
        for ((name, what, run) in probes) {
            val r = runCatching { run() }.getOrElse { Result.failure(it) }
            val verdict = when {
                r.isFailure -> "DOWN — ${r.exceptionOrNull()?.message?.take(90)}"
                r.getOrNull().isNullOrEmpty() -> "EMPTY — reachable, returned nothing"
                else -> "OK — ${r.getOrNull()?.size} listings"
            }
            say("%-14s %-42s %s".format(name, what, verdict))
        }
        say("%-14s %-42s %s".format("SerpApi", "Google Shopping AU, for the benchmark",
            if (serpKey.isNotBlank()) "key present" else "no key"))
    }

    private suspend fun stylistRounds() {
        val people = listOf(
            "man 180cm 75kg" to UserProfile(gender = "Male", height = 180, weight = 75),
            "man 162cm 55kg" to UserProfile(gender = "Male", height = 162, weight = 55),
            "woman 158cm 50kg" to UserProfile(gender = "Female", height = 158, weight = 50)
        )
        for ((who, profile) in people) for (p in PriceExpectation.entries) {
            val raw = runCatching {
                claude.tryOnWardrobePlan(
                    openAiKey, wardrobe, emptyList(), profile.gender, setOf("streetwear"),
                    priceHint = p.stylistHint, bodyHint = BodyHints.describe(profile)
                )
            }.getOrElse { "ERROR ${it.message}" }
            say()
            say("$who · ${p.name}")
            // Read the way the app reads it, so the report shows what a user
            // would actually get rather than what the model happened to type.
            val plan = com.trymeon.app.ui.screens.tryon.TryOnPlanParser.parse(raw) { it }
            if (plan.isEmpty()) say("  (nothing usable: ${raw.take(110).replace("\n", " / ")})")
            plan.forEach {
                say("  %-10s %-14s %-46s %s".format(
                    it.name, it.fashnCategory, it.searchQuery.take(46), it.chineseQuery.take(22)))
            }
        }
    }

    private suspend fun searchRounds() {
        val catalog = ShoppingCatalog(
            sources = listOf(
                AliExpressApiService(),
                TaobaoUnionApiService(),
                ScraperProductSearch(TaobaoApiService(), rapidKey)
            ),
            queryBuilder = ClaudeSourcingQueryBuilder(
                claude, openAiKey, cache = SourcingReplyCache(),
                priceHint = { PriceExpectation.FAR_BELOW_LOCAL.sellerVocabulary }
            ),
            fxRates = FxRateRepository(AppSettings(ctx)),
            auMarket = if (serpKey.isNotBlank()) AuMarketPrices(SerpApiService(), serpKey) else null,
            expectation = { PriceExpectation.FAR_BELOW_LOCAL }
        )
        for (q in listOf("black chunky sneakers", "grey straight jeans", "cropped linen blazer")) {
            val items = runCatching { catalog.search(q, gender = "Male", limit = 6) }
                .getOrElse { emptyList() }
            say()
            say("\"$q\" → ${items.size} items")
            items.forEach { say("  A$%-9s %-8s %s".format(it.price, it.source, it.title.take(52))) }
        }
    }

    private suspend fun benchmarkRounds() {
        if (serpKey.isBlank()) { say("(no SerpApi key)"); return }
        val market = AuMarketPrices(SerpApiService(), serpKey)
        for (q in listOf("black chunky sneakers", "grey straight jeans", "cropped linen blazer")) {
            val b = runCatching { market.benchmark(q) }.getOrNull()
            say(if (b == null) "%-26s no benchmark (too thin a sample, or no signal)".format(q)
                else "%-26s median A$%.0f from %d listings".format(q, b.typicalAud, b.sampleSize))
        }
    }
}
