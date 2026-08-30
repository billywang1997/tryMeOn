package com.trymeon.app.data.remote

import okhttp3.HttpUrl

/**
 * The pure part of relay routing: which upstream a URL belongs to, and what the
 * relayed URL should look like. Kept free of BuildConfig and Firebase so it can
 * be exercised by plain JVM tests — a mistake here either breaks every network
 * call or, worse, quietly forwards a key we meant to strip.
 */
internal object RelayRouting {

    /** Query parameters that carry an upstream key; the relay re-adds the real value. */
    val SECRET_PARAMS = setOf("api_key", "client_id", "key", "cx")

    /** Upstream host -> the relay target name the function dispatches on, or null to pass through. */
    fun targetFor(url: HttpUrl): String? = when {
        url.host == "api.openai.com" -> "openai"
        url.host == "api.fashn.ai" -> "fashn"
        url.host == "serpapi.com" -> "serpapi"
        url.host == "api.scraperapi.com" -> "scraperapi"
        url.host == "api.unsplash.com" -> "unsplash"
        url.host == "api.ebay.com" -> "ebay"
        url.host.endsWith(".p.rapidapi.com") -> "rapidapi"
        url.host == "www.googleapis.com" && url.encodedPath.startsWith("/customsearch") -> "googlesearch"
        else -> null
    }

    /** Rebuild [original] as a path under [base], dropping any client-side key. */
    fun relayUrl(base: HttpUrl, original: HttpUrl): HttpUrl = base.newBuilder().apply {
        original.pathSegments.forEach { addPathSegment(it) }
        // Indexed rather than by name: a URL may repeat a parameter, and
        // queryParameter(name) would silently keep only the first value.
        for (i in 0 until original.querySize) {
            val name = original.queryParameterName(i)
            if (name in SECRET_PARAMS) continue
            addQueryParameter(name, original.queryParameterValue(i))
        }
    }.build()
}
