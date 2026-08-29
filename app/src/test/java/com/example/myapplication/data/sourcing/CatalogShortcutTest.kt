package com.example.myapplication.data.sourcing

import com.example.myapplication.data.remote.ProductSearch
import com.example.myapplication.data.remote.TaobaoItem
import com.example.myapplication.domain.model.ClothingCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A recommendation is written by a model that reads Chinese, so asking it for
 * the search phrase in the same breath turns N+1 chat calls into one. These pin
 * that the shortcut is actually taken — if it silently fell back to translating,
 * nothing would look broken except the bill and a thirteen-second wait.
 */
class CatalogShortcutTest {

    private class CountingBuilder : SourcingQueryBuilder {
        var calls = 0
        override suspend fun build(
            englishDescription: String,
            gender: String,
            categoryHint: ClothingCategory?
        ): Result<SourcingQuery> {
            calls++
            return Result.success(
                SourcingQuery(
                    chineseQueries = listOf("翻译出来的词"),
                    englishSummary = englishDescription,
                    category = categoryHint ?: ClothingCategory.INNER,
                    parcel = com.example.myapplication.domain.sourcing.ParcelPresets
                        .forCategory(categoryHint ?: ClothingCategory.INNER)
                )
            )
        }
    }

    private class FakeSource(private val seen: MutableList<String>) : ProductSearch {
        override val name = "fake"
        override val available = true
        override suspend fun search(keyword: String, limit: Int): Result<List<TaobaoItem>> {
            seen += keyword
            return Result.success(
                listOf(TaobaoItem(itemId = "1", title = "亚麻小西装", price = "128.00"))
            )
        }
    }

    @Test
    fun `a supplied Chinese phrase skips the model entirely`() = runBlocking {
        val builder = CountingBuilder()
        val searched = mutableListOf<String>()
        val catalog = ShoppingCatalog(listOf(FakeSource(searched)), builder)

        val items = catalog.search(
            englishQuery = "cropped linen blazer",
            categoryHint = ClothingCategory.OUTERWEAR,
            chineseQuery = "亚麻短款西装外套"
        )

        assertEquals("no translation should have been paid for", 0, builder.calls)
        assertEquals(listOf("亚麻短款西装外套"), searched)
        assertTrue(items.isNotEmpty())
    }

    @Test
    fun `without one it falls back to translating`() = runBlocking {
        val builder = CountingBuilder()
        val searched = mutableListOf<String>()
        val catalog = ShoppingCatalog(listOf(FakeSource(searched)), builder)

        catalog.search("cropped linen blazer", categoryHint = ClothingCategory.OUTERWEAR)

        assertEquals(1, builder.calls)
        assertEquals(listOf("翻译出来的词"), searched)
    }

    @Test
    fun `a blank Chinese phrase is not treated as one`() = runBlocking {
        val builder = CountingBuilder()
        val catalog = ShoppingCatalog(listOf(FakeSource(mutableListOf())), builder)
        // An empty field from a model that dropped the column must not become a
        // search for nothing.
        catalog.search("linen blazer", chineseQuery = "   ")
        assertEquals(1, builder.calls)
    }

    @Test
    fun `results carry the landed price, not the yuan sticker`() = runBlocking {
        val catalog = ShoppingCatalog(listOf(FakeSource(mutableListOf())), CountingBuilder())
        val item = catalog.search("linen blazer", chineseQuery = "亚麻小西装").first()

        assertEquals("AUD", item.currency)
        assertEquals("Taobao", item.source)
        val shown = item.price.toDouble()
        // ¥128 converts to about A$26 before freight and GST; anything at or
        // below that means a sticker leaked through as the landed figure.
        assertTrue("price $shown looks like an unconverted sticker", shown > 28.0)
    }
}
