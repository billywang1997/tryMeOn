package com.trymeon.app.util

import java.net.URLEncoder

/**
 * Auto-affiliate wrapper for retailer URLs that aren't covered by eBay Partner
 * Network or Amazon Associates (which are appended elsewhere with their own codes).
 *
 * When both Skimlinks and Sovrn are configured, traffic is split 50/50 based on
 * a stable URL hash — keeps a given product on the same network across reloads,
 * while making revenue comparison clean over time.
 *
 * Configure once at app startup via [init]; call [wrap] anywhere a SerpAPI /
 * Google Shopping / retailer URL is about to be shown.
 */
object Affiliate {

    @Volatile private var skimlinksId: String = ""
    @Volatile private var sovrnSiteId: String = ""

    fun init(skimlinksId: String, sovrnSiteId: String) {
        this.skimlinksId = skimlinksId
        this.sovrnSiteId = sovrnSiteId
    }

    /**
     * Returns [url] wrapped with the chosen aggregator. eBay and Amazon URLs
     * are passed through untouched — those use their own partner programs.
     */
    fun wrap(url: String, source: String): String {
        if (url.isBlank()) return url
        // eBay / Amazon handled by their own affiliate wrappers
        val s = source.lowercase()
        if ("ebay" in s || "amazon" in s) return url

        val haveSkim = skimlinksId.isNotBlank()
        val haveSovrn = sovrnSiteId.isNotBlank()
        return when {
            haveSkim && haveSovrn -> if (url.hashCode().and(Int.MAX_VALUE) % 2 == 0) skim(url) else sovrn(url)
            haveSkim -> skim(url)
            haveSovrn -> sovrn(url)
            else -> url
        }
    }

    private fun skim(url: String): String {
        val u = URLEncoder.encode(url, "UTF-8")
        return "https://go.skimresources.com/?id=$skimlinksId&xs=1&url=$u"
    }

    private fun sovrn(url: String): String {
        val u = URLEncoder.encode(url, "UTF-8")
        return "https://redirect.viglink.com/?key=$sovrnSiteId&u=$u"
    }
}
