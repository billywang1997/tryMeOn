package com.trymeon.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ImageProcessor {

    suspend fun removeBackground(
        context: Context,
        uri: Uri
    ): Bitmap? = withContext(Dispatchers.Default) {
        try {
            val original = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext null

            val maxDim = 1024
            val scaled = if (original.width > maxDim || original.height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(original.width, original.height)
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt(),
                    (original.height * scale).toInt(),
                    true
                ).also { if (it !== original) original.recycle() }
            } else original

            val options = SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()
            val result = SubjectSegmentation.getClient(options)
                .process(InputImage.fromBitmap(scaled, 0))
                .await()

            result.foregroundBitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Crops a background-removed bitmap to just the main garment:
     *   1. Tightens to non-transparent bounds.
     *   2. Removes head/face by finding the low-density neck gap in the upper third.
     *   3. If still full-body tall, keeps the dominant garment (denser half) and discards the rest.
     */
    fun cropToMainGarment(fg: Bitmap): Bitmap {
        val w = fg.width
        val h = fg.height
        if (w == 0 || h == 0) return fg

        val pixels = IntArray(w * h)
        fg.getPixels(pixels, 0, w, 0, 0, w, h)

        // Non-transparent pixel count per row
        val rowDensity = IntArray(h)
        for (y in 0 until h) {
            var count = 0
            for (x in 0 until w) {
                if (((pixels[y * w + x] ushr 24) and 0xFF) > 30) count++
            }
            rowDensity[y] = count
        }

        val maxDensity = rowDensity.max() ?: return fg
        if (maxDensity == 0) return fg
        val minContent = (maxDensity * 0.05f).toInt().coerceAtLeast(1)

        // Tight vertical bounds
        val topRow = rowDensity.indexOfFirst { it >= minContent }.coerceAtLeast(0)
        val bottomRow = rowDensity.indexOfLast { it >= minContent }.coerceAtMost(h - 1)
        val contentH = bottomRow - topRow + 1
        if (contentH <= 0) return fg

        // Short/product photo — just tight-crop, no head to remove
        if (contentH <= w * 1.7f) {
            return safeCrop(fg, 0, topRow, w, contentH)
        }

        // Tall image: look for neck gap in the top 35% of content
        val gapThreshold = (maxDensity * 0.07f).toInt()
        val searchEnd = topRow + (contentH * 0.35f).toInt()
        var gapStart = -1
        var gapEnd = -1
        for (y in topRow until searchEnd) {
            if (rowDensity[y] <= gapThreshold) {
                if (gapStart < 0) gapStart = y
                gapEnd = y
            } else if (gapStart >= 0) {
                break // first contiguous gap found
            }
        }

        val bodyTop = if (gapEnd > 0) {
            var row = gapEnd + 1
            while (row < h && rowDensity[row] < minContent) row++
            row
        } else topRow

        val remainH = bottomRow - bodyTop + 1
        if (remainH <= 0) return safeCrop(fg, 0, topRow, w, contentH)

        // After head removal: if reasonable height, done
        if (remainH <= w * 1.6f) {
            return safeCrop(fg, 0, bodyTop, w, remainH)
        }

        // Still very tall (top + bottom garments): keep the denser half
        val mid = bodyTop + remainH / 2
        val topPixels = (bodyTop until mid).sumOf { rowDensity[it].toLong() }
        val botPixels = (mid..bottomRow).sumOf { rowDensity[it].toLong() }

        return if (topPixels >= botPixels) {
            // Top garment dominates — cut around waist (55% down from bodyTop)
            val cutRow = minOf(bodyTop + (remainH * 0.55f).toInt(), bottomRow)
            safeCrop(fg, 0, bodyTop, w, cutRow - bodyTop)
        } else {
            // Bottom garment dominates — keep from mid down
            safeCrop(fg, 0, mid, w, bottomRow - mid + 1)
        }
    }

    private fun safeCrop(bm: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap {
        val safeX = x.coerceIn(0, bm.width - 1)
        val safeY = y.coerceIn(0, bm.height - 1)
        val safeW = minOf(w, bm.width - safeX).coerceAtLeast(1)
        val safeH = minOf(h, bm.height - safeY).coerceAtLeast(1)
        return Bitmap.createBitmap(bm, safeX, safeY, safeW, safeH)
    }

    fun compositeOnColor(foreground: Bitmap, bgColor: Int): Bitmap {
        val output = Bitmap.createBitmap(foreground.width, foreground.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(bgColor)
        canvas.drawBitmap(foreground, 0f, 0f, null)
        return output
    }

    suspend fun saveBitmapToFile(
        context: Context,
        bitmap: Bitmap,
        subdir: String = "wardrobe"
    ): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, subdir).apply { mkdirs() }
            val file = File(dir, "${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            file.absolutePath
        } catch (e: Exception) { null }
    }
}
