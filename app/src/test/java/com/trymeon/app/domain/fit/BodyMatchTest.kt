package com.trymeon.app.domain.fit

import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.FitLook
import com.trymeon.app.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "People your size" is only honest if the matching is strict where it must
 * be — gender, height — and forgiving where a shopper would be.
 */
class BodyMatchTest {

    private val me = UserProfile(gender = "Male", height = 170, weight = 60)

    private fun look(
        gender: String = "Male", h: Int = 170, w: Int = 60,
        cat: ClothingCategory = ClothingCategory.INNER, garment: String = "black tee", id: String = "$gender$h$w$garment"
    ) = FitLook(id = id, gender = gender, heightCm = h, weightKg = w, category = cat.name, garment = garment,
        keywords = FitLook.keywordsOf(garment))

    @Test
    fun `a different gender never matches`() {
        assertNull(BodyMatch.distance(me, look(gender = "Female")))
    }

    @Test
    fun `gender spellings are normalised`() {
        assertTrue(BodyMatch.sameGender("male", "M"))
        assertTrue(BodyMatch.sameGender("Female", "woman"))
        assertTrue(!BodyMatch.sameGender("Other", "Other")) // unknown never matches anyone
    }

    @Test
    fun `height beyond the tolerance drops out`() {
        assertNull(BodyMatch.distance(me, look(h = 177)))
        assertEquals(1.0, BodyMatch.distance(me, look(h = 176))!!, 1e-9)
    }

    @Test
    fun `weight is optional but must be close when present`() {
        assertEquals(0.0, BodyMatch.distance(me, look(w = 0))!!, 1e-9)
        assertNull(BodyMatch.distance(me, look(w = 67)))
    }

    @Test
    fun `nearest body comes first`() {
        val looks = listOf(look(h = 175, w = 64, id = "far"), look(h = 171, w = 61, id = "near"), look(id = "exact"))
        val order = BodyMatch.forProfile(me, looks).map { it.look.id }
        assertEquals(listOf("exact", "near", "far"), order)
    }

    @Test
    fun `category narrows and keywords pull matching garments ahead`() {
        val looks = listOf(
            look(cat = ClothingCategory.SHOES, garment = "white sneakers", id = "shoes"),
            look(garment = "grey hoodie", id = "hoodie"),
            look(garment = "black oversized tee", id = "tee")
        )
        assertEquals(listOf("shoes"), BodyMatch.forProfile(me, looks, ClothingCategory.SHOES).map { it.look.id })
        val byKeyword = BodyMatch.forProfile(me, looks, ClothingCategory.INNER, keywords = listOf("oversized", "tee"))
        assertEquals("tee", byKeyword.first().look.id)
        assertEquals(2, byKeyword.size) // the hoodie stays, just behind
    }

    @Test
    fun `no profile means no strip`() {
        assertTrue(BodyMatch.forProfile(null, listOf(look())).isEmpty())
        assertTrue(BodyMatch.forProfile(UserProfile(gender = "Male"), listOf(look())).isEmpty())
    }
}
