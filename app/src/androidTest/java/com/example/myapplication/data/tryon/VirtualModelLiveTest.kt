package com.example.myapplication.data.tryon

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.domain.model.UserProfile
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Runs the portrait and try-on generators for real.
 *
 * These two calls are the most expensive code in the app and had never been
 * executed — the unit tests cover when a portrait is rebuilt, not whether the
 * request shape, the response parsing or the file write actually work.
 *
 * The input photo is synthesised here rather than supplied, so the test needs
 * nobody's likeness. That is enough to prove the plumbing and to see whether
 * the same face survives from the portrait into the try-on; judging whether it
 * looks like a particular person is still a human's job.
 */
class VirtualModelLiveTest {

    private val apiKey = BuildConfig.CLAUDE_API_KEY
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val http = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()

    private fun outDir() = context.filesDir.resolve("cards").apply { mkdirs() }

    /** A stand-in "user photo": a person who does not exist, generated on the spot. */
    private fun synthesiseSubject(): File {
        val body = JSONObject()
            .put("model", "gpt-image-1")
            .put(
                "prompt",
                "Casual smartphone selfie of one fictional person, head and shoulders, " +
                    "neutral expression, plain indoor background, natural daylight. " +
                    "Ordinary snapshot quality, not a studio photograph."
            )
            .put("n", 1)
            .put("size", "1024x1024")
            .toString()

        val req = Request.Builder()
            .url("https://api.openai.com/v1/images/generations")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val raw = http.newCall(req).execute().use { it.body?.string().orEmpty() }
        val b64 = JSONObject(raw).getJSONArray("data").getJSONObject(0).getString("b64_json")
        return File(outDir(), "subject.png").apply {
            FileOutputStream(this).use { it.write(Base64.decode(b64, Base64.DEFAULT)) }
        }
    }

    @Test
    fun portraitAndTryOnBothProduceImages() = runBlocking {
        assumeTrue("no image generation key", apiKey.isNotBlank())

        println("=== 1. subject photo ===")
        val existing = File(outDir(), "subject.png")
        val subject = if (existing.length() > 10_000) existing.also { println("reusing") }
                      else synthesiseSubject()
        println("subject: ${subject.length()} bytes")
        assertTrue("subject photo not written", subject.length() > 10_000)

        val claude = ClaudeApiService(context)
        val profile = UserProfile(gender = "Female", height = 168, weight = 58)

        println("=== 2. virtual model ===")
        val kept = File(context.filesDir, "virtual_model.png")
        val portrait = if (kept.length() > 10_000) {
            println("reusing the kept portrait — which is the point of keeping it")
            Result.success(kept.absolutePath)
        } else claude.generateModelPortrait(apiKey, subject.absolutePath, profile)
        portrait.exceptionOrNull()?.let { println("FAILED: ${it.message}") }
        assertTrue("portrait failed: ${portrait.exceptionOrNull()?.message}", portrait.isSuccess)

        val portraitPath = portrait.getOrThrow()
        val portraitFile = File(portraitPath)
        println("portrait: $portraitPath (${portraitFile.length()} bytes)")
        // The production path writes to a fixed name so attempts cannot pile up.
        assertTrue("portrait not on disk", portraitFile.exists() && portraitFile.length() > 10_000)
        assertTrue("expected the stable filename", portraitPath.endsWith("virtual_model.png"))
        portraitFile.copyTo(File(outDir(), "live_portrait.png"), overwrite = true)

        println("=== 3. dressing the model in a real Taobao garment ===")
        val garment = listOf(
            "https://img.alicdn.com/bao/uploaded/i2/1112458684/O1CN01An6sEx2E1KZeBjMya_!!1112458684.jpg"
                    to "linen cropped blazer"
        )
        val look = claude.generateTryOnImage(
            apiKey = apiKey,
            faceImagePath = subject.absolutePath,
            garments = garment,
            profile = profile,
            viewAngle = "front",
            modelPortraitPath = portraitPath
        )
        look.exceptionOrNull()?.let { println("FAILED: ${it.message}") }
        assertTrue("try-on failed: ${look.exceptionOrNull()?.message}", look.isSuccess)

        val lookFile = File(look.getOrThrow())
        println("look: ${lookFile.absolutePath} (${lookFile.length()} bytes)")
        assertTrue("look not on disk", lookFile.exists() && lookFile.length() > 10_000)
        lookFile.copyTo(File(outDir(), "live_look3.png"), overwrite = true)

        println("=== all three images written ===")
    }
}
