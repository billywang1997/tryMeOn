package com.example.myapplication.data.remote

import android.text.Html
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val AMZN_TAG = "AmazonRealTimeAPI"
private const val AMZN_HOST = "real-time-amazon-data.p.rapidapi.com"

class AmazonRealTimeApiService {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        rapidApiKey: String,
        query: String,
        limit: Int = 10
    ): Result<List<EbayItem>> = withContext(Dispatchers.IO) {
        if (rapidApiKey.isBlank()) return@withContext Result.failure(Exception("RapidAPI key not set"))
        try {
            val q = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://$AMZN_HOST/search" +
                "?query=$q&page=1&country=AU&sort_by=RELEVANCE&product_condition=ALL"

            val req = Request.Builder()
                .url(url)
                .header("X-RapidAPI-Key", rapidApiKey)
                .header("X-RapidAPI-Host", AMZN_HOST)
                .get()
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            Log.d(AMZN_TAG, "search status=${resp.code}")

            if (!resp.isSuccessful) return@withContext Result.failure(Exception("Amazon ${resp.code}"))

            val result = gson.fromJson(body, AmazonSearchResponse::class.java)
            val items = result.data?.products
                ?.take(limit)
                ?.mapNotNull { p ->
                    val photo = p.productPhoto?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val rawPrice = p.productPrice ?: p.productMinimumOfferPrice ?: ""
                    // Strip currency symbol, keep digits + decimal
                    val price = rawPrice.replace(Regex("[^0-9.]"), "").trimEnd('.')
                    val title = Html.fromHtml(p.productTitle ?: "", Html.FROM_HTML_MODE_LEGACY).toString()
                    EbayItem(
                        itemId    = "amzn_${p.asin}",
                        title     = title,
                        price     = price,
                        currency  = "AUD",
                        condition = "New",
                        imageUrl  = photo,
                        itemWebUrl = p.productUrl ?: "",
                        source    = "Amazon"
                    )
                } ?: emptyList()

            Result.success(items)
        } catch (e: Exception) {
            Log.e(AMZN_TAG, "error: ${e.message}")
            Result.failure(Exception("Amazon: ${e.message}"))
        }
    }
}

private data class AmazonSearchResponse(
    val status: String? = null,
    val data: AmazonData? = null
)

private data class AmazonData(
    val products: List<AmazonProduct>? = null
)

private data class AmazonProduct(
    val asin: String? = null,
    @SerializedName("product_title")         val productTitle: String? = null,
    @SerializedName("product_price")         val productPrice: String? = null,
    @SerializedName("product_minimum_offer_price") val productMinimumOfferPrice: String? = null,
    @SerializedName("product_url")           val productUrl: String? = null,
    @SerializedName("product_photo")         val productPhoto: String? = null
)
