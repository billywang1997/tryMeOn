package com.example.myapplication.data.sourcing

import com.example.myapplication.domain.model.ClothingCategory
import com.example.myapplication.domain.sourcing.Parcel
import com.example.myapplication.domain.sourcing.ParcelPresets

/**
 * An English wish turned into something Taobao can actually match.
 *
 * This is the half of the feature that is hard to copy. Taobao's own AI shopping
 * assistant is excellent and completely closed to a buyer who does not read
 * Chinese: seller titles are written in a dialect of marketing shorthand
 * ("冰丝", "盐系", "梨形身材") that a literal translation of "linen blazer" will
 * not hit. Getting from the English phrase to the phrase a seller actually typed
 * is the search.
 */
data class SourcingQuery(
    /** Ranked Chinese search phrases, best first. */
    val chineseQueries: List<String>,
    /** What we understood the user to be asking for, echoed back in English. */
    val englishSummary: String,
    val category: ClothingCategory,
    /** Parcel estimate used to quote freight before anything is bought. */
    val parcel: Parcel,
    /** One line of buyer's advice specific to sourcing this from a Chinese seller. */
    val buyerNote: String = ""
) {
    val primaryQuery: String get() = chineseQueries.firstOrNull().orEmpty()
}

/**
 * Turns an English description into Chinese search phrases.
 *
 * Deliberately an interface: the step is a translation-and-jargon problem, not a
 * reasoning problem, and the best model for it is not necessarily the one doing
 * the styling. A Qwen-backed implementation can replace the default without the
 * rest of the feature noticing.
 */
interface SourcingQueryBuilder {
    suspend fun build(
        englishDescription: String,
        gender: String = "",
        categoryHint: ClothingCategory? = null
    ): Result<SourcingQuery>
}

/** Shared parsing for the pipe-delimited reply format the app uses elsewhere. */
object SourcingQueryParser {

    fun parse(raw: String, fallbackCategory: ClothingCategory?, quantity: Int = 1): SourcingQuery? {
        val queries = mutableListOf<String>()
        var summary = ""
        var category = fallbackCategory
        var note = ""
        var grams: Int? = null
        var dims: Triple<Double, Double, Double>? = null

        raw.lineSequence().forEach { line ->
            val parts = line.trim().split("|")
            when (parts.firstOrNull()?.uppercase()) {
                "CN" -> parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { queries += it }
                "EN" -> summary = parts.getOrNull(1)?.trim().orEmpty()
                "CAT" -> category = runCatching {
                    ClothingCategory.valueOf(parts[1].trim().uppercase())
                }.getOrNull() ?: category
                "PARCEL" -> {
                    grams = parts.getOrNull(1)?.trim()?.toIntOrNull()
                    val l = parts.getOrNull(2)?.trim()?.toDoubleOrNull()
                    val w = parts.getOrNull(3)?.trim()?.toDoubleOrNull()
                    val h = parts.getOrNull(4)?.trim()?.toDoubleOrNull()
                    if (l != null && w != null && h != null) dims = Triple(l, w, h)
                }
                "NOTE" -> note = parts.getOrNull(1)?.trim().orEmpty()
            }
        }

        if (queries.isEmpty()) return null

        val resolvedCategory = category ?: ClothingCategory.INNER
        // Fall back to the category preset whenever the model left a hole; a
        // missing dimension must not silently become a zero-freight quote.
        val preset = ParcelPresets.forCategory(resolvedCategory, quantity)
        val parcel = Parcel(
            lengthCm = dims?.first ?: preset.lengthCm,
            widthCm = dims?.second ?: preset.widthCm,
            heightCm = (dims?.third ?: preset.heightCm) * if (dims != null) quantity else 1,
            actualGrams = (grams?.times(quantity)) ?: preset.actualGrams
        )

        return SourcingQuery(
            chineseQueries = queries.take(3),
            englishSummary = summary,
            category = resolvedCategory,
            parcel = parcel,
            buyerNote = note
        )
    }
}
