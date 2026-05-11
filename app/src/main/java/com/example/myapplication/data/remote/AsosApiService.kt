package com.example.myapplication.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val ASOS_TAG = "AsosAPI"
private const val ASOS_HOST = "asos2.p.rapidapi.com"

class AsosApiService {

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
            val url = "https://$ASOS_HOST/products/v2/list" +
                "?store=AU&offset=0&country=AU&sort=freshness&currency=AUD" +
                "&sizeSchema=AU&lang=en-AU&q=$q&limit=$limit"

            val req = Request.Builder()
                .url(url)
                .header("X-RapidAPI-Key", rapidApiKey)
                .header("X-RapidAPI-Host", ASOS_HOST)
                .get()
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            Log.d(ASOS_TAG, "search status=${resp.code}")

            if (!resp.isSuccessful) return@withContext Result.failure(Exception("ASOS ${resp.code}"))

            val result = gson.fromJson(body, AsosSearchResponse::class.java)
            val items = result.products?.mapNotNull { p ->
                val imgUrl = p.imageUrl?.let { if (it.startsWith("//")) "https:$it" else it } ?: return@mapNotNull null
                val webUrl = p.url?.let { if (it.startsWith("//")) "https:$it" else it } ?: ""
                EbayItem(
                    itemId    = "asos_${p.id}",
                    title     = p.name ?: "",
                    price     = p.price?.current?.value?.let { "%.2f".format(it) } ?: "",
                    currency  = "AUD",
                    condition = "New",
                    imageUrl  = imgUrl,
                    itemWebUrl = webUrl,
                    source    = "ASOS"
                )
            } ?: emptyList()

            Result.success(items)
        } catch (e: Exception) {
            Log.e(ASOS_TAG, "error: ${e.message}")
            Result.failure(Exception("ASOS: ${e.message}"))
        }
    }
}

private data class AsosSearchResponse(val products: List<AsosProduct>? = null)

private data class AsosProduct(
    val id: Long = 0,
    val name: String? = null,
    val price: AsosPrice? = null,
    val imageUrl: String? = null,
    val url: String? = null
)

private data class AsosPrice(val current: AsosPriceCurrent? = null)

private data class AsosPriceCurrent(
    val value: Double? = null,
    @SerializedName("text") val text: String? = null
)
