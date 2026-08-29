package com.example.myapplication.data.sourcing

import com.example.myapplication.data.remote.TaobaoItem
import com.example.myapplication.domain.model.ClothingCategory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The handoff is a one-shot: consumed twice it would silently re-add a garment
 * the user already removed, and never cleared it would follow them around the app.
 */
class PendingTryOnTest {

    private val listing = TaobaoItem(
        itemId = "654321",
        title = "夏季薄款亚麻小西装外套女",
        price = "128.00",
        imageUrl = "https://img.alicdn.com/a.jpg",
        itemUrl = "https://item.taobao.com/item.htm?id=654321"
    )

    @Before fun reset() = PendingTryOn.clear()
    @After fun cleanUp() = PendingTryOn.clear()

    @Test
    fun `carries the listing across as a garment`() {
        PendingTryOn.offer(listing, ClothingCategory.OUTERWEAR, priceCny = 128.0, landedAud = 47.35)
        val garment = PendingTryOn.consume()!!
        assertEquals(ClothingCategory.OUTERWEAR, garment.category)
        assertEquals("654321", garment.item.itemId)
        assertEquals("https://img.alicdn.com/a.jpg", garment.item.imageUrl)
        assertEquals("Taobao", garment.item.source)
    }

    @Test
    fun `carries the landed price, not the sticker`() {
        // The sticker is the number that misleads; the try-on screen should
        // repeat what the thing actually costs.
        PendingTryOn.offer(listing, ClothingCategory.OUTERWEAR, priceCny = 128.0, landedAud = 47.35)
        val garment = PendingTryOn.consume()!!
        assertEquals("47.35", garment.item.price)
        assertEquals("AUD", garment.item.currency)
    }

    @Test
    fun `is consumed exactly once`() {
        PendingTryOn.offer(listing, ClothingCategory.DRESS, 128.0, 47.35)
        assertEquals(ClothingCategory.DRESS, PendingTryOn.consume()?.category)
        assertNull("a second read must not re-add the garment", PendingTryOn.consume())
    }

    @Test
    fun `nothing pending reads as nothing`() {
        assertNull(PendingTryOn.consume())
    }

    @Test
    fun `a newer offer replaces an unconsumed one`() {
        PendingTryOn.offer(listing, ClothingCategory.DRESS, 128.0, 47.35)
        PendingTryOn.offer(listing.copy(itemId = "999"), ClothingCategory.SHOES, 88.0, 30.0)
        val garment = PendingTryOn.consume()!!
        assertEquals("999", garment.item.itemId)
        assertEquals(ClothingCategory.SHOES, garment.category)
    }
}
