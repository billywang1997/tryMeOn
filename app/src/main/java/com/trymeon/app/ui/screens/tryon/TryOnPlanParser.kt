package com.trymeon.app.ui.screens.tryon

import com.trymeon.app.domain.model.ClothingCategory

/**
 * Reads the stylist's plan.
 *
 * The model is asked for `CAT:<category>|<reason>|<english>|<chinese>` and
 * mostly obliges, but a quality run over twelve rounds showed it dropping the
 * literal prefix in a quarter of them and writing `TOP:`, `SET:` or `BAG:`
 * instead — the category as the prefix, everything else identical. Those
 * rounds produced an empty strip in the app. Accepting both shapes is the
 * difference between a recommendation and a blank space, and costs nothing.
 *
 * Category names come back in whatever case and wording the model chose
 * ("SHOES", "shoes", "bottoms", "accessory"), so they are matched case
 * insensitively and through synonyms. Getting this wrong is not cosmetic: an
 * unmatched name used to fall through to "tops", which asks the try-on service
 * to render a pair of shoes as a shirt.
 */
internal object TryOnPlanParser {

    /**
     * The wardrobe slot each name the model uses corresponds to.
     *
     * Resolved to a try-on category through [ClothingCategory.fashnCategory],
     * so the plan and the render answer this question the same way — they used
     * to hold separate tables, and only one of them was ever read.
     */
    private val SLOTS = mapOf(
        "top" to ClothingCategory.INNER, "tops" to ClothingCategory.INNER,
        "shirt" to ClothingCategory.INNER, "tee" to ClothingCategory.INNER,
        "inner" to ClothingCategory.INNER,
        "jacket" to ClothingCategory.OUTERWEAR, "outerwear" to ClothingCategory.OUTERWEAR,
        "bottoms" to ClothingCategory.PANTS, "bottom" to ClothingCategory.PANTS,
        "pants" to ClothingCategory.PANTS, "trousers" to ClothingCategory.PANTS,
        "jeans" to ClothingCategory.PANTS, "skirt" to ClothingCategory.PANTS,
        "set" to ClothingCategory.DRESS, "dress" to ClothingCategory.DRESS,
        "jumpsuit" to ClothingCategory.DRESS, "outfit" to ClothingCategory.DRESS,
        "shoes" to ClothingCategory.SHOES, "footwear" to ClothingCategory.SHOES,
        "sneakers" to ClothingCategory.SHOES,
        "bag" to ClothingCategory.BAG,
        "accessory" to ClothingCategory.ACCESSORY, "accessories" to ClothingCategory.ACCESSORY,
        "hat" to ClothingCategory.ACCESSORY, "jewellery" to ClothingCategory.ACCESSORY,
        "jewelry" to ClothingCategory.ACCESSORY
    )

    /** Category names the model uses as a line prefix when it drops "CAT:". */
    private val PREFIXES = SLOTS.keys + setOf("one-piece", "one-pieces")

    fun parse(text: String, ensureGender: (String) -> String): List<EbayTryOnCategory> =
        text.lines().mapNotNull { raw ->
            val line = raw.trim()
            val (name, rest) = split(line) ?: return@mapNotNull null
            val parts = rest.split("|")
            if (parts.size < 2) return@mapNotNull null

            // "CAT:shoes|reason|english|chinese" carries the name in the body;
            // "SHOES:reason|english|chinese" carries it in the prefix.
            val fields = if (name == null) parts else listOf("") + parts
            val category = (name ?: parts[0]).trim()
            if (fields.size < 3) return@mapNotNull null

            EbayTryOnCategory(
                name = category,
                reason = fields[1].trim(),
                fashnCategory = (SLOTS[category.lowercase()] ?: ClothingCategory.INNER).fashnCategory,
                searchQuery = ensureGender(fields[2].trim()),
                // The plan already wrote this, so the strip does not pay for a
                // second round trip to translate what the model just said.
                chineseQuery = fields.getOrNull(3)?.trim().orEmpty()
            )
        }

    /**
     * Splits a plan line into its category prefix, if it has one, and the rest.
     * Returns null for a line that is not a plan entry at all.
     */
    private fun split(line: String): Pair<String?, String>? {
        if (line.startsWith("CAT:", ignoreCase = true)) {
            return null to line.substring(4)
        }
        val colon = line.indexOf(':')
        if (colon > 0) {
            val head = line.substring(0, colon).trim().lowercase()
            if (head in PREFIXES) return head to line.substring(colon + 1)
        }
        // A third shape, seen live: "TOP|reason|english|chinese" — the category
        // as the first pipe field and no colon anywhere. It is the format the
        // closet-gap and complete-the-look prompts use, and the model carries it
        // across. Three shapes for one instruction is what asking a model for a
        // format actually gets you, so the parser accepts all of them rather
        // than the prompt being rewritten a fourth time.
        val bar = line.indexOf('|')
        if (bar > 0) {
            val head = line.substring(0, bar).trim().lowercase()
            if (head in PREFIXES) return head to line.substring(bar + 1)
        }
        return null
    }
}
