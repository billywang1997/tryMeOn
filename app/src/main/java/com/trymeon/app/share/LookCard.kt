package com.trymeon.app.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Turns a finished try-on into the branded card people actually post.
 *
 * Sharing the raw generated PNG gives away nothing about where it came from;
 * the card carries the wordmark and the garment credits, so a screenshot in
 * someone's story is also the only distribution channel this app gets for free.
 */
object LookCard {

    private const val TAG = "LookCard"

    /** Generated views are 1024×1536; half that is still sharper than the card cell needs. */
    private const val MAX_DIMENSION = 1200

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Render the card. Returns null when not one image could be loaded — there is
     * no card worth sharing without the look itself.
     */
    suspend fun render(
        paths: List<String>,
        credits: List<ShareCardRenderer.Credit>,
        headline: String = "THE FIT"
    ): Bitmap? = withContext(Dispatchers.IO) {
        val images = paths.take(3).mapNotNull { load(it) }
        if (images.isEmpty()) return@withContext null
        ShareCardRenderer.render(ShareCardRenderer.CardSpec.LookBoard(images, credits, headline))
    }

    /** Render and hand off to the system share sheet. Returns false if nothing could be rendered. */
    suspend fun share(
        context: Context,
        paths: List<String>,
        credits: List<ShareCardRenderer.Credit>,
        caption: String = "",
        headline: String = "THE FIT"
    ): Boolean {
        val card = render(paths, credits, headline) ?: return false
        withContext(Dispatchers.Main) { Sharer.share(context, card, caption) }
        return true
    }

    private fun load(path: String): Bitmap? = runCatching {
        when {
            path.startsWith("http://") || path.startsWith("https://") -> decode(download(path))
            else -> decodeFile(path.removePrefix("file://"))
        }
    }.onFailure { Log.w(TAG, "could not load $path: ${it.message}") }.getOrNull()

    private fun download(url: String): ByteArray? =
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.bytes() else null
        }

    private fun decodeFile(path: String): Bitmap? {
        if (!File(path).exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        return BitmapFactory.decodeFile(path, sampledOptions(bounds))
    }

    private fun decode(bytes: ByteArray?): Bitmap? {
        if (bytes == null) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sampledOptions(bounds))
    }

    /** Downsample on decode — three full-size PNGs at once is an easy OOM on a low-end device. */
    private fun sampledOptions(bounds: BitmapFactory.Options) = BitmapFactory.Options().apply {
        var sample = 1
        var longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / 2 >= MAX_DIMENSION) {
            sample *= 2
            longest /= 2
        }
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
}
