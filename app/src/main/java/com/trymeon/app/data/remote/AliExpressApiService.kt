package com.trymeon.app.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AliExpress affiliate product search.
 *
 * The one source that needs no Chinese business presence: an overseas developer
 * applies at portals.aliexpress.com with a form of ID. Everything the Taobao
 * line requires — an ICP filing, a media registration, an ad slot — has no
 * equivalent here.
 *
 * As with Taobao Union, nothing secret lives in the app. The relay holds the key,
 * the secret and the tracking id, and signs. That last one matters more than it
 * looks: the API answers a request with no tracking id by returning an empty
 * product list and no error, so a client that could set it could also silently
 * search away our commission.
 */
class AliExpressApiService : ProductSearch {

    private val client = RelayHttp.builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override val name = "AliExpress"

    /** Usable only behind the relay: there is nothing here to sign the call with. */
    override val available: Boolean get() = RelayHttp.enabled

    override suspend fun search(keyword: String, limit: Int): Result<List<TaobaoItem>> =
        withContext(Dispatchers.IO) {
            if (!available) {
                return@withContext Result.failure(IllegalStateException("Relay required for the affiliate API"))
            }
            runCatching {
                val url = HttpUrl.Builder()
                    .scheme("https").host("api-sg.aliexpress.com").addPathSegment("sync")
                    .addQueryParameter("method", "aliexpress.affiliate.product.query")
                    .addQueryParameter("keywords", keyword)
                    .addQueryParameter("page_size", limit.coerceIn(1, 50).toString())
                    .addQueryParameter("page_no", "1")
                    // Prices come back already converted and already inclusive of
                    // the GST the platform collects at checkout, which is why the
                    // landed-cost model for this source has so little left to add.
                    .addQueryParameter("target_currency", "AUD")
                    .addQueryParameter("target_language", "EN")
                    .addQueryParameter("ship_to_country", "AU")
                    // Present so it is signed; the relay replaces it with ours.
                    .addQueryParameter("tracking_id", "relay")
                    .build()

                val body = client.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                    val raw = r.body?.string().orEmpty()
                    Log.d(TAG, "status=${r.code} body=${raw.take(300)}")
                    raw
                }
                AliExpressParser.parse(body).getOrThrow()
            }
        }

    private companion object { const val TAG = "AliExpress" }
}

/** Response parsing, separated so it can be tested without credentials. */
object AliExpressParser {

    fun parse(body: String): Result<List<TaobaoItem>> = runCatching {
        val root = JSONObject(body)

        // Errors arrive with HTTP 200 and an error envelope, so a parser that only
        // looked for products would report a rejected key as an empty shelf.
        root.optJSONObject("error_response")?.let { err ->
            val code = err.optString("code").ifEmpty { err.optString("sub_code") }
            val msg = err.optString("msg").ifEmpty { err.optString("sub_msg") }
            error("AliExpress error $code: $msg")
        }

        val result = root
            .optJSONObject("aliexpress_affiliate_product_query_response")
            ?.optJSONObject("resp_result")
            ?: return@runCatching emptyList()

        // A non-success resp_code is also not an empty shelf.
        val respCode = result.optInt("resp_code", 200)
        if (respCode != 200) error("AliExpress responded $respCode: ${result.optString("resp_msg")}")

        val products = result.optJSONObject("result")
            ?.optJSONObject("products")
            ?.optJSONArray("product")
            ?: return@runCatching emptyList()

        buildList {
            for (i in 0 until products.length()) {
                val o = products.optJSONObject(i) ?: continue
                val id = o.optString("product_id").ifEmpty { continue }
                add(
                    TaobaoItem(
                        itemId = id,
                        itemIdStr = id,
                        title = o.optString("product_title"),
                        // The price actually charged, in the currency requested.
                        // original_price is the struck-through figure and quoting
                        // it would overstate every comparison.
                        price = o.optString("target_sale_price")
                            .ifEmpty { o.optString("sale_price") }
                            .ifEmpty { o.optString("target_original_price") },
                        imageUrl = https(o.optString("product_main_image_url")),
                        // The promotion link carries our tracking id; without it a
                        // click reaches the same product and pays nobody.
                        itemUrl = https(
                            o.optString("promotion_link")
                                .ifEmpty { o.optString("product_detail_url") }
                        ),
                        shop = o.optString("shop_name"),
                        currency = "AUD",
                        // ship_to_country=AU, so the quote is delivered and taxed.
                        deliveredPrice = true,
                        marketplace = "AliExpress",
                        sold = o.optString("lastest_volume").toIntOrNull() ?: 0,
                        source = TaobaoSource.AFFILIATE
                    )
                )
            }
        }
    }

    private fun https(url: String) = if (url.startsWith("//")) "https:$url" else url
}
