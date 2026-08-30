package com.trymeon.app.domain.sourcing

import com.trymeon.app.domain.model.UserProfile

/**
 * The profile's height and build as one line a stylist prompt can use.
 *
 * Shared so every surface that recommends clothes describes the same person:
 * a "cropped boxy jacket" is a different suggestion for someone petite than
 * for someone very tall, and a strip that ignores that is guessing.
 */
object BodyHints {

    fun describe(profile: UserProfile?): String {
        if (profile == null || profile.height <= 0) return ""
        val heightDesc = when {
            profile.height < 155 -> "petite"
            profile.height < 163 -> "short"
            profile.height < 170 -> "average height"
            profile.height < 178 -> "tall"
            else                 -> "very tall"
        }
        val buildDesc = if (profile.weight > 0) {
            val bmi = profile.weight.toFloat() / ((profile.height / 100f) * (profile.height / 100f))
            when {
                bmi < 17.5 -> "very slim"
                bmi < 20   -> "slim"
                bmi < 23   -> "lean"
                bmi < 25   -> "average build"
                bmi < 27.5 -> "slightly fuller"
                bmi < 30   -> "fuller figure"
                else       -> "plus-size"
            }
        } else ""
        val shape = when {
            profile.bust > 0 && profile.waist > 0 && profile.hips > 0 -> when {
                profile.hips - profile.bust >= 6 -> "pear shape (hips wider than bust)"
                profile.bust - profile.hips >= 6 -> "inverted triangle (shoulders wider than hips)"
                profile.bust - profile.waist >= 20 && profile.hips - profile.waist >= 20 -> "hourglass"
                else -> ""
            }
            else -> ""
        }
        val parts = listOf(heightDesc, buildDesc, shape).filter { it.isNotEmpty() }
        return "Body: ${profile.height}cm, ${parts.joinToString(", ")}. Pick cuts and lengths that flatter this build and say why in the reason."
    }
}
