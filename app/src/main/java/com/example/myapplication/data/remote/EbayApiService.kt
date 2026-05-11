package com.example.myapplication.data.remote

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

private const val TAG = "EbayAPI"

data class EbayItem(
    val itemId: String = "",
    val title: String = "",
    val price: String = "",
    val currency: String = "AUD",
    val condition: String = "",
    val imageUrl: String = "",
    val itemWebUrl: String = "",
    val source: String = "eBay"
)

private data class EbayTokenResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("expires_in")   val expiresIn: Int = 0
)

private data class EbaySearchResponse(
    val itemSummaries: List<EbayItemSummary>? = null,
    val total: Int = 0
)

private data class EbayItemSummary(
    val itemId: String = "",
    val title: String = "",
    val price: EbayPrice? = null,
    val condition: String = "",
    val image: EbayImage? = null,
    val itemWebUrl: String = ""
)

private data class EbayPrice(val value: String = "", val currency: String = "AUD")
private data class EbayImage(val imageUrl: String = "")

class EbayApiService {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor { Log.d(TAG, it) }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var cachedToken: String = ""
    private var tokenExpiresAt: Long = 0L

    private suspend fun getToken(clientId: String, clientSecret: String): String? =
        withContext(Dispatchers.IO) {
            if (cachedToken.isNotEmpty() && System.currentTimeMillis() < tokenExpiresAt) {
                return@withContext cachedToken
            }
            try {
                val credentials = Base64.encodeToString(
                    "$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP
                )
                val req = Request.Builder()
                    .url("https://api.ebay.com/identity/v1/oauth2/token")
                    .header("Authorization", "Basic $credentials")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .post(FormBody.Builder().add("grant_type", "client_credentials")
                        .add("scope", "https://api.ebay.com/oauth/api_scope").build())
                    .build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: return@withContext null
                Log.d(TAG, "token status=${resp.code}")
                if (!resp.isSuccessful) return@withContext null
                val token = gson.fromJson(body, EbayTokenResponse::class.java)
                cachedToken = token.accessToken ?: return@withContext null
                tokenExpiresAt = System.currentTimeMillis() + (token.expiresIn - 60) * 1000L
                cachedToken
            } catch (e: Exception) {
                Log.e(TAG, "token error: ${e.message}")
                null
            }
        }

    suspend fun search(
        clientId: String,
        clientSecret: String,
        query: String,
        limit: Int = 20
    ): Result<List<EbayItem>> = withContext(Dispatchers.IO) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            return@withContext Result.failure(Exception("Please add your eBay API credentials in settings"))
        }
        try {
            val token = getToken(clientId, clientSecret)
                ?: return@withContext Result.failure(Exception("eBay authorization failed, please check your Client ID / Secret"))

            // Search used/secondhand items in AU marketplace
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.ebay.com/buy/browse/v1/item_summary/search" +
                "?q=$encodedQuery" +
                "&limit=$limit" +
                "&filter=conditionIds:%7B3000%7C2500%7C2000%7C1500%7D" + // used conditions
                "&category_ids=11450" // Fashion category

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("X-EBAY-C-MARKETPLACE-ID", "EBAY_AU")
                .header("X-EBAY-C-ENDUSERCTX", "contextualLocation=country=AU")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            Log.d(TAG, "search status=${resp.code} total chars=${body.length}")

            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("Search failed ${resp.code}: $body"))
            }

            val result = gson.fromJson(body, EbaySearchResponse::class.java)
            val items = result.itemSummaries?.map { s ->
                EbayItem(
                    itemId    = s.itemId,
                    title     = s.title,
                    price     = s.price?.value ?: "",
                    currency  = s.price?.currency ?: "AUD",
                    condition = s.condition,
                    imageUrl  = s.image?.imageUrl ?: "",
                    itemWebUrl = s.itemWebUrl
                )
            } ?: emptyList()

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(Exception("Search error: ${e.message}"))
        }
    }
}
