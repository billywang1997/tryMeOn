package com.trymeon.app.data.sourcing

import com.trymeon.app.data.remote.ProductSearch
import com.trymeon.app.data.remote.SearchQuotaExceeded
import com.trymeon.app.data.remote.SearchUnavailable
import com.trymeon.app.data.remote.TaobaoItem
import com.trymeon.app.domain.model.ClothingCategory
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

    private class Source(
        private val outcome: Result<List<TaobaoItem>>,
        override val available: Boolean = true
    ) : ProductSearch {
        override val name = "fake"
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
                parcel = com.trymeon.app.domain.sourcing.ParcelPresets
                    .forCategory(ClothingCategory.OUTERWEAR)
            )
        )
    }

    private fun repo(outcome: Result<List<TaobaoItem>>, available: Boolean = true) =
        SourcingRepository(listOf(Source(outcome, available)), Builder())

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
            "Nothing matched that — try fewer words",
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

    @Test
    fun `no configured source says so, rather than blaming the words`() {
        // Different from finding nothing: we never looked. Reporting it as a
        // miss sends someone off rewording a query that was never the problem.
        runBlocking {
            val e = repo(Result.success(emptyList()), available = false)
                .source("linen blazer").exceptionOrNull()
            assertTrue("expected an availability failure, got $e", e is SearchUnavailable)
            assertTrue(e!!.message!!.contains("not set up"))
        }
    }

    @Test
    fun `no failure names a marketplace at the shopper`() = runBlocking {
        // Several marketplaces feed this now, and which one answered is not
        // something a shopper should have to know about.
        listOf(
            repo(Result.success(emptyList()), available = false),
            repo(Result.success(emptyList()))
        ).forEach {
            val msg = it.source("linen blazer").exceptionOrNull()?.message.orEmpty()
            assertTrue(
                "leaked a marketplace name: $msg",
                !msg.contains("Taobao") && !msg.contains("AliExpress")
            )
        }
    }
}
