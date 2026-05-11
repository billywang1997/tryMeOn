package com.example.myapplication.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "SheinAPI"
// asyncsolutions Shein Scraper — endpoint path must be verified from RapidAPI dashboard
private const val HOST = "shein-scraper-api.p.rapidapi.com"
private const val BASE = "https://$HOST"

data class SheinItem(
    val goodsId: String = "",
    val title: String = "",
    val price: String = "",
    val imageUrl: String = "",
    val itemUrl: String = ""
)

class SheinApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        apiKey: String,
        keyword: String,
        page: Int = 1,
        pageSize: Int = 19
    ): Result<List<SheinItem>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("RapidAPI key not configured"))
        runCatching {
            val size = pageSize.coerceIn(1, 50)
            val url = "$BASE/shein/search/products".toHttpUrl().newBuilder()
                .addQueryParameter("keywords", keyword)
                .addQueryParameter("sort", "recommend")
                .addQueryParameter("size", size.toString())
                .addQueryParameter("page", page.toString())
                .addQueryParameter("country", "us")
                .addQueryParameter("language", "en")
                .addQueryParameter("currency", "usd")
                .build()
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

    private fun parseSearchResponse(body: String): List<SheinItem> {
        val root = JSONObject(body)
        // apidojo shape: { "info": { "result": { "goods": [...] } } }
        val arr: JSONArray = root.optJSONObject("info")
            ?.optJSONObject("result")
            ?.optJSONArray("goods")
            ?: root.optJSONObject("data")?.optJSONArray("products")
            ?: root.optJSONArray("items")
            ?: return emptyList()
        val result = mutableListOf<SheinItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val item = buildItem(obj)
            if (item.title.isNotEmpty()) result.add(item)
        }
        return result
    }

    private fun buildItem(obj: JSONObject): SheinItem {
        val goodsId = obj.optString("goods_id").ifEmpty { obj.optString("goodsSn") }
        val title = obj.optString("goods_name").ifEmpty { obj.optString("name").ifEmpty { obj.optString("title") } }
        val priceNode = obj.optJSONObject("salePrice") ?: obj.optJSONObject("retailPrice")
        val price = priceNode?.optString("amount")?.ifEmpty { priceNode.optString("amountWithSymbol") }
            ?: obj.optString("price").ifEmpty { obj.optString("priceStr") }
        val imageUrl = obj.optString("goods_img").ifEmpty { obj.optString("mainImage").ifEmpty { obj.optString("image") } }
        val itemUrl = if (goodsId.isNotEmpty())
            "https://www.shein.com/product-p-$goodsId.html"
        else obj.optString("goods_url_name").ifEmpty { "" }
        return SheinItem(goodsId = goodsId, title = title, price = price, imageUrl = ensureHttps(imageUrl), itemUrl = itemUrl)
    }

    private fun ensureHttps(url: String): String =
        if (url.startsWith("//")) "https:$url" else url
}
