package com.trymeon.app.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These templates carry the referral code, so a malformed one does not fail
 * loudly — it sends the shopper to a page that works and pays nobody.
 */
class DaigouTest {

    @After fun reset() = Daigou.init(emptyList())

    private val config =
        "agent1|Agent One|https://a.example/order?url={url}&ref={code}|CODE1" +
        ";;agent2|Agent Two|https://b.example/buy?tp={url}|CODE2"

    @Test
    fun `parses a configured list`() {
        val providers = Daigou.parse(config)
        assertEquals(2, providers.size)
        assertEquals("Agent One", providers[0].name)
        assertEquals("CODE1", providers[0].referralCode)
        assertEquals("CODE2", providers[1].referralCode)
    }

    @Test
    fun `a provider with no referral code is still usable`() {
        // Worth supporting: an agent may be listed before a partner code exists.
        val p = Daigou.parse("a|A|https://a.example/order?url={url}").single()
        assertEquals("", p.referralCode)
        assertTrue(p.configured)
    }

    @Test
    fun `rejects a template that cannot open a product page`() {
        // Sending someone to an agent's homepage looks like it worked and buys
        // nothing, so an entry without {url} is dropped rather than used.
        assertTrue(Daigou.parse("a|A|https://a.example/home|CODE").isEmpty())
        assertTrue(Daigou.parse("a|A||CODE").isEmpty())
        assertTrue(Daigou.parse("||https://a.example/{url}|CODE").isEmpty())
        assertTrue(Daigou.parse("incomplete|entry").isEmpty())
        assertTrue(Daigou.parse("").isEmpty())
    }

    @Test
    fun `one bad entry does not discard the good ones`() {
        val providers = Daigou.parse("bad|Bad|no-placeholder|X;;$config")
        assertEquals(2, providers.size)
    }

    @Test
    fun `builds an order link with the product URL encoded`() {
        Daigou.init(config)
        val url = Daigou.orderUrl("https://item.taobao.com/item.htm?id=123", "agent1")!!
        assertTrue(url.startsWith("https://a.example/order?url="))
        // Unencoded, the product's own query string would truncate the link.
        assertTrue(url.contains("https%3A%2F%2Fitem.taobao.com%2Fitem.htm%3Fid%3D123"))
        assertTrue(url.endsWith("ref=CODE1"))
    }

    @Test
    fun `falls back to the first provider when none is preferred`() {
        Daigou.init(config)
        assertEquals("agent1", Daigou.preferred()?.id)
        Daigou.choose("agent2")
        assertEquals("agent2", Daigou.preferred()?.id)
    }

    @Test
    fun `an unknown preference does not silently pick a different agent's link`() {
        Daigou.init(config, preferredAgentId = "nope")
        // init falls back to the first configured provider rather than none.
        assertEquals("agent1", Daigou.preferred()?.id)
        assertNull(Daigou.orderUrl("https://item.taobao.com/item.htm?id=1", "nope"))
    }

    @Test
    fun `with nothing configured there is no order link at all`() {
        Daigou.init("")
        // Better to hide the button than to send someone somewhere broken.
        assertNull(Daigou.orderUrl("https://item.taobao.com/item.htm?id=1"))
    }

    @Test
    fun `a blank product URL yields nothing`() {
        Daigou.init(config)
        assertNull(Daigou.orderUrl(""))
    }
}
