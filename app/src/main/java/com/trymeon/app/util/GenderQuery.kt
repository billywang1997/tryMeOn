package com.trymeon.app.util

/**
 * Strips any existing gender markers from [query] and prepends the right
 * word ("man" / "woman") so shopping APIs (eBay / SerpAPI / Amazon) return
 * gender-correct results.
 *
 * Accepts common variants case-insensitively ("Male", "man", "m", etc).
 * For "Other", empty, or unrecognized values the query is returned unchanged —
 * gender-neutral (unfiltered) results, which is the correct inclusive default.
 */
fun ensureGenderInQuery(query: String, gender: String?): String {
    val genderWord = when (gender?.trim()?.lowercase()) {
        "male", "man", "m" -> "man"
        "female", "woman", "f" -> "woman"
        else -> return query
    }
    val cleaned = query
        .replace(Regex("\\bwomen'?s?\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\bmen'?s?\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\bwoman\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\bman\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\bfemale\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\bmale\\b", RegexOption.IGNORE_CASE), "")
        .trim()
        .replace(Regex("\\s{2,}"), " ")
    return "$genderWord $cleaned"
}
