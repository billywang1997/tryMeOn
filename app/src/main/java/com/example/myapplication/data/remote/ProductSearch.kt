package com.example.myapplication.data.remote

/**
 * A source of Taobao listings.
 *
 * Two implementations exist on purpose. The affiliate API is the real
 * integration — official data, and commission on what it sells — but it cannot
 * be switched on without an Alimama account and media registration. Until then
 * the scraper keeps the feature usable. Making them interchangeable means
 * activating the real one is a configuration change, not a rewrite.
 */
interface ProductSearch {
    val name: String

    /** False when this source lacks the credentials or relay it needs. */
    val available: Boolean

    suspend fun search(keyword: String, limit: Int = 20): Result<List<TaobaoItem>>
}

/** Adapts the RapidAPI scraper to [ProductSearch]. */
class ScraperProductSearch(
    private val service: TaobaoApiService,
    private val rapidApiKey: String
) : ProductSearch {
    override val name = "Taobao (unofficial)"
    override val available: Boolean get() = rapidApiKey.isNotBlank()
    override suspend fun search(keyword: String, limit: Int) =
        service.search(rapidApiKey, keyword, pageSize = limit)
}
