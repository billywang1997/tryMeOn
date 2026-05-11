package com.example.myapplication.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object GoogleImageSearchService {

    private var apiKey: String = ""
    private var cx: String = ""
    private val cache = ConcurrentHashMap<String, String>()
    private val client = OkHttpClient()

    // Fashion retailer sites known for clean product-only shots
    private val PRODUCT_SITES = "site:asos.com OR site:theiconic.com.au OR site:net-a-porter.com OR site:shopbop.com OR site:revolve.com"

    fun init(apiKey: String, cx: String) {
        this.apiKey = apiKey
        this.cx = cx
    }

    val isConfigured get() = apiKey.isNotBlank() && cx.isNotBlank()

    /**
     * Resolves a product image URL for the given clothing query.
     * Strategy:
     *   1. Search within known fashion retailer sites (ASOS, The Iconic, etc.) — best product shots
     *   2. Fall back to all-web search with "-model -person" exclusions
     */
    suspend fun resolveUrl(query: String): String? = withContext(Dispatchers.IO) {
        cache[query]?.let { return@withContext it }
        if (!isConfigured) return@withContext null

        // Pass 1: retailer-scoped — gets clean e-commerce product shots
        val retailerResult = searchGoogle("$query ($PRODUCT_SITES)")
        if (retailerResult != null) {
            cache[query] = retailerResult
            return@withContext retailerResult
        }

        // Pass 2: all-web with model-exclusion terms
        val fallback = searchGoogle("$query flat lay ghost mannequin product -model -person -woman -man")
        if (fallback != null) cache[query] = fallback
        fallback
    }

    private suspend fun searchGoogle(fullQuery: String): String? = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(fullQuery, "UTF-8")
            val url = "https://www.googleapis.com/customsearch/v1" +
                "?key=$apiKey" +
                "&cx=$cx" +
                "&q=$encoded" +
                "&searchType=image" +
                "&imgType=photo" +
                "&imgSize=medium" +
                "&safe=active" +
                "&num=1"
            val body = client.newCall(Request.Builder().url(url).build())
                .execute().use { it.body?.string() } ?: return@withContext null
            JSONObject(body)
                .optJSONArray("items")
                ?.takeIf { it.length() > 0 }
                ?.getJSONObject(0)
                ?.getString("link")
        } catch (e: Exception) { null }
    }
}
