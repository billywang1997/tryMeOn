package com.trymeon.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AliExpress answers failures with HTTP 200 and an error envelope, and answers a
 * request missing its tracking id with an empty product list and no error at
 * all. Both look like "nothing in stock" to a parser that only counts products —
 * and the second one would quietly cost every commission on the platform.
 */
class AliExpressParserTest {

    private val ok = """
    {"aliexpress_affiliate_product_query_response":{"resp_result":{
      "resp_code":200,"resp_msg":"success","result":{"products":{"product":[
        {"product_id":"1005006123456789",
         "product_title":"Women Linen Blazer Cropped Casual Suit Jacket",
         "target_sale_price":"48.64","target_original_price":"96.20",
         "sale_price":"31.50",
         "product_main_image_url":"//ae01.alicdn.com/kf/S123.jpg",
         "promotion_link":"https://s.click.aliexpress.com/e/_abc123",
         "product_detail_url":"https://www.aliexpress.com/item/1005006123456789.html",
         "shop_name":"Fashion Store","lastest_volume":"842"}
      ]}}}}}
    """.trimIndent()

    @Test
    fun `parses an affiliate listing`() {
        val item = AliExpressParser.parse(ok).getOrThrow().single()
        assertEquals("1005006123456789", item.itemId)
        assertTrue(item.title.startsWith("Women Linen Blazer"))
        assertEquals("Fashion Store", item.shop)
        assertEquals(842, item.sold)
        assertEquals(TaobaoSource.AFFILIATE, item.source)
    }

    @Test
    fun `quotes the price actually charged in the requested currency`() {
        // target_original_price is the struck-through figure; quoting it would
        // overstate every local-market comparison the app makes.
        assertEquals("48.64", AliExpressParser.parse(ok).getOrThrow().single().price)
    }

    @Test
    fun `prefers the promotion link, which is the one that pays`() {
        val item = AliExpressParser.parse(ok).getOrThrow().single()
        assertEquals("https://s.click.aliexpress.com/e/_abc123", item.itemUrl)
        assertEquals("https://ae01.alicdn.com/kf/S123.jpg", item.imageUrl)
    }

    @Test
    fun `falls back to the plain product page when there is no promotion link`() {
        val noLink = ok.replace(""""promotion_link":"https://s.click.aliexpress.com/e/_abc123",""", "")
        assertEquals(
            "https://www.aliexpress.com/item/1005006123456789.html",
            AliExpressParser.parse(noLink).getOrThrow().single().itemUrl
        )
    }

    @Test
    fun `an error envelope fails loudly instead of looking empty`() {
        val body = """
        {"error_response":{"type":"ISV","code":"InvalidAppKey",
         "msg":"The specified App Key is invalid","request_id":"2138"}}
        """.trimIndent()
        val result = AliExpressParser.parse(body)
        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()!!.message.orEmpty()
        assertTrue(message.contains("InvalidAppKey"))
    }

    @Test
    fun `a non-success response code is not an empty shelf either`() {
        val body = ok.replace(""""resp_code":200""", """"resp_code":403""")
        assertTrue(AliExpressParser.parse(body).isFailure)
    }

    @Test
    fun `a genuinely empty result is empty, not an error`() {
        val body = """
        {"aliexpress_affiliate_product_query_response":{"resp_result":{
          "resp_code":200,"result":{"products":{"product":[]}}}}}
        """.trimIndent()
        assertEquals(emptyList<TaobaoItem>(), AliExpressParser.parse(body).getOrThrow())
    }

    @Test
    fun `malformed payloads fail rather than throw into the caller`() {
        assertTrue(AliExpressParser.parse("<html>502</html>").isFailure)
        assertTrue(AliExpressParser.parse("").isFailure)
    }

    @Test
    fun `entries without an id are skipped`() {
        val body = ok.replace(""""product_id":"1005006123456789",""", "")
        assertEquals(emptyList<TaobaoItem>(), AliExpressParser.parse(body).getOrThrow())
    }

    @Test
    fun `a listing is marked as delivered, priced locally, and named`() {
        val item = AliExpressParser.parse(ok).getOrThrow().single()

        // Each of these decides money or wording downstream, and each is one
        // line in the parser that nothing else would notice the loss of.
        //
        // deliveredPrice routes the listing to the two-line cost model; drop it
        // and an already-taxed price gets our freight and GST added on top.
        assertTrue("a ship-to quote is delivered", item.deliveredPrice)
        // currency stops the quoter converting a price that is already local,
        // which at the CNY rate would cut it to a fifth.
        assertEquals("AUD", item.currency)
        // marketplace is what the card, the try-on strip and the saved wishlist
        // record call it, and decides whether the link may be rewritten.
        assertEquals("AliExpress", item.marketplace)
    }
}
