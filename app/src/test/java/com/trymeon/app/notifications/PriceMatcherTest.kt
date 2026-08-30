package com.trymeon.app.notifications

import com.trymeon.app.data.remote.EbayItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Built from what a shopping search actually returns for "Nike Air Force 1" —
 * five results, none of them titled that. The old exact-match rule found
 * nothing in this list, which is why price watch never reported anything.
 */
class PriceMatcherTest {

    private fun item(title: String, price: String, url: String = "") =
        EbayItem(itemId = title.hashCode().toString(), title = title, price = price, itemWebUrl = url)

    private val realResults = listOf(
        item("Nike Air Force 1 07 Men's", "$180.00"),
        item("Nike Air Force 1 07", "$136.00"),
        item("Nike Air Force 1 '07 Men's Shoes", "$180.00"),
        item("Nike Air Force 1 '07 LV8 Men's", "$160.00"),
        item("Nike Men's Air Force 1 '07 Shoes", "$180.00")
    )

    @Test
    fun `matches the listings a real search returns`() {
        val match = PriceMatcher.bestPrice("Nike Air Force 1", "", realResults)
        assertEquals("nothing matched — the old bug", 136.0, match!!.second, 0.001)
    }

    @Test
    fun `takes the best price among matches, since that is the point of watching`() {
        assertEquals(136.0, PriceMatcher.bestPrice("Nike Air Force 1", "", realResults)!!.second, 0.001)
    }

    @Test
    fun `an exact URL match wins whatever the listing is called now`() {
        val renamed = listOf(
            item("Completely Different Product Name", "$99.00", "https://shop.example/item/1"),
            item("Nike Air Force 1 07", "$136.00")
        )
        val match = PriceMatcher.bestPrice("Nike Air Force 1", "https://shop.example/item/1", renamed)
        assertEquals(99.0, match!!.second, 0.001)
    }

    @Test
    fun `refuses a different product rather than reporting a false drop`() {
        // Telling someone their item is cheap when a different one is would be
        // worse than staying quiet.
        val others = listOf(
            item("Adidas Samba OG", "$40.00"),
            item("Nike Dunk Low", "$90.00"),
            item("Air Jordan 1 Mid", "$110.00")
        )
        assertNull(PriceMatcher.bestPrice("Nike Air Force 1", "", others))
    }

    @Test
    fun `a partial brand overlap is not a match`() {
        val partial = listOf(item("Nike socks 3 pack", "$15.00"))
        assertNull(PriceMatcher.bestPrice("Nike Air Force 1", "", partial))
    }

    @Test
    fun `ignores noise words that appear in every listing`() {
        val match = PriceMatcher.bestPrice(
            "Nike Air Force 1 Men's Shoes",
            "",
            listOf(item("Nike Air Force 1 07", "$136.00"))
        )
        assertEquals(136.0, match!!.second, 0.001)
    }

    @Test
    fun `reads the price formats a shopping search produces`() {
        listOf("$136.00" to 136.0, "136" to 136.0, "A\$1,299.00" to 1299.0, "AUD 89.95" to 89.95)
            .forEach { (raw, expected) ->
                val m = PriceMatcher.bestPrice("test item", "", listOf(item("test item extra", raw)))
                assertEquals(raw, expected, m!!.second, 0.001)
            }
    }

    @Test
    fun `skips results with no usable price`() {
        val mixed = listOf(
            item("Nike Air Force 1 07", "Price on request"),
            item("Nike Air Force 1 07 Low", "$0"),
            item("Nike Air Force 1 07 White", "$150.00")
        )
        assertEquals(150.0, PriceMatcher.bestPrice("Nike Air Force 1", "", mixed)!!.second, 0.001)
    }

    @Test
    fun `nothing to match on means no match`() {
        assertNull(PriceMatcher.bestPrice("Nike Air Force 1", "", emptyList()))
        // A title made only of noise carries no identity to compare.
        assertNull(PriceMatcher.bestPrice("men's shoes", "", realResults))
    }

    // ── Chinese titles ──────────────────────────────────────────────────────
    //
    // Everything saved now comes from Taobao, so this is the common case rather
    // than the exotic one. Chinese is not space delimited, so the word rule
    // above collapses a whole phrase into one token and matches nothing.

    @Test
    fun `matches a seller title padded with the usual marketing`() {
        val cn = listOf(item("2026春夏新款亚麻短款西装外套女韩版显瘦", "¥210.90"))
        assertEquals(210.90, PriceMatcher.bestPrice("亚麻短款西装外套", "", cn)!!.second, 0.001)
    }

    @Test
    fun `matches when the seller reorders the description`() {
        val cn = listOf(item("女士短款亚麻西装外套 通勤", "¥188.00"))
        assertEquals(188.0, PriceMatcher.bestPrice("亚麻短款西装外套女", "", cn)!!.second, 0.001)
    }

    @Test
    fun `refuses a different Chinese product`() {
        val cn = listOf(
            item("纯棉圆领短袖T恤男", "¥39.00"),
            item("真皮乐福鞋女厚底", "¥299.00")
        )
        assertNull(PriceMatcher.bestPrice("亚麻短款西装外套", "", cn))
    }

    @Test
    fun `takes the cheapest among Chinese matches`() {
        val cn = listOf(
            item("亚麻短款西装外套女 新款", "¥210.90"),
            item("春季亚麻短款西装外套女", "¥168.00"),
            item("亚麻短款西装外套女韩版", "¥259.00")
        )
        assertEquals(168.0, PriceMatcher.bestPrice("亚麻短款西装外套", "", cn)!!.second, 0.001)
    }
}
