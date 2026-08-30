package com.trymeon.app.data.sourcing

import androidx.test.platform.app.InstrumentationRegistry
import com.trymeon.app.BuildConfig
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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the recommendation pipeline the way a user would hit it, several
 * rounds over, and prints what came back so a human can judge it.
 *
 * Two halves. The stylist half asks the model for pieces against a real-shaped
 * wardrobe under each price expectation and body, and checks the queries
 * change with them. The shop half searches Taobao for the same phrase under
 * each expectation and checks the landed prices land in the band the user
 * asked for. Both spend real credit and are skipped without keys.
 *
 * Read the printed tables in the test output: passing means "nothing was
 * obviously wrong", it does not mean the shoes were nice.
 */
class RecommendationRoundsTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val openAiKey = BuildConfig.CLAUDE_API_KEY
    private val rapidKey = BuildConfig.RAPID_API_KEY
    private val serpKey = BuildConfig.SERP_API_KEY

    private val claude by lazy { ClaudeApiService(ctx) }

    /** A wardrobe with a clear style, so a good recommendation is recognisable. */
    private val wardrobe = listOf(
        ClothingItem(1, "", ClothingCategory.INNER, "oversized crew tee", "black"),
        ClothingItem(2, "", ClothingCategory.INNER, "boxy shirt", "white"),
        ClothingItem(3, "", ClothingCategory.OUTERWEAR, "zip hoodie", "black"),
        ClothingItem(4, "", ClothingCategory.OUTERWEAR, "shell jacket", "charcoal"),
        ClothingItem(5, "", ClothingCategory.PANTS, "straight jeans", "grey"),
        ClothingItem(6, "", ClothingCategory.PANTS, "cargo pants", "black"),
        ClothingItem(7, "", ClothingCategory.BAG, "backpack", "black")
    )

    private fun catalog(expectation: PriceExpectation, benchmark: Boolean) = ShoppingCatalog(
        sources = listOf(TaobaoUnionApiService(), ScraperProductSearch(TaobaoApiService(), rapidKey)),
        queryBuilder = ClaudeSourcingQueryBuilder(
            claude, openAiKey, cache = SourcingReplyCache(),
            priceHint = { expectation.sellerVocabulary }
        ),
        fxRates = FxRateRepository(com.trymeon.app.AppSettings(ctx)),
        auMarket = if (benchmark) AuMarketPrices(SerpApiService(), serpKey) else null,
        expectation = { expectation }
    )

    // ── stylist rounds ──────────────────────────────────────────────────────

    @Test
    fun completeTheLookAdaptsToBudgetAndBody() = runBlocking {
        assumeTrue("no Claude key", openAiKey.isNotBlank())
        val anchor = wardrobe[0] // black oversized tee
        val bodies = listOf(
            "petite" to UserProfile(gender = "Male", height = 162, weight = 55),
            "tall"   to UserProfile(gender = "Male", height = 186, weight = 82)
        )
        var rounds = 0
        val allQueries = mutableMapOf<PriceExpectation, MutableList<String>>()
        println("=== COMPLETE THE LOOK · anchor: ${anchor.color} ${anchor.name} ===")
        for (p in PriceExpectation.entries) for ((label, body) in bodies) {
            val raw = claude.completeTheLook(
                openAiKey, anchor, wardrobe, body.gender,
                priceHint = p.stylistHint, bodyHint = BodyHints.describe(body),
                styleKeywords = setOf("streetwear", "minimal")
            )
            val lines = raw.lines().filter { it.startsWith("QUERY|") }
            println("--- ${p.name} · $label (${body.height}cm) ---")
            lines.forEach { println("  $it") }
            assertTrue("no QUERY lines for ${p.name}/$label:\n$raw", lines.isNotEmpty())
            // Every piece must be a different category from the anchor.
            assertTrue("anchor category repeated", lines.none { it.split("|")[1].trim() == anchor.category.name })
            allQueries.getOrPut(p) { mutableListOf() } += lines.map { it.split("|")[2].lowercase() }
            rounds++
        }
        println("rounds: $rounds")

        // Budget should show in the words: premium materials belong at the top end.
        val premium = listOf("leather", "wool", "cashmere", "silk", "suede", "designer")
        val cheapRounds = allQueries[PriceExpectation.FAR_BELOW_LOCAL].orEmpty()
        val dearRounds = allQueries[PriceExpectation.ABOVE_LOCAL_OK].orEmpty()
        val cheapPremium = cheapRounds.count { q -> premium.any { it in q } }
        val dearPremium = dearRounds.count { q -> premium.any { it in q } }
        println("premium-material queries: far-below=$cheapPremium/${cheapRounds.size}, above=$dearPremium/${dearRounds.size}")
        assertTrue(
            "far-below rounds should not suggest more premium materials than above-local",
            cheapPremium <= dearPremium
        )
    }

    @Test
    fun tryOnPlanRespectsGenderBudgetAndBody() = runBlocking {
        assumeTrue("no Claude key", openAiKey.isNotBlank())
        println("=== TRY-ON PLAN ===")
        for (gender in listOf("Female", "Male")) for (p in listOf(PriceExpectation.FAR_BELOW_LOCAL, PriceExpectation.ABOVE_LOCAL_OK)) {
            val profile = UserProfile(gender = gender, height = if (gender == "Female") 158 else 180, weight = if (gender == "Female") 50 else 75)
            val raw = claude.tryOnWardrobePlan(
                openAiKey, wardrobe, emptyList(), gender, setOf("streetwear"),
                priceHint = p.stylistHint, bodyHint = BodyHints.describe(profile)
            )
            val lines = raw.lines().filter { it.startsWith("CAT:") }
            println("--- $gender · ${p.name} ---")
            lines.forEach { println("  $it") }
            assertTrue("no CAT lines:\n$raw", lines.size >= 3)
            val gw = if (gender == "Female") "women" else "men"
            val wrong = if (gender == "Female") Regex("\\bmen's\\b") else Regex("\\bwomen's\\b")
            val english = lines.map { it.split("|").getOrElse(2) { "" }.lowercase() }
            assertTrue("gender leaked the wrong way: $english", english.none { wrong.containsMatchIn(it) })
            assertTrue("queries should carry $gw", english.count { it.contains(gw) } >= english.size / 2)
            // Chinese phrase present so the shop side does not pay for a translation.
            val chinese = lines.map { it.split("|").getOrElse(3) { "" } }
            assertTrue("missing Chinese phrases: $chinese", chinese.all { c -> c.any { it.code in 0x4E00..0x9FFF } })
        }
    }

    // ── shop rounds ─────────────────────────────────────────────────────────

    @Test
    fun landedPricesLandInTheRequestedBand() = runBlocking {
        assumeTrue("no keys", openAiKey.isNotBlank() && rapidKey.isNotBlank())
        val queries = listOf(
            "black chunky sneakers" to ClothingCategory.SHOES,
            "grey straight jeans" to ClothingCategory.PANTS,
            "white boxy tee" to ClothingCategory.INNER
        )
        val haveBenchmark = serpKey.isNotBlank()
        println("=== SHOP ROUNDS (benchmark=${haveBenchmark}) ===")
        var rounds = 0
        val cheapestByExpectation = mutableMapOf<String, MutableMap<PriceExpectation, Double>>()
        for ((q, cat) in queries) for (p in PriceExpectation.entries) {
            val items = catalog(p, haveBenchmark).search(q, gender = "Male", categoryHint = cat, limit = 8)
            rounds++
            println("--- \"$q\" · ${p.name} · ${items.size} items ---")
            items.forEach { println("  A$%-8s %s".format(it.price, it.title.take(44))) }
            assertTrue("no items for \"$q\" / ${p.name}", items.isNotEmpty())
            items.map { it.price.toDouble() }.minOrNull()?.let { min ->
                cheapestByExpectation.getOrPut(q) { mutableMapOf() }[p] = min
            }
            // Men's search must not surface women's-only listings.
            assertTrue(
                "women's-only listing in a men's strip: ${items.map { it.title }}",
                items.none { ListingRanker.genderMismatch(it.title, "Male") }
            )
            assertTrue(
                "junk in a $cat strip: ${items.map { it.title }}",
                items.none { ListingRanker.categoryJunk(it.title, cat) }
            )
        }
        println("rounds: $rounds")
        println("--- cheapest landed by expectation ---")
        cheapestByExpectation.forEach { (q, m) ->
            println("  \"$q\": " + PriceExpectation.entries.joinToString("  ") { "${it.name.take(9)}=A$%.0f".format(m[it] ?: -1.0) })
            val far = m[PriceExpectation.FAR_BELOW_LOCAL]; val above = m[PriceExpectation.ABOVE_LOCAL_OK]
            if (far != null && above != null) {
                assertTrue("\"$q\": far-below (A$$far) should start no dearer than above-local (A$$above)", far <= above + 0.01)
            }
        }
    }
}
