package com.trymeon.app.data.sourcing

import com.trymeon.app.data.remote.TaobaoItem
import com.trymeon.app.data.remote.TaobaoSource
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.util.Affiliate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Several marketplaces now feed one listing type. Getting the label wrong is
 * not cosmetic: it is shown on the card, saved into the wishlist record, and
 * used to decide whether a link may be rewritten.
 */
class MarketplaceLabelTest {

    private fun row(item: TaobaoItem) = SourcedItem(
        listing = item,
        priceCny = 48.64,
        quotes = emptyList(),
        orderUrl = null
    )

    @Test
    fun `an aliexpress listing is not shown as taobao`() {
        val row = row(
            TaobaoItem(
                itemId = "1", title = "Linen Blazer", marketplace = "AliExpress",
                source = TaobaoSource.AFFILIATE
            )
        )
        // asProductRow needs a quote for its price, so only the labels are read.
        assertEquals("AliExpress", row.listing.marketplace)
    }

    @Test
    fun `a scraped listing still says so, whichever marketplace it is`() {
        val scraped = TaobaoItem(itemId = "1", source = TaobaoSource.SCRAPER)
        assertEquals("Taobao", scraped.marketplace)
    }

    @Test
    fun `an aliexpress promotion link is never rewritten`() {
        // It already carries our tracking id. Sending it through a monetiser
        // strips the attribution the affiliate application exists for.
        Affiliate.init(skimlinksId = "12345", sovrnSiteId = "67890")
        try {
            val promo = "https://s.click.aliexpress.com/e/_ABCDEF"
            assertEquals(promo, Affiliate.wrap(promo, "AliExpress"))
            // Even if a row reaches here mislabelled, the host still protects it.
            assertEquals(promo, Affiliate.wrap(promo, "Taobao"))

            // A plain Taobao link is still monetised, so the exemption is narrow.
            val taobao = "https://item.taobao.com/item.htm?id=1"
            assertTrue(Affiliate.wrap(taobao, "Taobao") != taobao)
        } finally {
            Affiliate.init(skimlinksId = "", sovrnSiteId = "")
        }
    }

    @Test
    fun `a pending try-on carries the marketplace it came from`() {
        PendingTryOn.offer(
            TaobaoItem(itemId = "1", title = "Blazer", marketplace = "AliExpress"),
            ClothingCategory.OUTERWEAR, priceCny = 48.64, landedAud = 50.10
        )
        assertEquals("AliExpress", PendingTryOn.consume()?.item?.source)
    }
}
