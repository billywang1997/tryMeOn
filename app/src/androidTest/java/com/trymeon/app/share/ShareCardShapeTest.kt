package com.trymeon.app.share

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The share card with a real try-on render in it.
 *
 * The existing fixtures use square placeholders, which makes the layout look
 * emptier than it is: a try-on is a tall portrait and fills most of the card.
 * Judging the composition off the placeholders would mean redesigning around a
 * shape the app never produces.
 */
class ShareCardShapeTest {

    @Test
    fun renderWithRealPortraits() {
        val photo = File("/sdcard/Download/tryon.jpg")
        assumeTrue("no sample portrait on the device", photo.exists())
        val bmp = BitmapFactory.decodeFile(photo.absolutePath)

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.filesDir, "cards").apply { mkdirs() }

        listOf(1, 2).forEach { n ->
            val card = ShareCardRenderer.render(
                ShareCardRenderer.CardSpec.LookBoard(
                    headline = "The fit",
                    images = List(n) { bmp },
                    credits = listOf(
                        ShareCardRenderer.Credit("top", "White cotton oxford shirt", "Closet"),
                        ShareCardRenderer.Credit("bottoms", "Vintage high-rise straight jeans", "Secondhand"),
                        ShareCardRenderer.Credit("shoes", "Black leather loafers", "Essential")
                    )
                )
            )
            FileOutputStream(File(dir, "shape_$n.png")).use {
                card.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
