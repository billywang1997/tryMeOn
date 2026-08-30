package com.trymeon.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every shopping query passes through here, so a wrong answer is a strip full
 * of the wrong department's clothes.
 */
class GenderQueryTest {

    @Test
    fun `a query without a gender gets one`() {
        assertEquals("man linen blazer", ensureGenderInQuery("linen blazer", "Male"))
        assertEquals("woman linen blazer", ensureGenderInQuery("linen blazer", "Female"))
    }

    @Test
    fun `the wrong gender is replaced, not appended`() {
        // The model writes "women's …" for a man often enough to matter; the
        // point of this function is that the query cannot end up saying both.
        assertEquals("man linen blazer", ensureGenderInQuery("women's linen blazer", "Male"))
        assertEquals("woman linen blazer", ensureGenderInQuery("men's linen blazer", "Female"))
    }

    @Test
    fun `an unknown gender leaves the query alone`() {
        assertEquals("women's linen blazer", ensureGenderInQuery("women's linen blazer", "Other"))
        assertEquals("linen blazer", ensureGenderInQuery("linen blazer", null))
    }

    @Test
    fun `a word that merely contains men or man is not a gender`() {
        // "women" contains "men"; "mandarin" starts with "man". Stripping
        // either would quietly change what is being searched for.
        assertEquals("man mandarin collar shirt", ensureGenderInQuery("mandarin collar shirt", "Male"))
        assertEquals("woman performance jacket", ensureGenderInQuery("performance jacket", "Female"))
    }

    @Test
    fun `female is not left as -male- when stripped`() {
        assertEquals("man linen blazer", ensureGenderInQuery("female linen blazer", "Male"))
    }

    @Test
    fun `a query that was only a gender word does not become a stray space`() {
        assertEquals("man", ensureGenderInQuery("men's", "Male"))
    }
}
