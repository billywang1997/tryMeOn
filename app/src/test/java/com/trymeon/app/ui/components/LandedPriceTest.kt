package com.trymeon.app.ui.components

import com.trymeon.app.data.remote.EbayItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The strips show one number where the sourcing screen shows a ledger. Read
 * bare, that number is indistinguishable from a sticker price — the confusion
 * the cost engine exists to remove.
 */
class LandedPriceTest {

    @Test
    fun `a sourced row says the price is delivered`() {
        assertEquals("A$50.10 delivered", EbayItem(price = "50.10", currency = "AUD").landedLabel())
    }

    @Test
    fun `a row from elsewhere makes no landed claim`() {
        // Nothing outside the sourcing pipeline has been through the engine, so
        // calling its price "delivered" would be inventing a guarantee.
        assertEquals("USD 45.00", EbayItem(price = "45.00", currency = "USD").landedLabel())
    }

    @Test
    fun `no price, nothing to say`() {
        assertEquals("", EbayItem(price = "", currency = "AUD").landedLabel())
    }
}
