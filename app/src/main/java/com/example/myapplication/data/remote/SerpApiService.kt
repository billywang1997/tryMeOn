package com.example.myapplication.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "SerpAPI"
private const val BASE = "https://serpapi.com/search.json"

// Free tier: 100 searches/month — see https://serpapi.com/pricing
class SerpApiService {

    private val client = RelayHttp.builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        apiKey: String,
        query: String,
        country: String = "au",
        language: String = "en",
        limit: Int = 20
    ): Result<List<EbayItem>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("SerpAPI key not configured"))
        runCatching {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE?engine=google_shopping&q=$q&gl=$country&hl=$language&api_key=$apiKey"
            val req = Request.Builder().url(url).get().build()
            val body = client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string() ?: ""
                Log.d(TAG, "search status=${resp.code} chars=${raw.length}")
                if (!resp.isSuccessful) throw Exception("SerpAPI ${resp.code}: ${raw.take(200)}")
                raw
            }
            parseShoppingResults(body, limit)
        }
    }

    private fun parseShoppingResults(body: String, limit: Int): List<EbayItem> {
        val json = JSONObject(body)
        json.optJSONObject("error")?.let { throw Exception("SerpAPI error: ${json.optString("error")}") }
        val arr = json.optJSONArray("shopping_results") ?: return emptyList()
        val out = mutableListOf<EbayItem>()
        for (i in 0 until arr.length()) {
            if (out.size >= limit) break
            val o = arr.getJSONObject(i)
            val thumb = o.optString("thumbnail").takeIf { it.isNotBlank() } ?: continue
            val priceStr = o.optString("price")
            // SerpAPI gives "$29.99" — strip currency symbol for clean display, default currency = AUD
            val (currency, amount) = splitPrice(priceStr)
            val link = o.optString("product_link").ifBlank { o.optString("link") }
            val source = o.optString("source").ifBlank { "Google Shopping" }
            out.add(
                EbayItem(
                    itemId = "serp_${o.optString("product_id").ifBlank { o.optInt("position").toString() }}_$i",
                    title = o.optString("title"),
                    price = amount,
                    currency = currency,
                    condition = "",
                    imageUrl = thumb,
                    itemWebUrl = link,
                    source = source
                )
            )
        }
        return out
    }

    private fun splitPrice(raw: String): Pair<String, String> {
        if (raw.isBlank()) return "AUD" to ""
        // Match patterns like "$29.99", "AUD 29.99", "A$29.99", "₩30,000"
        val regex = Regex("""^([^\d.,\s]+|[A-Z]{2,4})\s?([\d.,]+)""")
        val m = regex.find(raw.trim())
        return if (m != null) {
            val sym = m.groupValues[1]
            val currency = when (sym.uppercase()) {
                "$", "A$", "AU$", "AUD" -> "AUD"
                "US$", "USD" -> "USD"
                "£", "GBP" -> "GBP"
                "€", "EUR" -> "EUR"
                "¥", "CNY", "RMB" -> "CNY"
                else -> sym
            }
            currency to m.groupValues[2]
        } else "AUD" to raw
    }
}
