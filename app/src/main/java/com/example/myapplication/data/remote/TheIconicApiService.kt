package com.example.myapplication.data.remote

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val ICONIC_TAG = "TheIconicAPI"
private const val ICONIC_HOST = "theiconic.p.rapidapi.com"

class TheIconicApiService {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        rapidApiKey: String,
        query: String,
        limit: Int = 12
    ): Result<List<EbayItem>> = withContext(Dispatchers.IO) {
        if (rapidApiKey.isBlank()) return@withContext Result.failure(Exception("RapidAPI key not set"))
        try {
            val q = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://$ICONIC_HOST/products/search?q=$q&limit=$limit&country=AU&currency=AUD"

            val req = Request.Builder()
                .url(url)
                .header("X-RapidAPI-Key", rapidApiKey)
                .header("X-RapidAPI-Host", ICONIC_HOST)
                .get()
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            Log.d(ICONIC_TAG, "search status=${resp.code}")

            if (!resp.isSuccessful) return@withContext Result.failure(Exception("The Iconic ${resp.code}"))

            val result = gson.fromJson(body, IconicSearchResponse::class.java)
            val items = result.products?.mapNotNull { p ->
                val imgUrl = p.imageUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                EbayItem(
                    itemId    = "iconic_${p.sku ?: p.id ?: System.nanoTime().toString()}",
                    title     = p.name ?: "",
                    price     = p.price?.let { "%.2f".format(it) } ?: p.priceStr ?: "",
                    currency  = "AUD",
                    condition = "New",
                    imageUrl  = imgUrl,
                    itemWebUrl = p.url ?: "",
                    source    = "The Iconic"
                )
            } ?: emptyList()

            Result.success(items)
        } catch (e: Exception) {
            Log.e(ICONIC_TAG, "error: ${e.message}")
            Result.failure(Exception("The Iconic: ${e.message}"))
        }
    }
}

private data class IconicSearchResponse(val products: List<IconicProduct>? = null)

private data class IconicProduct(
    val id: Any? = null,
    val sku: String? = null,
    val name: String? = null,
    val price: Double? = null,
    val priceStr: String? = null,
    val imageUrl: String? = null,
    val url: String? = null
)
