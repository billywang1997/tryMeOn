package com.example.myapplication.data.sourcing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** These queries go straight into a paid search, so a malformed line must be dropped, not searched. */
class ClosetGapsTest {

    @Test
    fun `parses gap lines`() {
        val raw = """
            GAP|black wide leg trousers|Pairs with 6 of your tops
            GAP|cream knit cardigan|Layers over 4 dresses you own
        """.trimIndent()
        val gaps = ClosetGapService.parse(raw)
        assertEquals(2, gaps.size)
        assertEquals("black wide leg trousers", gaps[0].query)
        assertEquals("Pairs with 6 of your tops", gaps[0].reason)
    }

    @Test
    fun `keeps at most four`() {
        val raw = (1..8).joinToString("\n") { "GAP|item $it|reason" }
        assertEquals(4, ClosetGapService.parse(raw).size)
    }

    @Test
    fun `a gap with no reason is still usable`() {
        assertEquals("silk scarf", ClosetGapService.parse("GAP|silk scarf").single().query)
    }

    @Test
    fun `skips empty queries and unrelated lines`() {
        val raw = """
            Sure, here are some ideas:
            GAP||no query here
            GAP|leather ankle boots|Works with everything
            BUY|wrong prefix|ignored
        """.trimIndent()
        val gaps = ClosetGapService.parse(raw)
        assertEquals(1, gaps.size)
        assertEquals("leather ankle boots", gaps.single().query)
    }

    @Test
    fun `a refusal or empty reply yields nothing to search`() {
        assertTrue(ClosetGapService.parse("I'm sorry, I can't help with that.").isEmpty())
        assertTrue(ClosetGapService.parse("").isEmpty())
    }
}
