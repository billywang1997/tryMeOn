package com.trymeon.app.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Taobao Union (Alimama) — the official affiliate API.
 *
 * This is the only supported way to read Taobao product data, and unlike the
 * scraper it also pays commission, which makes the forwarder-affiliate detour
 * unnecessary for anything bought through it.
 *
 * Nothing secret lives here. Requests are signed by the relay, which holds the
 * app secret and the ad slot id; the app only names the search. That is
 * deliberate: the TOP signature is computed with the secret on both ends of the
 * payload, so a client that could sign could also be unpacked for the secret.
 *
 * Activation is an account matter, not a code one: register at pub.alimama.com,
 * complete media registration for the app, then put TAOBAO_APP_KEY /
 * TAOBAO_APP_SECRET / TAOBAO_ADZONE_ID into Secret Manager.
 */
class TaobaoUnionApiService : ProductSearch {

    private val client = RelayHttp.builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override val name = "Taobao Union"

    /** Usable only behind the relay: without it there is nothing to sign the call. */
    override val available: Boolean get() = RelayHttp.enabled

    override suspend fun search(keyword: String, limit: Int): Result<List<TaobaoItem>> =
        withContext(Dispatchers.IO) {
            if (!available) {
                return@withContext Result.failure(IllegalStateException("Relay required for the affiliate API"))
            }
            runCatching {
                val url = okhttp3.HttpUrl.Builder()
                    .scheme("https").host("eco.taobao.com")
                    .addPathSegment("router").addPathSegment("rest")
                    .addQueryParameter("method", "taobao.tbk.dg.material.optional")
                    .addQueryParameter("q", keyword)
                    .addQueryParameter("page_size", limit.coerceIn(1, 100).toString())
                    .addQueryParameter("page_no", "1")
                    // Replaced with our real slot by the relay; present so it is signed.
                    .addQueryParameter("adzone_id", "0")
                    .build()

                val body = client.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                    val raw = r.body?.string().orEmpty()
                    Log.d(TAG, "union status=${r.code} body=${raw.take(300)}")
                    raw
                }
                TaobaoUnionParser.parse(body).getOrThrow()
            }
        }

    private companion object { const val TAG = "TaobaoUnion" }
}

/** Response parsing, kept separate so it can be tested without a network or credentials. */
object TaobaoUnionParser {

    fun parse(body: String): Result<List<TaobaoItem>> = runCatching {
        val root = JSONObject(body)

        // TOP reports failures with HTTP 200 and an error envelope, so a naive
        // "did it parse" check would read an auth failure as an empty result.
        root.optJSONObject("error_response")?.let { err ->
            val msg = err.optString("sub_msg").ifEmpty { err.optString("msg") }
            error("Taobao Union error ${err.optInt("code")}: $msg")
        }

        val list = root.optJSONObject("tbk_dg_material_optional_response")
            ?.optJSONObject("result_list")
            ?.optJSONArray("map_data")
            ?: return@runCatching emptyList()

        buildList {
            for (i in 0 until list.length()) {
                val o = list.optJSONObject(i) ?: continue
                val id = o.optLong("item_id").takeIf { it > 0 }?.toString()
                    ?: o.optString("item_id").ifEmpty { continue }
                val coupon = o.optString("coupon_amount").toDoubleOrNull() ?: 0.0
                add(
                    TaobaoItem(
                        itemId = id,
                        itemIdStr = id,
                        title = o.optString("title"),
                        // zk_final_price is the price actually charged; reserve_price
                        // is the inflated "was" figure and must not be quoted.
                        price = o.optString("zk_final_price").ifEmpty { o.optString("reserve_price") },
                        imageUrl = https(o.optString("pict_url")),
                        // Prefer the coupon link: it is the one that both discounts
                        // the item and carries our commission.
                        itemUrl = o.optString("coupon_share_url").ifEmpty {
                            o.optString("url").ifEmpty { "https://item.taobao.com/item.htm?id=$id" }
                        }.let(::https),
                        shop = o.optString("shop_title"),
                        couponCny = coupon,
                        sold = o.optInt("volume"),
                        source = TaobaoSource.AFFILIATE
                    )
                )
            }
        }
    }

    private fun https(url: String) = if (url.startsWith("//")) "https:$url" else url
}
