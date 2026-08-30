package com.trymeon.app.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.WishlistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * What happens to a record written by an older build.
 *
 * Wishlist items are persisted as Gson JSON in DataStore, and Gson does not run
 * Kotlin constructor defaults — a field absent from the stored JSON is set to
 * null even where the declared type is non-null. The type system then says the
 * value cannot be null and the compiler inserts no check, so the failure
 * surfaces far away, as a crash in whatever first touches the field.
 */
class StoredRecordCompatTest {

    private val gson = Gson()
    private val listType = object : TypeToken<List<WishlistItem>>() {}.type

    private fun read(json: String): List<WishlistItem> = gson.fromJson(json, listType)

    @Test
    fun `a record from before a field existed does not carry a default`() {
        val item = read("""[{"id":"abc","title":"Linen Blazer"}]""").single()

        assertEquals("abc", item.id)
        @Suppress("SENSELESS_COMPARISON")
        val sourceIsNull = item.source == null
        assertEquals(
            "Gson leaves an absent String null; the declared default does not run",
            true, sourceIsNull
        )
    }

    @Test
    fun `the wishlist screen would crash on such a record`() {
        val item = read("""[{"id":"abc","title":"Linen Blazer"}]""").single()

        // WishlistScreen renders item.source.uppercase(). On a record written
        // before `source` existed that is a null receiver.
        val threw = runCatching { item.source.uppercase() }.exceptionOrNull()
        assertNotNull("expected the null receiver this test exists to document", threw)
    }

    @Test
    fun `a record written by the current build round-trips intact`() {
        val written = WishlistItem(
            id = "abc", title = "Linen Blazer", source = "AliExpress",
            currency = "AUD", savedPrice = "50.10"
        )
        assertEquals(written, read("[" + gson.toJson(written) + "]").single())
    }

    @Test
    fun `an unknown category name takes the whole wardrobe down, not one item`() {
        // Categories are stored by enum name. A record written by a build that
        // had a category this one does not — an install rolled back, a sync
        // from a newer device — reaches valueOf, which throws. The read maps
        // over the whole list, so one bad row means an empty wardrobe rather
        // than a missing garment.
        val threw = runCatching { ClothingCategory.valueOf("ACCESSORY_NEW") }.exceptionOrNull()
        assertNotNull("expected valueOf to reject an unknown name", threw)

        // What a tolerant read would do instead.
        val tolerant = ClothingCategory.entries.firstOrNull { it.name == "ACCESSORY_NEW" }
        assertEquals(null, tolerant)
    }
}
