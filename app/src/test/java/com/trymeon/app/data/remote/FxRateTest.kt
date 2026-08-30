package com.trymeon.app.data.remote

import com.trymeon.app.data.sourcing.FxPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The FX rate multiplies into every figure the sourcing feature shows, so the
 * failure that matters is not "no rate" — it is a wrong rate that looks fine.
 */
class FxRateTest {

    // ── Response parsing ────────────────────────────────────────────────────

    @Test
    fun `parses the ECB response`() {
        val body = """{"amount":1.0,"base":"CNY","date":"2026-08-27","rates":{"AUD":0.20696}}"""
        assertEquals(0.20696, FxParsing.parseFrankfurter(body)!!, 1e-9)
    }

    @Test
    fun `parses the backup response`() {
        val body = """{"result":"success","base_code":"CNY","rates":{"AUD":0.206816,"USD":0.14}}"""
        assertEquals(0.206816, FxParsing.parseErApi(body)!!, 1e-9)
    }

    @Test
    fun `rejects an inverted quote`() {
        // 4.83 is CNY per AUD. Taken as AUD per CNY it understates every landed
        // price roughly twentyfold and still reads like a plausible number.
        assertNull(FxParsing.parseFrankfurter("""{"amount":1.0,"base":"CNY","rates":{"AUD":4.83}}"""))
        assertNull(FxParsing.parseErApi("""{"result":"success","base_code":"CNY","rates":{"AUD":4.83}}"""))
    }

    @Test
    fun `rejects a non-unit amount rather than scaling it`() {
        val body = """{"amount":100.0,"base":"CNY","rates":{"AUD":20.696}}"""
        assertNull(FxParsing.parseFrankfurter(body))
    }

    @Test
    fun `rejects the wrong base currency`() {
        val body = """{"result":"success","base_code":"USD","rates":{"AUD":1.52}}"""
        assertNull(FxParsing.parseErApi(body))
    }

    @Test
    fun `rejects failures, junk and missing fields`() {
        assertNull(FxParsing.parseErApi("""{"result":"error","error-type":"invalid-key"}"""))
        assertNull(FxParsing.parseFrankfurter("""{"amount":1.0,"rates":{"USD":0.14}}"""))
        assertNull(FxParsing.parseFrankfurter("<html>502</html>"))
        assertNull(FxParsing.parseErApi(""))
        assertNull(FxParsing.parseFrankfurter("""{"amount":1.0,"rates":{"AUD":0}}"""))
    }

    // ── Refresh policy ──────────────────────────────────────────────────────

    private val now = 1_800_000_000_000L
    private fun hoursAgo(h: Long) = now - TimeUnit.HOURS.toMillis(h)
    private fun daysAgo(d: Long) = now - TimeUnit.DAYS.toMillis(d)

    @Test
    fun `refetches only once the rate is old enough to matter`() {
        assertFalse(FxPolicy.shouldRefetch(hoursAgo(2), now))
        assertFalse(FxPolicy.shouldRefetch(hoursAgo(11), now))
        assertTrue(FxPolicy.shouldRefetch(hoursAgo(13), now))
        assertTrue("never fetched", FxPolicy.shouldRefetch(0L, now))
    }

    @Test
    fun `a future timestamp is treated as a clock change, not a fresh rate`() {
        assertTrue(FxPolicy.shouldRefetch(now + TimeUnit.DAYS.toMillis(2), now))
    }

    @Test
    fun `staleness is flagged for old and fallback rates`() {
        val fresh = FxRate(0.207, hoursAgo(3), "ECB")
        val old = FxRate(0.207, daysAgo(6), "ECB")
        val fallback = FxRate(0.207, 0L, "indicative", isFallback = true)

        assertFalse(FxPolicy.isStale(fresh, now))
        assertTrue(FxPolicy.isStale(old, now))
        assertTrue(FxPolicy.isStale(fallback, now))
    }

    @Test
    fun `labels say where the rate came from and how old it is`() {
        assertEquals("¥1 = A\$0.2070 · ECB today", FxPolicy.label(FxRate(0.20696, hoursAgo(2), "ECB"), now))
        assertEquals("¥1 = A\$0.2070 · ECB yesterday", FxPolicy.label(FxRate(0.20696, daysAgo(1), "ECB"), now))
        assertEquals("¥1 = A\$0.2070 · ECB 5 days old", FxPolicy.label(FxRate(0.20696, daysAgo(5), "ECB"), now))
        assertEquals(
            "¥1 = A\$0.2070 · indicative rate",
            FxPolicy.label(FxRate(0.20696, 0L, "indicative", isFallback = true), now)
        )
    }
}
