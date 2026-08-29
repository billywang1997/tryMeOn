package com.example.myapplication.data.sourcing

import com.example.myapplication.data.remote.ProductSearch
import com.example.myapplication.data.remote.SearchQuotaExceeded
import com.example.myapplication.data.remote.TaobaoItem
import com.example.myapplication.domain.model.ClothingCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Nothing matched" and "we can no longer search" look identical to a user and
 * call for opposite responses — try different words, versus wait or fix a key.
 * Reporting the first when the second is true sends people to rephrase a search
 * that was never going to run.
 */
class SearchFailureTest {

    private class Source(private val outcome: Result<List<TaobaoItem>>) : ProductSearch {
        override val name = "fake"
        override val available = true
        override suspend fun search(keyword: String, limit: Int) = outcome
    }

    private class Builder : SourcingQueryBuilder {
        override suspend fun build(
            englishDescription: String, gender: String, categoryHint: ClothingCategory?
        ) = Result.success(
            SourcingQuery(
                chineseQueries = listOf("亚麻小西装", "短款西装"),
                englishSummary = englishDescription,
                category = ClothingCategory.OUTERWEAR,
                parcel = com.example.myapplication.domain.sourcing.ParcelPresets
                    .forCategory(ClothingCategory.OUTERWEAR)
            )
        )
    }

    private fun repo(outcome: Result<List<TaobaoItem>>) =
        SourcingRepository(listOf(Source(outcome)), Builder())

    @Test
    fun `an exhausted quota is reported as such`() = runBlocking {
        val result = repo(Result.failure(SearchQuotaExceeded())).source("linen blazer")
        assertTrue(result.isFailure)
        assertTrue(
            "should not be reported as an unlucky search",
            result.exceptionOrNull() is SearchQuotaExceeded
        )
    }

    @Test
    fun `a genuinely empty result still reads as no matches`() = runBlocking {
        val result = repo(Result.success(emptyList())).source("linen blazer")
        assertTrue(result.isFailure)
        assertEquals(
            "No Taobao listings matched that",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun `a working source is not derailed by a broken one`() = runBlocking {
        val repo = SourcingRepository(
            sources = listOf(
                Source(Result.failure(SearchQuotaExceeded())),
                Source(Result.success(listOf(TaobaoItem(itemId = "1", title = "亚麻小西装", price = "128"))))
            ),
            queryBuilder = Builder()
        )
        val result = repo.source("linen blazer")
        assertTrue("a fallback source should still deliver", result.isSuccess)
        assertEquals(1, result.getOrThrow().listings.size)
    }
}
