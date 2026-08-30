package com.trymeon.app.data.sourcing

import com.trymeon.app.data.remote.ClaudeApiService
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.ClothingItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Advice is paid for once per wardrobe, not once per visit.
 *
 * The cache used to live on the service instance, which is built inside a
 * `composable { remember(...) }` — so navigating away and back rebuilt it empty
 * and asked the model again. The symptom was visible rather than subtle: the
 * shopping tab suggested four different things every time it was opened.
 */
class ClosetGapCacheTest {

    // The cache is deliberately process-wide, so one test would otherwise
    // answer the next one's question.
    @Before
    fun emptyTheCache() = ClosetGapService.clearCache()

    private val wardrobe = listOf(
        ClothingItem(1, "", ClothingCategory.OUTERWEAR, "shell jacket", "charcoal"),
        ClothingItem(2, "", ClothingCategory.PANTS, "straight jeans", "grey")
    )

    private class Counter {
        var calls = 0
        val ask: suspend (List<ClothingItem>, String, String) -> String = { _, _, _ ->
            calls++
            "GAP|white cotton t-shirt|layers under the jacket|白色纯棉T恤"
        }
    }

    private fun service(counter: Counter) =
        ClosetGapService(claude = null, apiKey = "key", ask = counter.ask)

    @Test
    fun `a second service instance reuses the first one's answer`() = runBlocking {
        val counter = Counter()

        val first = service(counter).gaps(wardrobe)
        // A fresh instance is exactly what navigating back produces.
        val second = service(counter).gaps(wardrobe)

        assertEquals(first, second)
        assertEquals("the model was asked twice for the same wardrobe", 1, counter.calls)
    }

    @Test
    fun `a changed wardrobe is asked again`() = runBlocking {
        val counter = Counter()
        service(counter).gaps(wardrobe)
        service(counter).gaps(
            wardrobe + ClothingItem(3, "", ClothingCategory.SHOES, "canvas low tops", "white")
        )
        assertEquals("adding a garment must change the advice", 2, counter.calls)
    }
}
