package com.example.myapplication.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "AliExpressAPI"
private const val HOST = "aliexpress-datahub.p.rapidapi.com"
private const val BASE = "https://$HOST"

data class AliExpressItem(
    val itemId: String = "",
    val title: String = "",
    val price: String = "",
    val imageUrl: String = "",
    val itemUrl: String = ""
)

class AliExpressApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        apiKey: String,
        keyword: String,
        page: Int = 1
    ): Result<List<AliExpressItem>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("RapidAPI key not configured"))
        runCatching {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            // Try item_search first (text-based), item_search_image is visual-only
            val url = "$BASE/item_search?q=$encoded&page=$page&sort=default&catId=0"
            val req = Request.Builder()
                .url(url)
                .get()
                .header("x-rapidapi-host", HOST)
                .header("x-rapidapi-key", apiKey)
                .build()
            val body = client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string() ?: ""
                Log.d(TAG, "search status=${resp.code} body=${raw.take(400)}")
                raw
            }
            parseSearchResponse(body)
        }
    }

    private fun parseSearchResponse(body: String): List<AliExpressItem> {
        val root = JSONObject(body)
        val data = root.optJSONObject("result") ?: root.optJSONObject("data") ?: root
        val arr = data.optJSONArray("resultList")
            ?: data.optJSONArray("items")
            ?: data.optJSONArray("products")
            ?: return emptyList()
        val result = mutableListOf<AliExpressItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val item = obj.optJSONObject("item") ?: obj
            val id = item.optString("itemId").ifEmpty { item.optString("productId") }
            val title = item.optString("title").ifEmpty { item.optString("productTitle") }
            val priceNode = item.optJSONObject("sku")?.optJSONObject("def")
                ?: item.optJSONObject("price")
            val price = priceNode?.optString("promotionPrice")?.ifEmpty { priceNode.optString("price") }
                ?: item.optString("salePrice").ifEmpty { item.optString("price") }
            val imageUrl = item.optString("image").ifEmpty { item.optString("productMainImageUrl") }
            val itemUrl = if (id.isNotEmpty())
                "https://www.aliexpress.com/item/$id.html"
            else item.optString("productDetailUrl").ifEmpty { "" }
            val aliItem = AliExpressItem(itemId = id, title = title, price = price,
                imageUrl = ensureHttps(imageUrl), itemUrl = itemUrl)
            if (aliItem.title.isNotEmpty()) result.add(aliItem)
        }
        return result
    }

    private fun ensureHttps(url: String): String =
        if (url.startsWith("//")) "https:$url" else url
}
