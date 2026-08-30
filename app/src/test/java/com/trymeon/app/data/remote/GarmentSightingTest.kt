package com.trymeon.app.data.remote

import com.trymeon.app.domain.model.ClothingCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reading a photo lookup's reply.
 *
 * Written tolerantly on purpose: a quality run over twelve stylist rounds found
 * the model dropping its prefix in a quarter of them, and there is no reason to
 * expect better discipline here. A dropped prefix should cost nothing.
 */
class GarmentSightingTest {

    @Test
    fun `the shape the prompt asks for`() {
        val seen = GarmentSighting.parse("SEEN:Shoes|black chunky leather sneakers")!!
        assertEquals("black chunky leather sneakers", seen.query)
        assertEquals(ClothingCategory.SHOES, seen.category)
    }

    @Test
    fun `a dropped prefix still reads`() {
        val seen = GarmentSighting.parse("Outerwear|beige oversized wool coat")!!
        assertEquals("beige oversized wool coat", seen.query)
        assertEquals(ClothingCategory.OUTERWEAR, seen.category)
    }

    @Test
    fun `prose around the answer is ignored`() {
        val seen = GarmentSighting.parse(
            "Here is what I see:\nSEEN:Bottoms|grey straight leg trousers\nHope that helps."
        )!!
        assertEquals("grey straight leg trousers", seen.query)
        assertEquals(ClothingCategory.PANTS, seen.category)
    }

    @Test
    fun `spacing around the separator is not part of the answer`() {
        val seen = GarmentSighting.parse("SEEN: Dress | black midi slip dress ")!!
        assertEquals("black midi slip dress", seen.query)
        assertEquals(ClothingCategory.DRESS, seen.category)
    }

    @Test
    fun `an unrecognised category is still searchable`() {
        // The description is what the search uses; the category only steers
        // where a try-on would put it, so an odd word must not lose the answer.
        val seen = GarmentSighting.parse("SEEN:Knitwear|cream cable knit jumper")!!
        assertEquals("cream cable knit jumper", seen.query)
        assertEquals(ClothingCategory.INNER, seen.category)
    }

    @Test
    fun `nothing usable is null, not an empty search`() {
        // An empty query would run a search for nothing and show an empty grid
        // as though the shops had let the user down.
        assertNull(GarmentSighting.parse(""))
        assertNull(GarmentSighting.parse("I can't tell what that is."))
        assertNull(GarmentSighting.parse("SEEN:Shoes|"))
    }
}
