package com.example.myapplication.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the look card for real and writes it out, so the layout can be looked
 * at rather than only asserted about. Canvas text metrics do not exist off-device,
 * which is why this is instrumented rather than a JVM test.
 */
@RunWith(AndroidJUnit4::class)
class LookCardRenderTest {

    // Internal storage: on modern Android, adb shell cannot read another app's
    // external files dir, so `run-as` on filesDir is the only way to get the
    // rendered card off the device and actually look at it.
    private val outDir: File
        get() = InstrumentationRegistry.getInstrumentation().targetContext
            .filesDir.resolve("cards").also { it.mkdirs() }

    /** Stand-in for a generated try-on view: same 2:3 portrait shape gpt-image-1 returns. */
    private fun fakeView(tint: Int): Bitmap =
        Bitmap.createBitmap(1024, 1536, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(tint)
            val p = Paint().apply { color = Color.WHITE; isAntiAlias = true; textSize = 90f }
            canvas.drawText("TRY-ON", 60f, 200f, p)
            // A body-ish silhouette, so cropping mistakes are obvious in the output.
            canvas.drawRect(362f, 300f, 662f, 1400f, Paint().apply { color = Color.WHITE })
        }

    private val credits = listOf(
        ShareCardRenderer.Credit("Top", "White cotton oxford shirt", "Closet"),
        ShareCardRenderer.Credit("Bottoms", "Vintage high-rise straight leg denim jeans", "Secondhand"),
        ShareCardRenderer.Credit("Shoes", "Black leather loafers", "Essential"),
    )

    private fun write(name: String, bmp: Bitmap): File {
        val file = File(outDir, name)
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    @Test
    fun rendersTwoViewsSideBySide() {
        val card = ShareCardRenderer.render(
            ShareCardRenderer.CardSpec.LookBoard(
                images = listOf(fakeView(Color.parseColor("#5B6E7F")), fakeView(Color.parseColor("#8A7B6B"))),
                credits = credits
            )
        )
        assertEquals(1080, card.width)
        assertEquals(1920, card.height)
        val file = write("lookcard_two.png", card)
        assertTrue(file.length() > 0)
    }

    @Test
    fun rendersSingleView() {
        val card = ShareCardRenderer.render(
            ShareCardRenderer.CardSpec.LookBoard(
                images = listOf(fakeView(Color.parseColor("#5B6E7F"))),
                credits = credits.take(2)
            )
        )
        write("lookcard_one.png", card)
        assertEquals(1080, card.width)
    }

    @Test
    fun rendersWithoutCreditsOrImages() {
        // A look card with nothing to credit must still be a valid bitmap, not a crash.
        val noCredits = ShareCardRenderer.render(
            ShareCardRenderer.CardSpec.LookBoard(listOf(fakeView(Color.DKGRAY)), emptyList())
        )
        assertNotNull(noCredits)
        val noImages = ShareCardRenderer.render(
            ShareCardRenderer.CardSpec.LookBoard(emptyList(), credits)
        )
        assertEquals(1920, noImages.height)
    }

    @Test
    fun longGarmentNamesStayInsideTheCard() {
        val card = ShareCardRenderer.render(
            ShareCardRenderer.CardSpec.LookBoard(
                images = listOf(fakeView(Color.parseColor("#3F4A55"))),
                credits = listOf(
                    ShareCardRenderer.Credit(
                        "Outerwear",
                        "Extremely long secondhand listing title that would run off the edge of the card if it were not trimmed",
                        "Secondhand"
                    )
                )
            )
        )
        write("lookcard_longname.png", card)

        // The right margin column must stay paper-coloured: text bleeding to the
        // edge is the failure mode an assertion on width alone would not catch.
        val paper = Color.parseColor("#FAF9F6")
        for (y in 1520..1570) {
            assertEquals("row $y bled past the margin", paper, card.getPixel(1075, y))
        }
    }
}
