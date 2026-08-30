package com.trymeon.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.trymeon.app.data.remote.ClaudeApiService
import com.trymeon.app.data.remote.GarmentSighting
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Turns a photo the user picked into something the shop search can look for. */
object PhotoQuery {

    /** Long edge of the image sent for reading. */
    private const val MAX_EDGE = 768

    suspend fun read(
        context: Context,
        uri: Uri,
        claude: ClaudeApiService,
        apiKey: String
    ): GarmentSighting? = withContext(Dispatchers.IO) {
        val base64 = encode(context, uri) ?: return@withContext null
        claude.describeGarment(apiKey, base64)
    }

    /**
     * Reads and shrinks the photo.
     *
     * A phone photo is several megabytes and the lookup only needs to see what
     * kind of garment it is, so it is scaled down before being sent — both to
     * keep the request small and because there is no reason to ship someone's
     * full-resolution camera roll to a third party.
     */
    private fun encode(context: Context, uri: Uri): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = generateSequence(1) { it * 2 }
                .first { longest / it <= MAX_EDGE }
        }
        val bitmap: Bitmap? = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        bitmap?.let {
            val out = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, 80, out)
            it.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    } catch (e: Exception) {
        Log.w("PhotoQuery", "could not read the photo: ${e.message}")
        null
    }
}
