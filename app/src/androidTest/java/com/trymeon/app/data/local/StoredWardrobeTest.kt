package com.trymeon.app.data.local

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.model.WishlistItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What a real store does with records an older or newer build wrote.
 *
 * Run against the actual DataStore rather than a Gson call, because the thing
 * being checked is the read-modify-write cycle: every write here reads the
 * whole list back, converts it and overwrites, so a row the build cannot
 * interpret is one careless map away from being deleted.
 */
class StoredWardrobeTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = DataStoreManager(ctx)
    private val gson = Gson()

    @Before
    fun clear() = runBlocking {
        store.getAllClothingOnce().forEach { store.deleteClothing(it.id) }
        store.wishlistFlow.first().forEach { store.removeWishlistItem(it.id) }
    }

    @Test
    fun aGarmentInAnUnknownCategoryDoesNotEmptyTheWardrobe() = runBlocking {
        store.addClothing(
            ClothingItem(0, "/a.png", ClothingCategory.OUTERWEAR, "shell jacket", "black")
        )
        writeRawCategory("ACCESSORY_FROM_A_NEWER_BUILD")

        val read = store.getAllClothingOnce()
        // The readable garment is still there; only the row we cannot interpret
        // is withheld, and the user keeps a wardrobe rather than losing it all.
        assertEquals(1, read.size)
        assertEquals("shell jacket", read.single().name)
    }

    @Test
    fun anUnreadableGarmentSurvivesAWriteInsteadOfBeingDeleted() = runBlocking {
        store.addClothing(
            ClothingItem(0, "/a.png", ClothingCategory.OUTERWEAR, "shell jacket", "black")
        )
        writeRawCategory("ACCESSORY_FROM_A_NEWER_BUILD")

        // Any edit rewrites the whole list. The row this build cannot read must
        // come back out of storage afterwards, or a downgrade destroys data.
        store.addClothing(ClothingItem(0, "/b.png", ClothingCategory.PANTS, "cargo pants", "black"))

        val raw = rawJson()
        assertTrue(
            "the unreadable row was dropped by a write: $raw",
            raw.contains("ACCESSORY_FROM_A_NEWER_BUILD")
        )
        assertEquals(2, store.getAllClothingOnce().size)
    }

    @Test
    fun aWishlistRecordMissingFieldsRendersInsteadOfCrashing() = runBlocking {
        store.saveWishlistItem(
            WishlistItem(id = "keep", title = "Blazer", source = "AliExpress")
        )
        // A record as an older build wrote it: no source, no currency.
        val legacy = """[{"id":"old","title":"Older Blazer"}]"""
        writeRawWishlist(legacy)

        val items = store.wishlistFlow.first()
        val old = items.single { it.id == "old" }
        // The screen calls source.uppercase(); this is the call that crashed.
        assertEquals("", old.source.uppercase())
        assertEquals("AUD", old.currency)
    }

    // ── raw storage helpers ─────────────────────────────────────────────────

    private suspend fun writeRawCategory(category: String) {
        val existing = gson.fromJson(rawJson(), List::class.java) ?: emptyList<Any>()
        val row = mapOf(
            "id" to 99, "imagePath" to "/x.png", "category" to category,
            "name" to "mystery", "createdAt" to 1
        )
        store.writeRawClothingJson(gson.toJson(existing + listOf(row)))
    }

    private suspend fun rawJson(): String = store.readRawClothingJson()

    private suspend fun writeRawWishlist(json: String) {
        val current = gson.fromJson(store.readRawWishlistJson(), List::class.java)
            ?: emptyList<Any>()
        val extra = gson.fromJson(json, List::class.java) ?: emptyList<Any>()
        store.writeRawWishlistJson(gson.toJson(current + extra))
    }
}
