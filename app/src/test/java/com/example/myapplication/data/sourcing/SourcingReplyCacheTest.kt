package com.example.myapplication.data.sourcing

import com.example.myapplication.domain.model.ClothingCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * A cache that keys too loosely serves the wrong translation; one that keys too
 * tightly, or drops entries early, quietly pays for a chat call the user has
 * already bought. Both are invisible without these.
 */
class SourcingReplyCacheTest {

    private val reply = """
        CN|亚麻短款西装外套
        EN|Cropped linen blazer
        CAT|OUTERWEAR
        PARCEL|450|30|25|5
    """.trimIndent()

    private var clock = 1_000_000L
    private fun cache(max: Int = 60, ttl: Long = TimeUnit.DAYS.toMillis(30)) =
        SourcingReplyCache(maxEntries = max, ttlMillis = ttl, now = { clock })

    // ── Keying ──────────────────────────────────────────────────────────────

    @Test
    fun `case and spacing do not create a second entry`() {
        val c = cache()
        assertEquals(
            c.key("cropped linen blazer", "Female", null),
            c.key("  Cropped   Linen  Blazer ", "female", null)
        )
    }

    @Test
    fun `gender and category do change the answer, so they change the key`() {
        val c = cache()
        val base = c.key("linen blazer", "Female", null)
        assertNotEquals(base, c.key("linen blazer", "Male", null))
        assertNotEquals(base, c.key("linen blazer", "Female", ClothingCategory.OUTERWEAR))
    }

    // ── Hits and misses ─────────────────────────────────────────────────────

    @Test
    fun `a stored reply comes back`() {
        val c = cache()
        val k = c.key("linen blazer", "", null)
        assertNull(c.get(k))
        c.put(k, reply)
        assertEquals(reply, c.get(k))
    }

    @Test
    fun `an unusable reply is never cached`() {
        val c = cache()
        val k = c.key("linen blazer", "", null)
        // Pinning a refusal for a month would make the phrase permanently broken.
        c.put(k, "I'm sorry, I can't help with that.")
        c.put(k, "")
        assertNull(c.get(k))
        assertEquals(0, c.size())
    }

    @Test
    fun `entries expire so stale seller vocabulary is refreshed`() {
        val c = cache(ttl = TimeUnit.DAYS.toMillis(30))
        val k = c.key("linen blazer", "", null)
        c.put(k, reply)

        clock += TimeUnit.DAYS.toMillis(29)
        assertEquals("still fresh at 29 days", reply, c.get(k))

        clock += TimeUnit.DAYS.toMillis(2)
        assertNull("expired at 31 days", c.get(k))
    }

    // ── Bounded growth ──────────────────────────────────────────────────────

    @Test
    fun `the least recently used phrase is evicted, not the oldest stored`() {
        val c = cache(max = 3)
        val keys = (1..3).map { c.key("item $it", "", null) }
        keys.forEach { c.put(it, reply) }

        // Touch the first so it is no longer the least recent.
        c.get(keys[0])
        c.put(c.key("item 4", "", null), reply)

        assertEquals(3, c.size())
        assertEquals("a phrase just used must survive", reply, c.get(keys[0]))
        assertNull("the untouched one goes", c.get(keys[1]))
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    @Test
    fun `restoring drops what has already expired`() {
        val c = cache(ttl = TimeUnit.DAYS.toMillis(30))
        val fresh = c.key("fresh", "", null)
        val old = c.key("old", "", null)
        c.restore(
            mapOf(
                fresh to SourcingReplyCache.Entry(reply, clock - TimeUnit.DAYS.toMillis(1)),
                old to SourcingReplyCache.Entry(reply, clock - TimeUnit.DAYS.toMillis(40))
            )
        )
        assertEquals(reply, c.get(fresh))
        assertNull(c.get(old))
    }

    @Test
    fun `restoring more than fits keeps the newest`() {
        val c = cache(max = 2)
        val saved = (1..5).associate {
            c.key("item $it", "", null) to SourcingReplyCache.Entry(reply, clock - (6 - it) * 1000L)
        }
        c.restore(saved)
        assertEquals(2, c.size())
        assertEquals(reply, c.get(c.key("item 5", "", null)))
        assertNull(c.get(c.key("item 1", "", null)))
    }

    @Test
    fun `a snapshot survives a round trip`() {
        val c = cache()
        val k = c.key("linen blazer", "Female", ClothingCategory.OUTERWEAR)
        c.put(k, reply)

        val restored = cache()
        restored.restore(c.snapshot())
        assertEquals(reply, restored.get(k))
    }
}
