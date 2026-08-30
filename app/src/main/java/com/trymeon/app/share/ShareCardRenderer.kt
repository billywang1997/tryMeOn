package com.trymeon.app.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Pure Canvas-based renderer for branded share cards.
 * Output is a 1080×1920 portrait bitmap (Instagram Stories friendly).
 */
object ShareCardRenderer {

    private const val W = 1080
    private const val H = 1920
    private const val MARGIN = 80f

    // Brand palette (matches app theme)
    private val INK = Color.parseColor("#111111")
    private val ASH = Color.parseColor("#888888")
    private val WARM = Color.parseColor("#BFA98A")
    private val PAPER = Color.parseColor("#FAF9F6")
    private val MIST = Color.parseColor("#E8E8E8")

    /** One garment on a look card: what slot it fills, what it is, where it came from. */
    data class Credit(val slot: String, val label: String, val source: String)

    sealed class CardSpec {
        /**
         * The try-on result itself, on brand. Every other card is text about the
         * user's closet; this one is the picture they actually want to post, which
         * is why it takes the generated views side by side rather than one hero
         * shot — a front/side pair reads as a lookbook, a single frame reads as a
         * screenshot.
         */
        data class LookBoard(
            val images: List<Bitmap>,
            val credits: List<Credit>,
            val headline: String = "THE FIT"
        ) : CardSpec()

        data class Vibe(val verdict: String, val vibes: List<Pair<String, Int>>) : CardSpec()
        data class StyleTwin(val celebrity: String, val percent: Int, val reason: String) : CardSpec()
        data class Streak(val days: Int, val milestone: String) : CardSpec()
        data class AuditScore(val score: Int, val palette: List<String>, val verdict: String) : CardSpec()
    }

