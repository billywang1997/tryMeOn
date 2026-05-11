package com.example.myapplication.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "TaobaoAPI"
private const val HOST = "taobao-datahub.p.rapidapi.com"
private const val BASE = "https://$HOST"

data class TaobaoItem(
    val itemId: String = "",
    val itemIdStr: String = "",
    val title: String = "",
    val price: String = "",
    val imageUrl: String = "",
    val itemUrl: String = ""
)

class TaobaoApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Search Taobao products by keyword. Returns up to [pageSize] items. */
    suspend fun search(
        apiKey: String,
        keyword: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<TaobaoItem>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("RapidAPI key not configured"))
        runCatching {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            // Correct endpoint is /item_search; param is 'q' not 'keyword' (confirmed via API error)
            val url = "$BASE/item_search?q=$encoded&page=$page&pageSize=$pageSize"
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

    private fun parseSearchResponse(body: String): List<TaobaoItem> {
        // Response envelope: { "result": { "status": {...}, "resultList": [ { "item": {...} }, ... ] } }
        val root = JSONObject(body)
        val resultNode = root.optJSONObject("result") ?: root
        val statusCode = resultNode.optJSONObject("status")?.optInt("code", 0) ?: 0
        if (statusCode != 0 && statusCode != 200) return emptyList()
        val arr = resultNode.optJSONArray("resultList") ?: return emptyList()
        val result = mutableListOf<TaobaoItem>()
        for (i in 0 until arr.length()) {
            val wrapper = arr.optJSONObject(i) ?: continue
            val obj = wrapper.optJSONObject("item") ?: wrapper
            // Log first item's full keys so we can identify the correct image field
            if (i == 0) Log.d(TAG, "item[0] keys=${obj.keys().asSequence().toList()} json=${obj.toString().take(600)}")
            val imageUrl = ensureHttps(
                obj.optString("picUrl").ifEmpty {
                obj.optString("pic_url").ifEmpty {
                obj.optString("mainPicUrl").ifEmpty {
                obj.optString("pic").ifEmpty {
                obj.optString("image").ifEmpty {
                obj.optString("img").ifEmpty {
                // sometimes nested: "pic": {"url": "..."}
                obj.optJSONObject("pic")?.optString("url") ?: ""
                }}}}}}
            )
            val item = TaobaoItem(
                itemId    = obj.optString("itemId").ifEmpty { obj.optString("item_id") },
                itemIdStr = obj.optString("itemIdStr").ifEmpty { obj.optString("item_id_str") },
                title     = obj.optString("title").ifEmpty { obj.optString("name") },
                price     = obj.optString("price").ifEmpty { obj.optString("priceWap") },
                imageUrl  = imageUrl,
                itemUrl   = obj.optString("detail_url").ifEmpty { obj.optString("item_url") }
            )
            if (item.title.isNotEmpty()) result.add(item)
        }
        return result
    }

    private fun ensureHttps(url: String): String =
        if (url.startsWith("//")) "https:$url" else url
}
