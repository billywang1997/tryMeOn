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

/** The search quota is spent. Distinct from finding nothing. */
class SearchQuotaExceeded : Exception("Search quota exhausted for now")

/** The search backend cannot be reached or used at all. */
class SearchUnavailable(message: String) : Exception(message)
private const val HOST = "taobao-datahub.p.rapidapi.com"
private const val BASE = "https://$HOST"

data class TaobaoItem(
    val itemId: String = "",
    val itemIdStr: String = "",
    val title: String = "",
    /** Listed price in CNY, as text — sellers publish ranges as well as figures. */
    val price: String = "",
    val imageUrl: String = "",
    val itemUrl: String = "",
    /** Seller/shop name. Empty from the scraper, populated by the affiliate API. */
    val shop: String = "",
    /** Face value of an available coupon, in CNY. Zero when there is none. */
    val couponCny: Double = 0.0,
    /** Units sold — the closest thing Taobao gives to a trust signal. */
    val sold: Int = 0,
    /** Where this record came from, so the UI can be honest about data quality. */
    val source: TaobaoSource = TaobaoSource.SCRAPER
)

/**
 * Taobao data has two very different provenances and the difference is worth
 * surfacing: the affiliate API is official, complete and pays commission, while
 * the scraper is a stopgap that can go stale or empty without warning.
 */
enum class TaobaoSource { AFFILIATE, SCRAPER }

class TaobaoApiService {

    private val client = RelayHttp.builder()
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
                // A quota or auth failure is not "nothing matched". Returning an
                // empty list for it tells the user their search was fruitless
                // when the truth is that we stopped being able to search.
                when (resp.code) {
                    429 -> throw SearchQuotaExceeded()
                    401, 403 -> throw SearchUnavailable("Search is not configured correctly")
                    in 500..599 -> throw SearchUnavailable("Taobao search is down (${resp.code})")
                }
                raw
            }
            parseSearchResponse(body)
        }
    }

    internal fun parseSearchResponse(body: String): List<TaobaoItem> =
        TaobaoScraperParser.parse(body)
}

/**
 * Response shape for the unofficial item_search endpoint (apiVersion 4.0.5).
 *
 * Split out and tested against a captured payload because the shape is nothing
 * like the obvious one: there is no top-level `price` at all — it lives under
 * `sku.def`, the image field is `image` rather than `picUrl`, and every URL
 * comes back protocol-relative. Reading it wrong does not fail loudly; it
 * silently drops every listing for having no price.
 */
object TaobaoScraperParser {

    fun parse(body: String): List<TaobaoItem> = runCatching {
        val root = JSONObject(body)
        val result = root.optJSONObject("result") ?: root
        val status = result.optJSONObject("status")?.optInt("code", 200) ?: 200
        if (status != 0 && status != 200) return emptyList()

        val arr = result.optJSONArray("resultList") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val wrapper = arr.optJSONObject(i) ?: continue
                val o = wrapper.optJSONObject("item") ?: wrapper
                val title = o.optString("title")
                if (title.isEmpty()) continue

                val def = o.optJSONObject("sku")?.optJSONObject("def")
                // promotionPrice is what the buyer actually pays; `price` is the
                // pre-discount figure and quoting it overstates the landed cost.
                val price = def?.optString("promotionPrice")?.takeIf { it.isNotBlank() }
                    ?: def?.optString("price").orEmpty()

                val id = o.optString("itemId").ifEmpty { o.optString("item_id") }
                add(
                    TaobaoItem(
                        itemId = id,
                        itemIdStr = o.optString("itemIdStr"),
                        title = title,
                        price = price,
                        imageUrl = https(o.optString("image").ifEmpty { o.optString("picUrl") }),
                        itemUrl = https(
                            o.optString("itemUrl").ifEmpty {
                                if (id.isNotEmpty()) "//item.taobao.com/item.htm?id=$id" else ""
                            }
                        ),
                        sold = o.optString("sales").toIntOrNull() ?: 0,
                        source = TaobaoSource.SCRAPER
                    )
                )
            }
        }
    }.getOrElse {
        Log.w(TAG, "parse failed: ${it.message}")
        emptyList()
    }

    private fun https(url: String) = if (url.startsWith("//")) "https:$url" else url
}
