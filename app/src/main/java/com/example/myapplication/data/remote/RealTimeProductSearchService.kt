package com.example.myapplication.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "RTProductSearch"
private const val HOST = "real-time-product-search.p.rapidapi.com"
private const val BASE = "https://$HOST"

class RealTimeProductSearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Search across Google Shopping (aggregates SHEIN, Zara, H&M, ASOS, etc.)
     * Returns EbayItem list with source = "Web".
     */
    suspend fun search(
        apiKey: String,
        query: String,
        country: String = "AU",
        limit: Int = 10
    ): List<EbayItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val safeLimit = limit.coerceIn(1, 40)
            val url = "$BASE/search-v2?q=$encoded&country=$country&language=en&page=1&limit=$safeLimit&sort_by=BEST_MATCH&product_condition=ANY"
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
            parseResponse(body)
        }.getOrElse { e ->
            Log.w(TAG, "search failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseResponse(body: String): List<EbayItem> {
        // Shape: { "status": "OK", "request_id": "...", "data": { "products": [ { "product_id", "product_title", "product_photos": ["url",...], "offer": { "price", "store_name", "store_rating", "offer_page_url" } }, ... ] } }
        val root = JSONObject(body)
        val data = root.optJSONObject("data") ?: return emptyList()
        val arr = data.optJSONArray("products") ?: return emptyList()
        val result = mutableListOf<EbayItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val title = obj.optString("product_title").ifEmpty { obj.optString("product_description") }
            if (title.isEmpty()) continue

            // Best offer
            val offer = obj.optJSONObject("offer")
                ?: obj.optJSONArray("offers")?.optJSONObject(0)
            val price = offer?.optString("price") ?: obj.optString("typical_price_range")
            val url = offer?.optString("offer_page_url") ?: ""
            val storeName = offer?.optString("store_name") ?: "Shop"

            // First product photo
            val photos = obj.optJSONArray("product_photos")
            val imageUrl = photos?.optString(0)
                ?: obj.optString("product_photo")

            result.add(
                EbayItem(
                    itemId     = "rtps_${obj.optString("product_id").ifEmpty { i.toString() }}",
                    title      = title,
                    price      = price,
                    currency   = "AUD",
                    imageUrl   = imageUrl,
                    itemWebUrl = url,
                    source     = storeName.ifEmpty { "Web" }
                )
            )
        }
        return result
    }
}
