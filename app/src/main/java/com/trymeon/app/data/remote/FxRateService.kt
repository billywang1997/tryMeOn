package com.trymeon.app.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Live CNY→AUD, because a stale rate skews every landed-cost quote at once.
 *
 * Two independent sources, both keyless: the ECB reference rate as primary
 * because it is authoritative and stable, and a second feed as backup so one
 * provider having a bad day does not silently push every quote back onto a
 * hardcoded constant. Neither host is in the relay allowlist and neither needs
 * to be — there is no credential here to protect.
 */
data class FxRate(
    /** AUD per 1 CNY. */
    val rate: Double,
    val fetchedAtMillis: Long,
    val source: String,
    /** True when this is the compiled-in constant, not a fetched rate. */
    val isFallback: Boolean = false
)

object FxRateService {

    private const val TAG = "FxRate"

    private const val ECB = "https://api.frankfurter.dev/v1/latest?base=CNY&symbols=AUD"
    private const val BACKUP = "https://open.er-api.com/v6/latest/CNY"

    private val client = RelayHttp.builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch a fresh rate, or null when both sources fail. Callers are expected
     * to fall back to their last known good value rather than to zero.
     */
    suspend fun fetch(): FxRate? = withContext(Dispatchers.IO) {
        fetchFrom(ECB, "ECB", FxParsing::parseFrankfurter)
            ?: fetchFrom(BACKUP, "exchangerate-api", FxParsing::parseErApi)
    }

    private inline fun fetchFrom(url: String, source: String, parse: (String) -> Double?): FxRate? =
        runCatching {
            val body = client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string() ?: return null
            }
            val rate = parse(body) ?: return null
            FxRate(rate, System.currentTimeMillis(), source)
        }.onFailure { Log.w(TAG, "$source failed: ${it.message}") }.getOrNull()
}

/** Response shapes, split out so they can be tested without a network. */
object FxParsing {

    /** `{"amount":1.0,"base":"CNY","date":"2026-08-27","rates":{"AUD":0.20696}}` */
    fun parseFrankfurter(body: String): Double? = runCatching {
        val root = JSONObject(body)
        // A non-unit amount would silently scale the rate; refuse rather than guess.
        if (root.optDouble("amount", 1.0) != 1.0) return null
        root.getJSONObject("rates").optDouble("AUD").takeIf { it.isSane() }
    }.getOrNull()

    /** `{"result":"success","base_code":"CNY","rates":{"AUD":0.206816,...}}` */
    fun parseErApi(body: String): Double? = runCatching {
        val root = JSONObject(body)
        if (root.optString("result") != "success") return null
        if (root.optString("base_code") != "CNY") return null
        root.getJSONObject("rates").optDouble("AUD").takeIf { it.isSane() }
    }.getOrNull()

    /**
     * A plausible AUD-per-CNY. Guards against a provider returning 0, NaN, or an
     * inverted quote — 4.83 instead of 0.207 would understate every landed price
     * by a factor of twenty and still look like a number.
     */
    private fun Double.isSane(): Boolean = isFinite() && this in 0.05..1.0
}
