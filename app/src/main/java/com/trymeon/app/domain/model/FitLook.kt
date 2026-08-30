package com.trymeon.app.domain.model

/**
 * One person, one garment, and how it fit them.
 *
 * The buyer's-show that Chinese marketplaces run on and Western ones never
 * built: a photo plus the wearer's height, weight and the size they took.
 * Shown only to people of a similar build, because "how does it look on
 * someone 30cm taller" answers nothing. Shared explicitly, one look at a time —
 * a body and its numbers are the wearer's to publish, never the app's.
 *
 * It carries the three numbers the share sheet shows the wearer before they
 * agree — gender, height, weight — and no others. It once also carried bust,
 * waist and hips, which the sheet did not mention, the matcher did not use and
 * no screen displayed: measurements published to every signed-in reader for
 * nothing. A field here is a field the world can read.
 */
data class FitLook(
    val id: String = "",
    val uid: String = "",
    val gender: String = "",
    val heightCm: Int = 0,
    val weightKg: Int = 0,
    /** Public download URL of the render or photo. */
    val imageUrl: String = "",
    /** What was worn, as the wearer saw it named: "black oversized crew tee". */
    val garment: String = "",
    /** ClothingCategory.name, so a shoes strip does not show trousers. */
    val category: String = "",
    /** Free text: "M", "38", "L (sized up)". */
    val sizeWorn: String = "",
    /** One of [FIT_SMALL], [FIT_TRUE], [FIT_LARGE]. */
    val fit: String = FIT_TRUE,
    val note: String = "",
    /** "tryon" for an AI render, "photo" for a real one. Shown, never hidden. */
    val source: String = SOURCE_TRYON,
    /** Lower-cased words from [garment], for loose matching against a search. */
    val keywords: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val isRender: Boolean get() = source == SOURCE_TRYON

    val bodyLabel: String get() = listOf(
        heightCm.takeIf { it > 0 }?.let { "${it}cm" },
        weightKg.takeIf { it > 0 }?.let { "${it}kg" }
    ).filterNotNull().joinToString(" · ")

    val fitLabel: String get() = when (fit) {
        FIT_SMALL -> "Runs small"
        FIT_LARGE -> "Runs large"
        else -> "True to size"
    }

    companion object {
        const val FIT_SMALL = "small"
        const val FIT_TRUE = "true"
        const val FIT_LARGE = "large"
        const val SOURCE_TRYON = "tryon"
        const val SOURCE_PHOTO = "photo"

        fun keywordsOf(text: String): List<String> = text.lowercase()
            .split(Regex("[^a-z0-9\\u4e00-\\u9fff]+"))
            .filter { it.length > 2 }
            .distinct()
    }
}
