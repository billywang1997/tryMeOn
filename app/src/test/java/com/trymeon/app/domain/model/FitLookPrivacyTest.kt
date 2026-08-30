package com.trymeon.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a shared look is allowed to carry.
 *
 * A FitLook is readable by every signed-in user — that is the feature, not a
 * leak. Which makes the field list the whole of the privacy boundary: anything
 * on this class is published, so a field added without a line in the share
 * sheet is a measurement taken from someone without asking. It happened once,
 * with bust, waist and hips.
 */
class FitLookPrivacyTest {

    /** Exactly the body facts the share sheet shows before the wearer agrees. */
    private val disclosedBodyFields = setOf("gender", "heightCm", "weightKg")

    /** Everything else on the class: about the garment, not about the body. */
    private val nonBodyFields = setOf(
        "id", "uid", "imageUrl", "garment", "category", "sizeWorn",
        "fit", "note", "source", "keywords", "createdAt"
    )

    @Test
    fun `a shared look carries no body measurement the wearer was not shown`() {
        // Instance fields only: the constants on the companion are vocabulary,
        // not data about anyone.
        val fields = FitLook::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .filterNot { it.startsWith("$") }
            .toSet()

        val unexpected = fields - disclosedBodyFields - nonBodyFields
        assertTrue(
            "new fields on a world-readable record: $unexpected — if one of these " +
                "describes the wearer's body, the share sheet has to say so first",
            unexpected.isEmpty()
        )
    }

    @Test
    fun `the measurements that were published without asking are gone`() {
        val fields = FitLook::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
        listOf("bust", "waist", "hips").forEach {
            assertEquals("$it is back on a record strangers can read", false, it in fields)
        }
    }
}
