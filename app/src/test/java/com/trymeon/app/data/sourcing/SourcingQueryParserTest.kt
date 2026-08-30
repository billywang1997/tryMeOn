package com.trymeon.app.data.sourcing

import com.trymeon.app.domain.model.ClothingCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The model's reply is the one input here nobody controls. A dropped field must
 * degrade to a category preset, never to a zero-gram parcel — a zero parcel
 * quotes zero freight, which is a wrong number presented confidently.
 */
class SourcingQueryParserTest {

    private val full = """
        CN|亚麻小西装外套 短款
        CN|薄款西装 女 夏
        CN|盐系 小西装
        EN|Cropped linen blazer
        CAT|OUTERWEAR
        PARCEL|420|32|26|6
        NOTE|Asian sizing runs 1-2 sizes small — check 胸围 in cm
    """.trimIndent()

    @Test
    fun `parses a complete reply`() {
        val q = SourcingQueryParser.parse(full, null)!!
        assertEquals(listOf("亚麻小西装外套 短款", "薄款西装 女 夏", "盐系 小西装"), q.chineseQueries)
        assertEquals("亚麻小西装外套 短款", q.primaryQuery)
        assertEquals("Cropped linen blazer", q.englishSummary)
        assertEquals(ClothingCategory.OUTERWEAR, q.category)
        assertEquals(420, q.parcel.actualGrams)
        assertEquals(32.0, q.parcel.lengthCm, 0.001)
        assertEquals(6.0, q.parcel.heightCm, 0.001)
        assertTrue(q.buyerNote.contains("sizing"))
    }

    @Test
    fun `keeps at most three queries`() {
        val many = (1..6).joinToString("\n") { "CN|查询$it" } + "\nCAT|INNER"
        assertEquals(3, SourcingQueryParser.parse(many, null)!!.chineseQueries.size)
    }

    @Test
    fun `a missing parcel falls back to the category preset, never to zero`() {
        val noParcel = """
            CN|羊毛大衣 女
            CAT|OUTERWEAR
        """.trimIndent()
        val q = SourcingQueryParser.parse(noParcel, null)!!
        assertTrue("freight would be quoted at zero", q.parcel.actualGrams > 0)
        assertTrue(q.parcel.lengthCm > 0 && q.parcel.widthCm > 0 && q.parcel.heightCm > 0)
    }

    @Test
    fun `a partial parcel line still falls back rather than half-filling`() {
        val partial = "CN|帆布包\nCAT|BAG\nPARCEL|600|35"
        val q = SourcingQueryParser.parse(partial, null)!!
        assertEquals(600, q.parcel.actualGrams)
        // Width and height were missing, so the whole geometry comes from the preset.
        assertTrue(q.parcel.widthCm > 0 && q.parcel.heightCm > 0)
    }

    @Test
    fun `an unknown category falls back to the hint`() {
        val bad = "CN|连衣裙\nCAT|JUMPSUIT"
        assertEquals(
            ClothingCategory.DRESS,
            SourcingQueryParser.parse(bad, ClothingCategory.DRESS)!!.category
        )
    }

    @Test
    fun `no Chinese queries means no result`() {
        assertNull(SourcingQueryParser.parse("EN|nothing\nCAT|INNER", ClothingCategory.INNER))
        assertNull(SourcingQueryParser.parse("", null))
        assertNull(SourcingQueryParser.parse("I'm sorry, I can't help with that.", null))
    }

    @Test
    fun `tolerates surrounding prose and blank lines`() {
        val messy = """
            Sure! Here you go:

            CN|工装裤 女 直筒

            CAT|PANTS
            PARCEL|540|30|24|7
            Hope that helps.
        """.trimIndent()
        val q = SourcingQueryParser.parse(messy, null)!!
        assertEquals(listOf("工装裤 女 直筒"), q.chineseQueries)
        assertEquals(ClothingCategory.PANTS, q.category)
        assertEquals(540, q.parcel.actualGrams)
    }

    @Test
    fun `quantity stacks weight and height but not footprint`() {
        val one = SourcingQueryParser.parse(full, null, quantity = 1)!!
        val three = SourcingQueryParser.parse(full, null, quantity = 3)!!
        assertEquals(one.parcel.actualGrams * 3, three.parcel.actualGrams)
        assertEquals(one.parcel.heightCm * 3, three.parcel.heightCm, 0.001)
        // Three shirts in a bag are thicker, not longer.
        assertEquals(one.parcel.lengthCm, three.parcel.lengthCm, 0.001)
        assertEquals(one.parcel.widthCm, three.parcel.widthCm, 0.001)
    }
}
