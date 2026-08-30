package com.trymeon.app.ui.screens.tryon

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
     * What the try-on service should treat the garment as.
     *
     * Its vocabulary is exactly three values — tops, bottoms, one-pieces — so
     * shoes and accessories have no home of their own and are placed on the
     * half of the body they belong to. Every value here must be one the
     * service accepts; anything else is rejected at the render.
     */
    private val FASHN = mapOf(
        "top" to "tops", "tops" to "tops", "shirt" to "tops", "tee" to "tops",
        "jacket" to "tops", "outerwear" to "tops", "inner" to "tops",
        "bottoms" to "bottoms", "bottom" to "bottoms", "pants" to "bottoms",
        "trousers" to "bottoms", "jeans" to "bottoms", "skirt" to "bottoms",
        "set" to "one-pieces", "dress" to "one-pieces", "jumpsuit" to "one-pieces",
        "outfit" to "one-pieces",
        "shoes" to "bottoms", "footwear" to "bottoms", "sneakers" to "bottoms",
        "bag" to "tops", "accessory" to "tops", "accessories" to "tops",
        "hat" to "tops", "jewellery" to "tops", "jewelry" to "tops"
    )

    /** Category names the model uses as a line prefix when it drops "CAT:". */
    private val PREFIXES = FASHN.keys + setOf("one-piece", "one-pieces")

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
                fashnCategory = FASHN[category.lowercase()] ?: "tops",
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
        if (colon <= 0) return null
        val head = line.substring(0, colon).trim().lowercase()
        if (head !in PREFIXES) return null
        return head to line.substring(colon + 1)
    }
}
