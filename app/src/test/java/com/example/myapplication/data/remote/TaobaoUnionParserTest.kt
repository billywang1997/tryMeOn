package com.example.myapplication.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Taobao's Open Platform answers failures with HTTP 200 and an error envelope.
 * The failure that matters most here is reading "your app key is invalid" as
 * "no results" — the feature would look merely unlucky instead of unconfigured.
 */
class TaobaoUnionParserTest {

    private val ok = """
    {"tbk_dg_material_optional_response":{"result_list":{"map_data":[
      {"item_id":654321,"title":"夏季薄款亚麻小西装外套女",
       "zk_final_price":"128.00","reserve_price":"299.00",
       "pict_url":"//img.alicdn.com/a.jpg",
       "coupon_share_url":"//uland.taobao.com/coupon?x=1",
       "coupon_amount":"20","shop_title":"某某旗舰店","volume":3421}
    ]},"total_results":120}}
    """.trimIndent()

    @Test
    fun `parses an affiliate listing`() {
        val item = TaobaoUnionParser.parse(ok).getOrThrow().single()
        assertEquals("654321", item.itemId)
        assertEquals("夏季薄款亚麻小西装外套女", item.title)
        assertEquals("某某旗舰店", item.shop)
        assertEquals(20.0, item.couponCny, 1e-9)
        assertEquals(3421, item.sold)
        assertEquals(TaobaoSource.AFFILIATE, item.source)
    }

    @Test
    fun `quotes the price actually charged, not the inflated was-price`() {
        // reserve_price is marketing. Quoting it would overstate every landed cost.
        assertEquals("128.00", TaobaoUnionParser.parse(ok).getOrThrow().single().price)
    }

    @Test
    fun `prefers the coupon link because it discounts and pays commission`() {
        val item = TaobaoUnionParser.parse(ok).getOrThrow().single()
        assertEquals("https://uland.taobao.com/coupon?x=1", item.itemUrl)
        assertEquals("https://img.alicdn.com/a.jpg", item.imageUrl)
    }

    @Test
    fun `falls back to the item page when there is no coupon link`() {
        val body = """
        {"tbk_dg_material_optional_response":{"result_list":{"map_data":[
          {"item_id":11,"title":"T","zk_final_price":"9.90"}]}}}
        """.trimIndent()
        assertEquals(
            "https://item.taobao.com/item.htm?id=11",
            TaobaoUnionParser.parse(body).getOrThrow().single().itemUrl
        )
    }

    @Test
    fun `an error envelope fails loudly instead of looking empty`() {
        val body = """
        {"error_response":{"code":25,"msg":"Invalid signature","sub_msg":"appkey 或 secret 不正确"}}
        """.trimIndent()
        val result = TaobaoUnionParser.parse(body)
        assertTrue("must not be reported as zero results", result.isFailure)
        val message = result.exceptionOrNull()!!.message.orEmpty()
        assertTrue(message.contains("25"))
        assertTrue(message.contains("appkey"))
    }

    @Test
    fun `a genuinely empty result is empty, not an error`() {
        val body = """{"tbk_dg_material_optional_response":{"result_list":{},"total_results":0}}"""
        assertEquals(emptyList<TaobaoItem>(), TaobaoUnionParser.parse(body).getOrThrow())
    }

    @Test
    fun `malformed payloads fail rather than throw into the caller`() {
        assertTrue(TaobaoUnionParser.parse("<html>502 Bad Gateway</html>").isFailure)
        assertTrue(TaobaoUnionParser.parse("").isFailure)
    }

    @Test
    fun `entries without an id are skipped`() {
        val body = """
        {"tbk_dg_material_optional_response":{"result_list":{"map_data":[
          {"title":"no id","zk_final_price":"5"},
          {"item_id":7,"title":"has id","zk_final_price":"5"}]}}}
        """.trimIndent()
        val items = TaobaoUnionParser.parse(body).getOrThrow()
        assertEquals(1, items.size)
        assertEquals("7", items.single().itemId)
    }
}
