package com.trymeon.app.ui.screens.tryon

import com.trymeon.app.domain.model.ClothingCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are real model output, captured by QualityReportTest over twelve
 * rounds. Three of those rounds produced nothing the app could use, which is
 * what these cases are about — invented examples would have all been in the
 * happy format the prompt asks for.
 */
class TryOnPlanParserTest {

    private fun parse(text: String) = TryOnPlanParser.parse(text) { it }

    @Test
    fun `the format the prompt asks for`() {
        val plan = """
            CAT:shoes|Pairs with all ensembles, adds casual polish|men's black leather sneakers|男士黑色皮革运动鞋
        """.trimIndent()
        val one = parse(plan).single()
        assertEquals("shoes", one.name)
        assertEquals("Pairs with all ensembles, adds casual polish", one.reason)
        assertEquals("men's black leather sneakers", one.searchQuery)
        assertEquals("男士黑色皮革运动鞋", one.chineseQuery)
    }

    @Test
    fun `the format the model actually used a quarter of the time`() {
        // No CAT: prefix; the category is the prefix. Every one of these lines
        // was silently dropped, and the strip in the app came up empty.
        val plan = """
            TOP: Pair with both bottoms and outerwear, 4 outfits|men's black graphic tee oversized cotton|男士黑色图案T恤宽松棉质
            BOTTOMS: Matches every top they own|men's grey wide leg trousers|男士灰色阔腿裤
        """.trimIndent()
        val got = parse(plan)
        assertEquals(2, got.size)
        assertEquals("men's black graphic tee oversized cotton", got[0].searchQuery)
        assertEquals("bottoms", got[1].name)
        assertEquals("男士灰色阔腿裤", got[1].chineseQuery)
    }

    @Test
    fun `an uppercase category is not rendered as a shirt`() {
        // Real output: CAT:SHOES|… The map was keyed lowercase, so this missed
        // and fell through to "tops" — asking the try-on service to put a pair
        // of shoes on the model's torso.
        val one = parse("CAT:SHOES|reason|men's black sneakers|男士黑色运动鞋").single()
        assertEquals("bottoms", one.fashnCategory)
    }

    @Test
    fun `spaces around the separators are not part of the answer`() {
        // Real output from the top budget band, which spaced its pipes out.
        val one = parse(
            "CAT: bag | Completes casual outfits | men's minimal black sling bag | 男士极简黑色斜挎包  "
        ).single()
        assertEquals("bag", one.name)
        assertEquals("men's minimal black sling bag", one.searchQuery)
        assertEquals("男士极简黑色斜挎包", one.chineseQuery)
    }

    @Test
    fun `every category the model has been seen to use maps to a real one`() {
        // The service takes exactly these three; anything else fails the render.
        val legal = setOf("tops", "bottoms", "one-pieces")
        val seen = listOf(
            "bottoms", "top", "bag", "accessory", "SHOES", "BAG",
            "set", "shoes", "dress"
        )
        seen.forEach { name ->
            val one = parse("CAT:$name|reason|query|中文").single()
            assertTrue(
                "$name mapped to ${one.fashnCategory}, which the service rejects",
                one.fashnCategory in legal
            )
        }
    }

    @Test
    fun `prose around the plan is not mistaken for part of it`() {
        val plan = """
            Here are my suggestions:
            CAT:shoes|reason|men's black sneakers|男士黑色运动鞋
            Let me know if you'd like more.
        """.trimIndent()
        assertEquals(1, parse(plan).size)
    }

    @Test
    fun `a truncated line is dropped rather than half read`() {
        assertEquals(0, parse("CAT:shoes|only a reason").size)
        assertEquals(0, parse("SHOES:").size)
    }

    @Test
    fun `the plan and the render agree on where a garment goes`() {
        // These used to be two tables in two files, and only one of them was
        // ever read: the render inferred from the product title instead, so a
        // pair of trousers went on the torso while this table said bottoms.
        mapOf(
            "shoes" to ClothingCategory.SHOES,
            "bottoms" to ClothingCategory.PANTS,
            "jacket" to ClothingCategory.OUTERWEAR,
            "dress" to ClothingCategory.DRESS,
            "bag" to ClothingCategory.BAG
        ).forEach { (name, slot) ->
            val parsed = parse("CAT:$name|reason|query|中文").single()
            assertEquals(
                "the plan disagrees with the slot for $name",
                slot.fashnCategory, parsed.fashnCategory
            )
        }
    }
}
