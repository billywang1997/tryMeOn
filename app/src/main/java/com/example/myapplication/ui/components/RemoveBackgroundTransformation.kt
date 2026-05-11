package com.example.myapplication.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import coil.size.Size
import coil.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Coil Transformation that removes uniform backgrounds from product images.
 * Uses BFS flood-fill starting from all four edges, treating pixels within
 * [tolerance] (per channel) of the sampled corner color as background.
 *
 * Works well for product-on-white / product-on-neutral shots. Does NOT work
 * for lifestyle / model photos — use only where images are known product shots.
 */
class RemoveBackgroundTransformation(private val tolerance: Int = 30) : Transformation {

    override val cacheKey = "remove_bg_v2_$tolerance"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val w = input.width
        val h = input.height
        val bmp = input.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        // Sample ~5×5 patches from each corner and majority-vote for background color
        val sample = minOf(5, w / 4, h / 4).coerceAtLeast(1)
        val samples = ArrayList<Int>(sample * sample * 4)
        for (sy in 0 until sample) for (sx in 0 until sample) {
            samples += pixels[sy * w + sx]
            samples += pixels[sy * w + (w - 1 - sx)]
            samples += pixels[(h - 1 - sy) * w + sx]
            samples += pixels[(h - 1 - sy) * w + (w - 1 - sx)]
        }
        val bgColor = samples.groupBy { it }.maxByOrNull { it.value.size }?.key ?: pixels[0]
        val bgR = Color.red(bgColor)
        val bgG = Color.green(bgColor)
        val bgB = Color.blue(bgColor)

        // BFS flood-fill from all edge pixels
        val visited = BooleanArray(w * h)
        val queue = ArrayDeque<Int>(w * 2 + h * 2)
        for (x in 0 until w) {
            queue += x               // top row
            queue += (h - 1) * w + x // bottom row
        }
        for (y in 1 until h - 1) {
            queue += y * w           // left column
            queue += y * w + w - 1   // right column
        }

        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            if (idx < 0 || idx >= pixels.size || visited[idx]) continue
            visited[idx] = true
            val p = pixels[idx]
            val dr = abs(Color.red(p) - bgR)
            val dg = abs(Color.green(p) - bgG)
            val db = abs(Color.blue(p) - bgB)
            if (dr + dg + db <= tolerance * 3) {
                pixels[idx] = 0x00FFFFFF // transparent
                val x = idx % w; val y = idx / w
                if (x > 0)     queue += idx - 1
                if (x < w - 1) queue += idx + 1
                if (y > 0)     queue += idx - w
                if (y < h - 1) queue += idx + w
            }
        }

        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        bmp
    }
}
