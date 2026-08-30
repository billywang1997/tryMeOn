package com.trymeon.app.domain.fit

import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.FitLook
import com.trymeon.app.domain.model.UserProfile
import kotlin.math.abs

/**
 * Which shared looks are worth showing this person.
 *
 * "People your size" is a promise, and the numbers here are where it is kept.
 * Same gender is a hard line; then height and weight, on the spread a shopper
 * would themselves call "about my build". Beyond that the looks are ordered by
 * how close they are, so the nearest body is the first card.
 */
object BodyMatch {

    /** Within this the look is presented as "your size"; beyond it, not shown. */
    const val HEIGHT_TOLERANCE_CM = 6
    const val WEIGHT_TOLERANCE_KG = 6

    data class Match(val look: FitLook, val distance: Double)

    fun sameGender(a: String, b: String): Boolean {
        val x = norm(a); val y = norm(b)
        return x.isNotEmpty() && x == y
    }

    /**
     * Null when the look is not comparable to this profile: different gender,
     * or either side missing the numbers that make the comparison mean anything.
     */
    fun distance(profile: UserProfile, look: FitLook): Double? {
        if (!sameGender(profile.gender, look.gender)) return null
        if (profile.height <= 0 || look.heightCm <= 0) return null
        val dh = abs(profile.height - look.heightCm)
        if (dh > HEIGHT_TOLERANCE_CM) return null
        // Weight is optional on both sides; when present it must also be close.
        val dw = if (profile.weight > 0 && look.weightKg > 0) abs(profile.weight - look.weightKg) else 0
        if (dw > WEIGHT_TOLERANCE_KG) return null
        return dh / HEIGHT_TOLERANCE_CM.toDouble() + dw / WEIGHT_TOLERANCE_KG.toDouble()
    }

    /**
     * Looks for this person, nearest body first. [category] narrows to one
     * kind of garment; [keywords] pulls matching garments ahead without
     * excluding the rest, since a strip of three matches beats an empty one.
     */
    fun forProfile(
        profile: UserProfile?,
        looks: List<FitLook>,
        category: ClothingCategory? = null,
        keywords: List<String> = emptyList(),
        limit: Int = 12
    ): List<Match> {
        if (profile == null) return emptyList()
        val wanted = keywords.map { it.lowercase() }.filter { it.length > 2 }
        return looks.asSequence()
            .filter { category == null || it.category == category.name }
            .mapNotNull { look -> distance(profile, look)?.let { Match(look, it) } }
            .sortedWith(
                compareByDescending<Match> { m -> wanted.count { it in m.look.keywords } }
                    .thenBy { it.distance }
                    .thenByDescending { it.look.createdAt }
            )
            .take(limit)
            .toList()
    }

    private fun norm(g: String): String = when (g.trim().lowercase()) {
        "female", "woman", "f" -> "female"
        "male", "man", "m" -> "male"
        else -> ""
    }
}
