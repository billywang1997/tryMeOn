package com.trymeon.app.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "VintedAPI"
private const val HOST = "vinted3.p.rapidapi.com"
private const val BASE = "https://$HOST"

data class VintedItem(
    val id: String = "",
    val title: String = "",
    val price: String = "",
    val currency: String = "",
    val imageUrl: String = "",
    val itemUrl: String = "",
    val brand: String = "",
    val size: String = ""
)

class VintedApiService {

    private val client = RelayHttp.builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        apiKey: String,
        keyword: String,
        country: String = "us",
        page: Int = 1
    ): Result<List<VintedItem>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("RapidAPI key not configured"))
        runCatching {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "$BASE/getSearch?search_text=$encoded&country=$country&page=$page&order=newest_first"
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

    private fun parseSearchResponse(body: String): List<VintedItem> {
        // API returns a top-level array or {"items":[...]} wrapper
        val arr = try {
            org.json.JSONArray(body)
        } catch (_: Exception) {
            val root = JSONObject(body)
            root.optJSONArray("items")
                ?: root.optJSONObject("data")?.optJSONArray("items")
                ?: root.optJSONArray("results")
                ?: return emptyList()
        }
        val result = mutableListOf<VintedItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            // productId is a long, convert to string
            val id = obj.optLong("productId").takeIf { it != 0L }?.toString()
                ?: obj.optString("id")
            val title = obj.optString("title").ifEmpty { obj.optString("description") }
            // image is a direct URL string, not a nested object
            val imageUrl = obj.optString("image").ifEmpty {
                obj.optJSONObject("photo")?.optString("full_size_url")
                    ?: obj.optString("image_url")
            }
            val itemUrl = obj.optString("url").ifEmpty {
                if (id != null) "https://www.vinted.com/items/$id" else ""
            }
            // price is nested {amount, currency_code}
            val priceNode = obj.optJSONObject("price")
            val price = priceNode?.optString("amount") ?: obj.optString("price")
            val currency = priceNode?.optString("currency_code") ?: ""
            // brand is a plain string (not a nested object in this API)
            val brand = obj.optString("brand").ifEmpty {
                obj.optJSONObject("brand")?.optString("title") ?: ""
            }
            val size = obj.optString("size").ifEmpty {
                obj.optJSONObject("size")?.optString("title") ?: ""
            }
            val item = VintedItem(
                id = id ?: "", title = title, price = price, currency = currency,
                imageUrl = imageUrl, itemUrl = itemUrl, brand = brand, size = size
            )
            if (item.title.isNotEmpty()) result.add(item)
        }
        return result
    }
}
