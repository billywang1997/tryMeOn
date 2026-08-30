package com.trymeon.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Built from a payload captured off the live endpoint after every listing was
 * silently dropped for "no readable price". The shape is not the obvious one:
 * price hides under `sku.def`, the image field is `image`, and URLs arrive
 * protocol-relative. Each of those was a real bug.
 */
class TaobaoScraperParserTest {

    private val real = """
    {"result":{"status":{"code":200,"data":"success"},"resultList":[
      {"item":{
        "itemId":"1047934811236",
        "itemIdStr":"YzlGSDZEbm5",
        "title":"老钱风亚麻短款西装外套女2026夏季新款松弛感立领开衫气质穿搭",
        "sales":"132",
        "itemUrl":"//item.taobao.com/item.htm?id=1047934811236",
        "image":"//img.alicdn.com/bao/uploaded/i2/O1CN01An6sEx.jpg",
        "sku":{"def":{"price":"239.90","promotionPrice":"210.90"}}
      }}
    ]}}
    """.trimIndent()

    @Test
    fun `reads the price out of sku def`() {
        val item = TaobaoScraperParser.parse(real).single()
        // The whole feature failed on this: there is no top-level price field.
        assertEquals("210.90", item.price)
    }

    @Test
    fun `quotes the promotion price, not the pre-discount one`() {
        // Quoting 239.90 would overstate every landed cost by 14%.
        assertEquals("210.90", TaobaoScraperParser.parse(real).single().price)
    }

    @Test
    fun `falls back to list price when nothing is discounted`() {
        val noPromo = real.replace(""","promotionPrice":"210.90"""", "")
        assertEquals("239.90", TaobaoScraperParser.parse(noPromo).single().price)
    }

    @Test
    fun `upgrades protocol-relative URLs`() {
        val item = TaobaoScraperParser.parse(real).single()
        assertTrue(item.imageUrl.startsWith("https://"))
        assertTrue(item.itemUrl.startsWith("https://item.taobao.com/"))
    }

    @Test
    fun `carries id, title and sales through`() {
        val item = TaobaoScraperParser.parse(real).single()
        assertEquals("1047934811236", item.itemId)
        assertTrue(item.title.startsWith("老钱风亚麻短款西装外套"))
        assertEquals(132, item.sold)
        assertEquals(TaobaoSource.SCRAPER, item.source)
    }

    @Test
    fun `builds an item URL when the response omits one`() {
        val noUrl = real.replace(""""itemUrl":"//item.taobao.com/item.htm?id=1047934811236",""", "")
        assertEquals(
            "https://item.taobao.com/item.htm?id=1047934811236",
            TaobaoScraperParser.parse(noUrl).single().itemUrl
        )
    }

    @Test
    fun `an entry with no price still parses, and is dropped later by the repository`() {
        val noSku = real.replace(""""sku":{"def":{"price":"239.90","promotionPrice":"210.90"}}""", """"sku":{}""")
        val item = TaobaoScraperParser.parse(noSku).single()
        assertEquals("", item.price)
        assertEquals(null, com.trymeon.app.data.sourcing.SourcingRepository.parsePriceCny(item.price))
    }

    @Test
    fun `untitled entries and junk payloads yield nothing rather than throwing`() {
        val untitled = real.replace(""""title":"老钱风亚麻短款西装外套女2026夏季新款松弛感立领开衫气质穿搭",""", "")
        assertEquals(emptyList<TaobaoItem>(), TaobaoScraperParser.parse(untitled))
        assertEquals(emptyList<TaobaoItem>(), TaobaoScraperParser.parse("<html>502</html>"))
        assertEquals(emptyList<TaobaoItem>(), TaobaoScraperParser.parse(""))
    }
}
