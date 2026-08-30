package com.trymeon.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "ScraperAPI"
private const val BASE = "https://api.scraperapi.com/structured/amazon/search"

// Drop-in replacement for AmazonRealTimeApiService.
// Free tier: 1000 credits/month (~100-200 Amazon searches). See https://www.scraperapi.com/pricing
class ScraperApiService {

    private val gson = Gson()
    private val client = RelayHttp.builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // ScraperAPI may hold the request for several seconds while it scrapes & parses.
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        scraperApiKey: String,
        query: String,
        limit: Int = 10,
        country: String = "au"
    ): Result<List<EbayItem>> = withContext(Dispatchers.IO) {
        if (scraperApiKey.isBlank()) return@withContext Result.failure(Exception("ScraperAPI key not set"))
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE?api_key=$scraperApiKey&query=$q&country=$country"

            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            Log.d(TAG, "search status=${resp.code} chars=${body.length}")
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("ScraperAPI ${resp.code}: ${body.take(200)}"))
            }

            val parsed = gson.fromJson(body, ScraperAmazonResponse::class.java)
            val items = parsed.results
                ?.take(limit)
                ?.mapNotNull { p ->
                    val photo = p.image?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val rawPrice = p.price ?: p.priceString ?: ""
                    val priceDigits = rawPrice.replace(Regex("[^0-9.]"), "").trimEnd('.')
                    val title = p.name ?: return@mapNotNull null
                    val asin = p.asin ?: ""
                    val link = p.url
                        ?: asin.takeIf { it.isNotBlank() }?.let { "https://www.amazon.${countryTld(country)}/dp/$it" }
                        ?: ""
                    EbayItem(
                        itemId    = "amzn_${asin.ifBlank { System.nanoTime().toString() }}",
                        title     = title,
                        price     = priceDigits,
                        currency  = "AUD",
                        condition = "New",
                        imageUrl  = photo,
                        itemWebUrl = link,
                        source    = "Amazon"
                    )
                } ?: emptyList()

            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "error: ${e.message}")
            Result.failure(Exception("Amazon (ScraperAPI): ${e.message}"))
        }
    }

    private fun countryTld(country: String) = when (country.lowercase()) {
        "au" -> "com.au"
        "uk", "gb" -> "co.uk"
        "us" -> "com"
        else -> "com"
    }
}

private data class ScraperAmazonResponse(
    val results: List<ScraperAmazonProduct>? = null
)

private data class ScraperAmazonProduct(
    val asin: String? = null,
    val name: String? = null,
    val image: String? = null,
    val url: String? = null,
    val price: String? = null,
    @SerializedName("price_string") val priceString: String? = null
)
