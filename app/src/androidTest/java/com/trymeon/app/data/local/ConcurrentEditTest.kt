package com.trymeon.app.data.local

import androidx.test.platform.app.InstrumentationRegistry
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.ClothingItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Whether concurrent edits lose each other.
 *
 * Every mutation is a read-modify-write over the whole stored list, launched
 * from its own coroutine: two edits in flight at once is the normal case, not
 * an exotic one — a double tap on favourite, or cloud sync writing while the
 * user is editing. If those transactions are not serialised, the later write
 * overwrites a list read before the earlier one landed and an item silently
 * disappears.
 */
class ConcurrentEditTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = DataStoreManager(ctx)

    @Before
    fun clear() = runBlocking {
        store.getAllClothingOnce().forEach { store.deleteClothing(it.id) }
    }

    @Test
    fun concurrentAddsAllSurvive() = runBlocking {
        val n = 30
        coroutineScope {
            (1..n).map { i ->
                async {
                    store.addClothing(
                        ClothingItem(0, "/$i.png", ClothingCategory.INNER, "tee $i", "black")
                    )
                }
            }.awaitAll()
        }

        val stored = store.getAllClothingOnce()
        assertEquals("an add was lost to a concurrent one", n, stored.size)
        // Ids are handed out from a stored counter inside the same transaction,
        // so a race there would show up as a collision rather than a gap.
        assertEquals("duplicate ids handed out", n, stored.map { it.id }.toSet().size)
    }

    @Test
    fun concurrentEditsOfDifferentItemsBothStick() = runBlocking {
        val a = store.addClothing(ClothingItem(0, "/a.png", ClothingCategory.INNER, "tee", "black"))
        val b = store.addClothing(ClothingItem(0, "/b.png", ClothingCategory.PANTS, "jeans", "grey"))

        coroutineScope {
            listOf(
                async { store.toggleFavorite(a) },
                async { store.toggleFavorite(b) }
            ).awaitAll()
        }

        val stored = store.getAllClothingOnce().associateBy { it.id }
        assertEquals("one favourite was overwritten by the other", true, stored[a]?.isFavorite)
        assertEquals("one favourite was overwritten by the other", true, stored[b]?.isFavorite)
    }

    @Test
    fun concurrentDeletesDoNotResurrectAnything() = runBlocking {
        val ids = (1..10).map { i ->
            store.addClothing(ClothingItem(0, "/$i.png", ClothingCategory.INNER, "tee $i", "black"))
        }

        coroutineScope {
            ids.take(5).map { id -> async { store.deleteClothing(id) } }.awaitAll()
        }

        assertEquals(5, store.getAllClothingOnce().size)
    }
}
