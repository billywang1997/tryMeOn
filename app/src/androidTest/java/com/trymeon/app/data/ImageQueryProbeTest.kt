package com.trymeon.app.data

import androidx.test.platform.app.InstrumentationRegistry
import com.trymeon.app.AppSettings
import com.trymeon.app.data.remote.GoogleImageSearchService
import com.trymeon.app.data.remote.UnsplashService
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Which thumbnail query actually finds a picture of the garment.
 *
 * The closet's built-in items were showing photographs of people — a man in a
 * blue kurta for "navy t-shirt", a woman's face for a basic. The queries were
 * the difference: some asked for a packshot and some just named the garment.
 * Before changing all of them, measure whether the packshot wording finds
 * anything at all, because an unfindable query is worse than a bad photo.
 */
class ImageQueryProbeTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun compareQueryShapes() = runBlocking {
        val settings = AppSettings(ctx)
        assumeTrue("no Google image search key", settings.googleSearchApiKey.isNotBlank())
        GoogleImageSearchService.init(settings.googleSearchApiKey, settings.googleSearchEngineId)
        UnsplashService.init(settings.unsplashAccessKey)

        val garments = listOf(
            "white t-shirt women", "navy blue t-shirt women", "black turtleneck sweater women",
            "grey hoodie women fleece", "blue skinny jeans women", "white sneakers women casual"
        )
        val shapes = listOf(
            "bare" to { g: String -> g },
            "ghost mannequin" to { g: String -> "$g ghost mannequin" },
            "ghost mannequin product photo" to { g: String -> "$g ghost mannequin product photo" }
        )

        val out = StringBuilder()
        for ((name, shape) in shapes) {
            var found = 0
            out.appendLine("--- $name ---")
            for (g in garments) {
                val url = GoogleImageSearchService.resolveUrl(shape(g))
                if (url != null) found++
                out.appendLine("  %-34s %s".format(g, url?.take(64) ?: "NOTHING"))
            }
            out.appendLine("  found $found/${garments.size}")
            out.appendLine()
        }
        File(ctx.filesDir, "image-probe.txt").writeText(out.toString())
    }
}