    fun render(spec: CardSpec): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(PAPER)
        drawHeader(canvas)
        when (spec) {
            is CardSpec.LookBoard -> drawLookBoard(canvas, spec)
            is CardSpec.Vibe -> drawVibe(canvas, spec)
            is CardSpec.StyleTwin -> drawStyleTwin(canvas, spec)
            is CardSpec.Streak -> drawStreak(canvas, spec)
            is CardSpec.AuditScore -> drawAudit(canvas, spec)
        }
        drawFooter(canvas)
        return bmp
    }

    private fun drawHeader(canvas: Canvas) {
        val brand = Paint().apply {
            color = WARM
            isAntiAlias = true
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.3f
        }
        canvas.drawText("WARDROBE  ·  AI", MARGIN, 130f, brand)
    }

    private fun drawFooter(canvas: Canvas) {
        val p = Paint().apply {
            color = ASH
            isAntiAlias = true
            textSize = 28f
            letterSpacing = 0.1f
        }
        canvas.drawText("Build yours · download the app", MARGIN, H - 80f, p)
    }

    // ── Look Board ──────────────────────────────────────────────────────────
    private fun drawLookBoard(canvas: Canvas, spec: CardSpec.LookBoard) {
        canvas.drawText(spec.headline.uppercase(), MARGIN, 240f, labelPaint())

        val credits = spec.credits.take(4)

        // The credit block is anchored to the bottom and the images to the top,
        // so slack collects in the middle of the card instead of opening a gap
        // between the headline and the picture.
        val creditLineHeight = 62f
        // Lowest the credits may sit. Images are sized against this so they can
        // never collide with the block, even when the block is at its tallest.
        val floorWearingBaseline = 1740f - (credits.size - 1) * creditLineHeight - 72f

        val imageTop = 320f
        val imageMaxHeight =
            (if (credits.isEmpty()) H - 200f else floorWearingBaseline - 100f) - imageTop
        val imageBoxWidth = W - 2 * MARGIN

        var imageBottom = imageTop
        val images = spec.images.take(3)
        if (images.isNotEmpty()) {
            val gap = 24f
            val cellWidth = (imageBoxWidth - gap * (images.size - 1)) / images.size
            val imagePaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true; isDither = true }

            // Every cell shares one scale so a front and side view come out the
            // same size — differing heights would read as a rendering bug.
            val scale = images.minOf {
                minOf(cellWidth / it.width, imageMaxHeight / it.height)
            }

            images.forEachIndexed { i, bmp ->
                // Fit without cropping: a try-on that cuts off the shoes defeats
                // the point of a head-to-toe render.
                val w = bmp.width * scale
                val h = bmp.height * scale
                val left = MARGIN + i * (cellWidth + gap) + (cellWidth - w) / 2f
                val dst = RectF(left, imageTop, left + w, imageTop + h)

                canvas.save()
                canvas.clipPath(Path().apply { addRoundRect(dst, 24f, 24f, Path.Direction.CW) })
                canvas.drawBitmap(bmp, null, dst, imagePaint)
                canvas.restore()
                imageBottom = maxOf(imageBottom, dst.bottom)
            }
        }

        if (credits.isEmpty()) return

        // Follow the images rather than the page bottom: a two-up pair is width-
        // limited and leaves slack, and slack below a footer-anchored block reads
        // better than a hole in the middle of the card.
        val wearingBaseline = minOf(imageBottom + 120f, floorWearingBaseline)
        val firstCreditBaseline = wearingBaseline + 72f

        canvas.drawText("WEARING", MARGIN, wearingBaseline, labelPaint())

        val slotPaint = Paint().apply {
            color = ASH; isAntiAlias = true; textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.15f
        }
        val itemPaint = Paint().apply { color = INK; isAntiAlias = true; textSize = 42f }
        val sourcePaint = Paint().apply {
            color = WARM; isAntiAlias = true; textSize = 32f
            textAlign = Paint.Align.RIGHT
        }

        val labelX = MARGIN + 230f
        var y = firstCreditBaseline
        for (credit in credits) {
            canvas.drawText(credit.slot.uppercase(), MARGIN, y, slotPaint)
            val sourceWidth =
                if (credit.source.isEmpty()) 0f else sourcePaint.measureText(credit.source) + 30f
            canvas.drawText(
                ellipsize(credit.label, itemPaint, W - MARGIN - labelX - sourceWidth),
                labelX, y, itemPaint
            )
            if (credit.source.isNotEmpty()) {
                canvas.drawText(credit.source, W - MARGIN, y, sourcePaint)
            }
            y += creditLineHeight
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f || paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end).trimEnd() + "…"
    }

    // ── Vibe Card ───────────────────────────────────────────────────────────
    private fun drawVibe(canvas: Canvas, spec: CardSpec.Vibe) {
        val labelPaint = labelPaint()
        canvas.drawText("VIBE CHECK", MARGIN, 240f, labelPaint)

        // Quote
        wrapText(
            canvas, "“${spec.verdict}”",
            x = MARGIN, y = 360f, maxWidth = W - 2 * MARGIN,
            paint = Paint().apply {
                color = INK
                isAntiAlias = true
                textSize = 76f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            },
            lineHeight = 96f
        )

        // Bars
        var y = 900f
        val topIdx = 0
        spec.vibes.forEachIndexed { i, (name, pct) ->
            drawBar(canvas, name, pct, y, isTop = i == topIdx)
            y += 160f
        }
    }

    private fun drawBar(canvas: Canvas, label: String, pct: Int, y: Float, isTop: Boolean) {
        val labelPaint = Paint().apply {
            color = INK
            isAntiAlias = true
            textSize = 44f
            typeface = if (isTop) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }
        val pctPaint = Paint(labelPaint).apply {
            color = if (isTop) INK else ASH
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(label, MARGIN, y, labelPaint)
        canvas.drawText("${pct}%", W - MARGIN, y, pctPaint)

        // Bar
        val barY = y + 30f
        val track = RectF(MARGIN, barY, W - MARGIN, barY + 12f)
        val trackPaint = Paint().apply { color = MIST; isAntiAlias = true }
        canvas.drawRoundRect(track, 6f, 6f, trackPaint)
        val fillEnd = MARGIN + (W - 2 * MARGIN) * (pct.coerceIn(0, 100) / 100f)
        val fill = RectF(MARGIN, barY, fillEnd, barY + 12f)
        val fillPaint = Paint().apply { color = if (isTop) INK else ASH; isAntiAlias = true }
        canvas.drawRoundRect(fill, 6f, 6f, fillPaint)
    }

    // ── Style Twin Card ─────────────────────────────────────────────────────
    private fun drawStyleTwin(canvas: Canvas, spec: CardSpec.StyleTwin) {
        canvas.drawText("STYLE TWIN", MARGIN, 240f, labelPaint())

        // "You dress like"
        val sub = Paint().apply {
            color = ASH; isAntiAlias = true; textSize = 40f
        }
        canvas.drawText("YOU DRESS LIKE", MARGIN, 340f, sub)

        // Celebrity huge
        wrapText(
            canvas, spec.celebrity,
            x = MARGIN, y = 480f, maxWidth = W - 2 * MARGIN,
            paint = Paint().apply {
                color = INK
                isAntiAlias = true
                textSize = 130f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            },
            lineHeight = 150f
        )

        // Percent badge
        val pctPaint = Paint().apply {
            color = WARM
            isAntiAlias = true
            textSize = 220f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        canvas.drawText("${spec.percent}%", MARGIN, 980f, pctPaint)
        canvas.drawText("MATCH", MARGIN + 360f, 970f, Paint().apply {
            color = ASH; isAntiAlias = true; textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.2f
        })

        // Divider
        canvas.drawRect(MARGIN, 1080f, W - MARGIN, 1082f, Paint().apply { color = MIST })

        // Reason
        wrapText(
            canvas, spec.reason,
            x = MARGIN, y = 1190f, maxWidth = W - 2 * MARGIN,
            paint = Paint().apply {
                color = INK; isAntiAlias = true; textSize = 50f
            },
            lineHeight = 70f
        )
    }

    // ── Streak Card ─────────────────────────────────────────────────────────
    private fun drawStreak(canvas: Canvas, spec: CardSpec.Streak) {
        canvas.drawText("OUTFIT STREAK", MARGIN, 240f, labelPaint())

        // Big emoji
        val emoji = Paint().apply { textSize = 360f; isAntiAlias = true }
        canvas.drawText("🔥", MARGIN, 700f, emoji)

        // Days number — huge
        val numPaint = Paint().apply {
            color = INK
            isAntiAlias = true
            textSize = 480f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(spec.days.toString(), MARGIN, 1180f, numPaint)
        canvas.drawText("DAYS · NO REPEATS", MARGIN, 1280f, Paint().apply {
            color = ASH; isAntiAlias = true; textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.2f
        })

        // Milestone tag
        if (spec.milestone.isNotEmpty()) {
            val tagY = 1480f
            val tagPaint = Paint().apply {
                color = INK; isAntiAlias = true; textSize = 44f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(spec.milestone, MARGIN, tagY, tagPaint)
        }
    }

    // ── Audit Score Card ────────────────────────────────────────────────────
    private fun drawAudit(canvas: Canvas, spec: CardSpec.AuditScore) {
        canvas.drawText("CLOSET AUDIT", MARGIN, 240f, labelPaint())
        canvas.drawText("VERSATILITY SCORE", MARGIN, 340f, Paint().apply {
            color = ASH; isAntiAlias = true; textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.2f
        })

        // Big score
        val scorePaint = Paint().apply {
            color = INK; isAntiAlias = true; textSize = 600f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        canvas.drawText(spec.score.toString(), MARGIN, 920f, scorePaint)
        canvas.drawText("/100", MARGIN + 600f, 900f, Paint().apply {
            color = ASH; isAntiAlias = true; textSize = 100f
        })

        // Palette chips
        if (spec.palette.isNotEmpty()) {
            canvas.drawText("PALETTE", MARGIN, 1100f, Paint().apply {
                color = WARM; isAntiAlias = true; textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.2f
            })
            var x = MARGIN
            val y = 1200f
            val chipPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
            val chipBorder = Paint().apply { color = MIST; isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 2f }
            val textP = Paint().apply { color = INK; isAntiAlias = true; textSize = 40f }
            for (c in spec.palette.take(4)) {
                val tw = textP.measureText(c)
                val rect = RectF(x, y, x + tw + 60f, y + 80f)
                canvas.drawRoundRect(rect, 40f, 40f, chipPaint)
                canvas.drawRoundRect(rect, 40f, 40f, chipBorder)
                canvas.drawText(c, x + 30f, y + 55f, textP)
                x += tw + 80f
                if (x > W - MARGIN - 200f) break
            }
        }

        // Verdict
        if (spec.verdict.isNotEmpty()) {
            wrapText(
                canvas, spec.verdict,
                x = MARGIN, y = 1430f, maxWidth = W - 2 * MARGIN,
                paint = Paint().apply { color = INK; isAntiAlias = true; textSize = 48f },
                lineHeight = 68f
            )
        }
    }

    private fun labelPaint() = Paint().apply {
        color = WARM
        isAntiAlias = true
        textSize = 36f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.3f
    }

    private fun wrapText(
        canvas: Canvas, text: String,
        x: Float, y: Float, maxWidth: Float,
        paint: Paint, lineHeight: Float, maxLines: Int = 8
    ) {
        val words = text.split(" ")
        val current = StringBuilder()
        var cursorY = y
        var lines = 0
        for (w in words) {
            val test = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(test) > maxWidth && current.isNotEmpty()) {
                canvas.drawText(current.toString(), x, cursorY, paint)
                cursorY += lineHeight
                current.clear(); current.append(w)
                if (++lines >= maxLines - 1) {
                    canvas.drawText("$w…", x, cursorY, paint)
                    return
                }
            } else current.replace(0, current.length, test)
        }
        if (current.isNotEmpty()) canvas.drawText(current.toString(), x, cursorY, paint)
    }
}

object Sharer {
    fun share(context: Context, bitmap: Bitmap, caption: String = "") {
        val cacheDir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(cacheDir, "share_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (caption.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
