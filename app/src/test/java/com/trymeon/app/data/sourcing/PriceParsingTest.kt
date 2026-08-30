package com.trymeon.app.data.sourcing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Listing prices arrive as free text; a misread here corrupts the quote silently. */
class PriceParsingTest {

    private fun parse(raw: String) = SourcingRepository.parsePriceCny(raw)

    @Test
    fun `reads the usual shapes`() {
        assertEquals(89.0, parse("89")!!, 1e-9)
        assertEquals(89.0, parse("89.00")!!, 1e-9)
        assertEquals(89.5, parse("¥89.50")!!, 1e-9)
        assertEquals(1288.0, parse("1288.00")!!, 1e-9)
    }

    @Test
    fun `a price range quotes the low end`() {
        assertEquals(89.0, parse("89.00-129.00")!!, 1e-9)
        assertEquals(89.0, parse("¥89 ~ ¥129")!!, 1e-9)
    }

    @Test
    fun `refuses what it cannot read rather than guessing zero`() {
        assertNull(parse(""))
        assertNull(parse("面议"))
        assertNull(parse("¥"))
        assertNull(parse("0.00"))
    }
}
