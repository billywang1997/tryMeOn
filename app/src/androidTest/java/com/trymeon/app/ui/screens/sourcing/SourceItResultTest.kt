package com.trymeon.app.ui.screens.sourcing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.trymeon.app.data.remote.FxRate
import com.trymeon.app.data.remote.TaobaoItem
import com.trymeon.app.data.sourcing.SourcedListing
import com.trymeon.app.data.sourcing.SourcingQuery
import com.trymeon.app.data.sourcing.SourcingQuoter
import com.trymeon.app.data.sourcing.SourcingResult
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.sourcing.MarketBenchmark
import com.trymeon.app.domain.sourcing.Parcel
import com.trymeon.app.domain.sourcing.SourcingDefaults
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the results layout against fabricated listings. The live search needs
 * API keys this build does not carry, and a layout that only ever runs in
 * production is a layout nobody has looked at.
 */
class SourceItResultTest {

    @get:Rule val compose = createComposeRule()

    private val quoted = SourcingQuoter.quote(
        SourcingResult(
            query = SourcingQuery(
                chineseQueries = listOf("亚麻小西装外套 短款"),
                englishSummary = "Cropped linen blazer",
                category = ClothingCategory.OUTERWEAR,
                parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400),
                buyerNote = "Asian sizing runs 1-2 sizes small — check 胸围 in cm"
            ),
            listings = listOf(
                SourcedListing(
                    TaobaoItem(
                        itemId = "1",
                        title = "夏季薄款亚麻小西装外套女短款设计感盐系通勤",
                        price = "128.00",
                        shop = "某某女装旗舰店",
                        sold = 3421
                    ),
                    128.0
                )
            ),
            usedQuery = "亚麻小西装外套 短款",
            fxRate = FxRate(0.20696, System.currentTimeMillis(), "ECB")
        ),
        agent = SourcingDefaults.defaultAgent
    )

    /**
     * The other shape that ships: a source that delivers and quotes the price
     * itself, so there is one route and nothing to add but the card margin.
     */
    private val platformQuoted = SourcingQuoter.quote(
        SourcingResult(
            query = SourcingQuery(
                chineseQueries = listOf("linen blazer"),
                englishSummary = "Cropped linen blazer",
                category = ClothingCategory.OUTERWEAR,
                parcel = Parcel(30.0, 25.0, 10.0, actualGrams = 400)
            ),
            listings = listOf(
                SourcedListing(
                    TaobaoItem(
                        itemId = "2",
                        title = "Women Linen Blazer Cropped Casual Suit Jacket",
                        price = "48.64",
                        // Quoted in the buyer's own currency, as AliExpress does.
                        currency = "AUD",
                        shop = "Fashion Store",
                        sold = 842
                    ),
                    48.64
                )
            ),
            usedQuery = "linen blazer",
            // A real rate, to prove it is not applied to a price already local.
            fxRate = FxRate(0.207, System.currentTimeMillis(), "ECB")
        ),
        lines = listOf(SourcingDefaults.platformQuoted)
    )

    @Test
    fun expandedCardShowsEveryRouteAndTheLandedTotal() {
        compose.setContent {
            Column(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                ListingCard(
                    item = quoted.single(),
                    benchmark = MarketBenchmark(typicalAud = 189.0, sampleSize = 14),
                    expanded = true,
                    onToggle = {}
                )
            }
        }

        // The claim the screen exists to make: every route, priced, with a total.
        compose.onNodeWithText("EVERY ROUTE").assertIsDisplayed()
        compose.onNodeWithText("Landed at your door").assertIsDisplayed()
        SourcingDefaults.lines.forEach { line ->
            compose.onNodeWithText(line.name, substring = true).assertIsDisplayed()
        }
        // The spread is the reason to read the ledger at all.
        compose.onNodeWithText("Choosing the wrong one costs", substring = true).assertIsDisplayed()

        compose.onRoot().save("sourced_card_expanded.png")
    }

    @Test
    fun collapsedCardLeadsWithTheLandedPrice() {
        compose.setContent {
            Column(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                ListingCard(
                    item = quoted.single(),
                    benchmark = MarketBenchmark(typicalAud = 189.0, sampleSize = 14),
                    expanded = false,
                    onToggle = {}
                )
            }
        }
        // Sticker and landed price must appear together — one without the other
        // is the misleading half of the comparison.
        val landed = "A$%.2f".format(quoted.single().bestTotalAud)
        compose.onNodeWithText(landed, substring = true).assertIsDisplayed()
        compose.onNodeWithText("¥128", substring = true).assertIsDisplayed()
        compose.onNodeWithText("×", substring = true).assertIsDisplayed()
        compose.onRoot().save("sourced_card_collapsed.png")
    }

    /** Writes the node to internal storage so `run-as` can pull it off the device. */
    private fun SemanticsNodeInteraction.save(name: String) {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext
            .filesDir.resolve("cards").apply { mkdirs() }
        val bitmap = captureToImage().asAndroidBitmap()
        FileOutputStream(File(dir, name)).use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun aSavingIsShownOnlyWhenTheItemIsActuallyCheaper() {
        // Local market at A$40 against a landed A$47: no claim to make.
        compose.setContent {
            Column(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                ListingCard(
                    item = quoted.single(),
                    benchmark = MarketBenchmark(typicalAud = 40.0, sampleSize = 14),
                    expanded = false,
                    onToggle = {}
                )
            }
        }
        val claims = compose
            .onAllNodes(androidx.compose.ui.test.hasText("under", substring = true))
            .fetchSemanticsNodes()
        assertTrue("must not invent a saving", claims.isEmpty())
        compose.onRoot().save("saving_absent.png")
    }

    @Test
    fun aDeliveredPriceLeadsWithTheLocalComparison() {
        compose.setContent {
            Column(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                ListingCard(
                    item = platformQuoted.single(),
                    benchmark = MarketBenchmark(typicalAud = 189.0, sampleSize = 14),
                    expanded = true,
                    onToggle = {}
                )
            }
        }

        // With one route there is no spread to weigh, so the ledger that exists
        // to compare routes must not appear and imply a choice.
        compose.onAllNodes(androidx.compose.ui.test.hasText("EVERY ROUTE"))
            .fetchSemanticsNodes()
            .let { assertTrue("a one-route ledger implies a choice that is not there", it.isEmpty()) }

        // The comparison against local retail is what carries the argument now.
        compose.onNodeWithText("below the usual price here").assertIsDisplayed()

        // The seller charges A$48.64; a yuan "from" price would be one we made up.
        compose.onNodeWithText("A$50.10", substring = true).assertIsDisplayed()
        compose.onAllNodes(androidx.compose.ui.test.hasText("¥", substring = true))
            .fetchSemanticsNodes()
            .let { assertTrue("no invented yuan price on a locally-quoted listing", it.isEmpty()) }
        compose.onNodeWithText("Card FX", substring = true).assertIsDisplayed()

        // Nothing invented on top of a price the seller already delivered.
        listOf("Freight", "GST", "service fee").forEach { absent ->
            compose.onAllNodes(androidx.compose.ui.test.hasText(absent, substring = true))
                .fetchSemanticsNodes()
                .let { assertTrue("$absent must not be added to a delivered price", it.isEmpty()) }
        }

        compose.onRoot().save("card_platform_quoted.png")
    }
}
