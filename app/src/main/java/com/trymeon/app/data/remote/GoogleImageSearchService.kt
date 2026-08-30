package com.trymeon.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object GoogleImageSearchService {

    private var apiKey: String = ""
    private var cx: String = ""

    /** Whether a product-image lookup can happen at all. */
    val configured: Boolean get() = apiKey.isNotBlank() && cx.isNotBlank()
    private val cache = ConcurrentHashMap<String, String>()
    private val client = RelayHttp.builder().build()

    // Fashion retailer sites known for clean product-only shots
    private val PRODUCT_SITES = "site:asos.com OR site:theiconic.com.au OR site:net-a-porter.com OR site:shopbop.com OR site:revolve.com"

    fun init(apiKey: String, cx: String) {
        this.apiKey = apiKey
        this.cx = cx
    }

    val isConfigured get() = apiKey.isNotBlank() && cx.isNotBlank()

    // Terms appended to every query to force face-free packshot results.
    private const val PACKSHOT = "ghost mannequin packshot product only"
    private const val EXCLUDE = "-model -person -woman -man -face -portrait -wearing"

    /**
     * Resolves a face-free product image URL for the given clothing query.
     * Every pass forces ghost-mannequin / packshot style and excludes people,
     * so wardrobe essentials never show a model's face.
     */
    suspend fun resolveUrl(query: String): String? = withContext(Dispatchers.IO) {
        cache[query]?.let { return@withContext it }
        if (!isConfigured) return@withContext null

        // Strip any gender words — they pull in model shots
        val clean = query
            .replace(Regex("(?i)\\b(women'?s?|men'?s?|woman|man|female|male|ghost mannequin|product)\\b"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

        // Pass 1: retailer-scoped packshot
        val retailer = searchGoogle("$clean $PACKSHOT ($PRODUCT_SITES) $EXCLUDE")
        if (retailer != null) {
            cache[query] = retailer
            return@withContext retailer
        }

        // Pass 2: all-web packshot
        val fallback = searchGoogle("$clean flat lay $PACKSHOT $EXCLUDE")
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
