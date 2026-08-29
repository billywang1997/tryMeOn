package com.example.myapplication.domain.tryon

import com.example.myapplication.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The signature decides when a paid image generation happens.
 *
 * Too eager and every try-on rebuilds the portrait and returns a different
 * person; too lax and a user who changes their photo keeps being shown the old
 * face. Both failures are silent, which is why they are pinned here.
 */
class VirtualModelTest {

    @get:Rule val temp = TemporaryFolder()

    private fun photo(content: String = "abc"): File =
        temp.newFile("face_${System.nanoTime()}.jpg").apply { writeText(content) }

    private val profile = UserProfile(gender = "Female", height = 170, weight = 60, bust = 86, waist = 68, hips = 92)

    @Test
    fun `same photo and measurements produce the same signature`() {
        val f = photo()
        assertEquals(
            VirtualModelSignature.of(profile, f.absolutePath),
            VirtualModelSignature.of(profile, f.absolutePath)
        )
    }

    @Test
    fun `changing a body measurement invalidates the portrait`() {
        val f = photo()
        val before = VirtualModelSignature.of(profile, f.absolutePath)
        listOf(
            profile.copy(height = 175),
            profile.copy(weight = 65),
            profile.copy(bust = 90),
            profile.copy(waist = 70),
            profile.copy(hips = 96),
            profile.copy(gender = "Male")
        ).forEach {
            assertNotEquals(
                "a changed body must not keep the old portrait",
                before, VirtualModelSignature.of(it, f.absolutePath)
            )
        }
    }

    @Test
    fun `replacing the photo at the same path invalidates it`() {
        val f = photo("original")
        val before = VirtualModelSignature.of(profile, f.absolutePath)
        // Same path, different content: comparing paths alone would miss this.
        f.writeText("a completely different photo")
        f.setLastModified(f.lastModified() + 10_000)
        assertNotEquals(before, VirtualModelSignature.of(profile, f.absolutePath))
    }

    @Test
    fun `an unrelated edit does not throw away a good portrait`() {
        val f = photo()
        // Display name and body photo path do not shape the portrait, so paying
        // to rebuild it on that change would be waste.
        assertEquals(
            VirtualModelSignature.of(profile, f.absolutePath),
            VirtualModelSignature.of(profile.copy(id = 42, bodyImagePath = "/somewhere/else.jpg"), f.absolutePath)
        )
    }

    @Test
    fun `validity requires both a matching signature and a file on disk`() {
        val f = photo()
        val model = VirtualModel(
            imagePath = temp.newFile("portrait.png").apply { writeText("x") }.absolutePath,
            signature = VirtualModelSignature.of(profile, f.absolutePath),
            createdAtMillis = 1L
        )
        assertTrue(VirtualModelSignature.isValid(model, profile, f.absolutePath))

        // Deleted by the OS reclaiming space: the record survives, the image does not.
        File(model.imagePath).delete()
        assertFalse(VirtualModelSignature.isValid(model, profile, f.absolutePath))
    }

    @Test
    fun `no stored portrait is never valid`() {
        val f = photo()
        assertFalse(VirtualModelSignature.isValid(null, profile, f.absolutePath))
        assertFalse(
            VirtualModelSignature.isValid(
                VirtualModel("", "sig", 1L), profile, f.absolutePath
            )
        )
    }

    @Test
    fun `a missing source photo still yields a stable signature`() {
        // The photo can vanish while a portrait built from it is still on disk;
        // that must not crash or churn.
        val missing = "/no/such/photo.jpg"
        assertEquals(
            VirtualModelSignature.of(profile, missing),
            VirtualModelSignature.of(profile, missing)
        )
    }
}
