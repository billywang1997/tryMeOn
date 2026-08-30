package com.trymeon.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which half of the body the try-on dresses.
 *
 * Nothing passes an explicit category to the render — `overrideCategory` has no
 * caller — so this function decides for every garment. The descriptions are the
 * two real shapes it receives: "colour Category name" for a wardrobe item, and
 * a raw product title for a shop item.
 */
class InferCategoryTest {

    private val svc = ReplicateApiService()

    @Test
    fun `a wardrobe item carries its category in the label`() {
        assertEquals("bottoms", svc.inferCategory("grey Bottoms straight jeans"))
        assertEquals("tops", svc.inferCategory("black Top oversized crew tee"))
        assertEquals("tops", svc.inferCategory("charcoal Outerwear shell jacket"))
        assertEquals("one-pieces", svc.inferCategory("black Dress slip dress"))
    }

    @Test
    fun `footwear goes on the lower half, not the torso`() {
        // The service has no shoes category, but "Shoes" was matching nothing
        // and falling through to tops — the render put them on the chest.
        assertEquals("bottoms", svc.inferCategory("white Shoes canvas low tops"))
        assertEquals("bottoms", svc.inferCategory("Men's Black Leather Sneakers"))
        assertEquals("bottoms", svc.inferCategory("Chelsea Boots Brown Suede"))
    }

    @Test
    fun `trousers are trousers, whatever the stylist calls them`() {
        // Real recommendations from a quality run. "trousers" was not in the
        // vocabulary at all, so every pair was rendered as a top.
        assertEquals("bottoms", svc.inferCategory("men's black straight-leg trousers cotton"))
        assertEquals("bottoms", svc.inferCategory("navy chinos smart casual"))
        assertEquals("bottoms", svc.inferCategory("women's black leggings high waist"))
        assertEquals("bottoms", svc.inferCategory("men's grey joggers fleece"))
    }

    @Test
    fun `a short sleeve top is not a pair of shorts`() {
        // Substring matching read "short" out of "short-sleeve" and dressed the
        // model's legs in a t-shirt.
        assertEquals("tops", svc.inferCategory("men's short sleeve linen shirt"))
        assertEquals("tops", svc.inferCategory("Short Sleeve Polo Navy"))
        assertEquals("bottoms", svc.inferCategory("men's cargo shorts khaki"))
    }

    @Test
    fun `a dress shirt is a shirt and dress shoes are shoes`() {
        // Only word order distinguishes these from a dress.
        assertEquals("tops", svc.inferCategory("white dress shirt slim fit"))
        assertEquals("bottoms", svc.inferCategory("black dress shoes leather"))
        assertEquals("one-pieces", svc.inferCategory("floral wrap dress midi"))
    }

    @Test
    fun `an unrecognised garment goes on the torso`() {
        // Tops is the safest of the three for something unnameable.
        assertEquals("tops", svc.inferCategory("beige Accessory bucket hat"))
        assertEquals("tops", svc.inferCategory(""))
    }
}
