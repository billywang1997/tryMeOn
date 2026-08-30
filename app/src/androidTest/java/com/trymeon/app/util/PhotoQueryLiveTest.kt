package com.trymeon.app.util

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.trymeon.app.AppSettings
import com.trymeon.app.BuildConfig
import com.trymeon.app.data.remote.ClaudeApiService
import com.trymeon.app.data.remote.SerpApiService
import com.trymeon.app.data.sourcing.AuMarketPrices
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Reads real photographs and writes down what the app would search for.
 *
 * The point of the feature is modest and worth checking rather than assuming:
 * without an index of product images there is no finding *this* jacket, only
 * jackets like it. So what matters is whether the phrase it produces is one a
 * shop search can actually use, and whether the local price band that follows
 * is plausible. Both are judgements, so this prints rather than asserts.
 */
class PhotoQueryLiveTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val openAiKey = BuildConfig.CLAUDE_API_KEY

    @Test
    fun readPhotosAndPriceThem() = runBlocking {
        assumeTrue("no Claude key", openAiKey.isNotBlank())
        val claude = ClaudeApiService(ctx)
        val serpKey = AppSettings(ctx).serpApiKey.ifBlank { BuildConfig.SERP_API_KEY }
        val market = if (serpKey.isNotBlank()) AuMarketPrices(SerpApiService(), serpKey) else null

        val out = StringBuilder()
        for (name in listOf("tryon", "model", "results")) {
            val file = File("/sdcard/Download/$name.jpg")
            if (!file.exists()) { out.appendLine("$name: not on device"); continue }

            val seen = PhotoQuery.read(ctx, Uri.fromFile(file), claude, openAiKey)
            if (seen == null) {
                out.appendLine("$name: could not read")
                continue
            }
            val bench = market?.benchmark(seen.query)
            out.appendLine(
                "%-9s %-11s %-38s %s".format(
                    name, seen.category.name, seen.query,
                    bench?.let { "A$%.0f from %d local".format(it.typicalAud, it.sampleSize) }
                        ?: "no local benchmark"
                )
            )
        }
        File(ctx.filesDir, "photo-query.txt").writeText(out.toString())
    }
}
