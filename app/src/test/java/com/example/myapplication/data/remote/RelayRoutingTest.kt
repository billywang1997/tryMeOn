package com.example.myapplication.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * These rules decide whether a request reaches the relay at all. A silent
 * mistake here is expensive in both directions: an unmapped host goes direct
 * (and in a release build has no key, so it just fails), while a leaked query
 * parameter puts a credential back on the wire.
 */
class RelayRoutingTest {

    private val base = "https://us-central1-mycloset-ce07e.cloudfunctions.net/relay".toHttpUrl()

    @Test
    fun `maps every upstream the app calls`() {
        val cases = mapOf(
            "https://api.openai.com/v1/chat/completions" to "openai",
            "https://api.openai.com/v1/images/edits" to "openai",
            "https://api.fashn.ai/v1/run" to "fashn",
            "https://api.fashn.ai/v1/status/abc123" to "fashn",
            "https://serpapi.com/search.json?q=dress" to "serpapi",
            "https://api.scraperapi.com/structured/amazon/search?query=coat" to "scraperapi",
            "https://api.unsplash.com/search/photos?query=coat" to "unsplash",
            "https://api.ebay.com/buy/browse/v1/item_summary/search?q=bag" to "ebay",
            "https://taobao-datahub.p.rapidapi.com/item_search?q=bag" to "rapidapi",
            "https://shein-scraper-api.p.rapidapi.com/shein/search/products" to "rapidapi",
            "https://vinted3.p.rapidapi.com/getSearch" to "rapidapi",
            "https://aliexpress-datahub.p.rapidapi.com/item_search" to "rapidapi",
            "https://asos2.p.rapidapi.com/products/v2/list" to "rapidapi",
            "https://theiconic.p.rapidapi.com/products/search" to "rapidapi",
            "https://www.googleapis.com/customsearch/v1?q=coat" to "googlesearch",
        )
        cases.forEach { (url, expected) ->
            assertEquals(url, expected, RelayRouting.targetFor(url.toHttpUrl()))
        }
    }

    @Test
    fun `leaves unrelated hosts alone`() {
        listOf(
            "https://wttr.in/Sydney?format=j1",
            "https://firebasestorage.googleapis.com/v0/b/x/o/y.jpg",
            "https://www.googleapis.com/oauth2/v1/userinfo",
            "https://webservices.amazon.com.au/paapi5/searchitems",
            "https://images.unsplash.com/photo-123",
        ).forEach { assertNull(it, RelayRouting.targetFor(it.toHttpUrl())) }
    }

    @Test
    fun `a lookalike host is not treated as rapidapi`() {
        // ".p.rapidapi.com" must be a suffix, not a substring an attacker controls.
        assertNull(RelayRouting.targetFor("https://p.rapidapi.com.evil.test/x".toHttpUrl()))
    }

    @Test
    fun `rewrites path under the relay base`() {
        val out = RelayRouting.relayUrl(base, "https://api.openai.com/v1/chat/completions".toHttpUrl())
        assertEquals(
            "https://us-central1-mycloset-ce07e.cloudfunctions.net/relay/v1/chat/completions",
            out.toString()
        )
    }

    @Test
    fun `strips upstream keys but keeps real parameters`() {
        val out = RelayRouting.relayUrl(
            base,
            ("https://serpapi.com/search.json?engine=google_shopping&q=black+dress" +
                "&gl=au&api_key=SECRET").toHttpUrl()
        )
        assertNull("api_key must not leave the device", out.queryParameter("api_key"))
        assertEquals("google_shopping", out.queryParameter("engine"))
        assertEquals("black dress", out.queryParameter("q"))
        assertEquals("au", out.queryParameter("gl"))
    }

    @Test
    fun `strips google search key and cx`() {
        val out = RelayRouting.relayUrl(
            base,
            "https://www.googleapis.com/customsearch/v1?q=coat&key=SECRET&cx=CXID&num=5".toHttpUrl()
        )
        assertNull(out.queryParameter("key"))
        assertNull(out.queryParameter("cx"))
        assertEquals("coat", out.queryParameter("q"))
        assertEquals("5", out.queryParameter("num"))
    }

    @Test
    fun `keeps every value of a repeated parameter`() {
        val out = RelayRouting.relayUrl(
            base,
            "https://api.ebay.com/buy/browse/v1/item_summary/search?filter=a&filter=b".toHttpUrl()
        )
        assertEquals(listOf("a", "b"), out.queryParameterValues("filter"))
    }

    @Test
    fun `preserves encoded characters in path and query`() {
        val out = RelayRouting.relayUrl(
            base,
            "https://api.ebay.com/buy/browse/v1/item_summary/search?filter=conditionIds%3A%7B3000%7C2500%7D".toHttpUrl()
        )
        assertEquals("conditionIds:{3000|2500}", out.queryParameter("filter"))
        assertEquals("/relay/buy/browse/v1/item_summary/search", out.encodedPath)
    }
}
