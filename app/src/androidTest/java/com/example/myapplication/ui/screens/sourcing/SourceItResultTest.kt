package com.example.myapplication.ui.screens.sourcing

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
import com.example.myapplication.data.remote.FxRate
import com.example.myapplication.data.remote.TaobaoItem
import com.example.myapplication.data.sourcing.SourcedListing
import com.example.myapplication.data.sourcing.SourcingQuery
import com.example.myapplication.data.sourcing.SourcingQuoter
import com.example.myapplication.data.sourcing.SourcingResult
import com.example.myapplication.domain.model.ClothingCategory
import com.example.myapplication.domain.sourcing.Parcel
import com.example.myapplication.domain.sourcing.SourcingDefaults
import androidx.test.platform.app.InstrumentationRegistry
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

    @Test
    fun expandedCardShowsEveryRouteAndTheLandedTotal() {
        compose.setContent {
            Column(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                ListingCard(item = quoted.single(), expanded = true, onToggle = {})
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
                ListingCard(item = quoted.single(), expanded = false, onToggle = {})
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
}
