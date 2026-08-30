package com.trymeon.app.domain.tryon

import com.trymeon.app.domain.model.UserProfile
import java.io.File

/**
 * A generated full-body portrait of the user, kept and reused.
 *
 * Every try-on currently rebuilds the person from a raw photo alongside the
 * garments, which costs a full image generation each time and — because the
 * model reinvents them from scratch — quietly returns a slightly different
 * person on every run. Taobao's own try-on does not work that way: it builds a
 * virtual model that matches the shopper once, then dresses that same model for
 * every garment. Generating the person once and reusing it makes each try-on
 * cheaper and, more importantly, makes it the same person twice.
 */
data class VirtualModel(
    val imagePath: String,
    /** Inputs this portrait was built from; a change invalidates it. */
    val signature: String,
    val createdAtMillis: Long
) {
    fun existsOnDisk(): Boolean = imagePath.isNotBlank() && File(imagePath).exists()
}

object VirtualModelSignature {

    /**
     * Identifies the inputs a portrait depends on.
     *
     * Deliberately narrow: it covers the photo and the measurements that shape
     * the body, and nothing else. Including something incidental would throw
     * away a good portrait — and a paid generation — on an unrelated edit.
     */
    fun of(profile: UserProfile?, facePhotoPath: String): String {
        val photo = File(facePhotoPath)
        // Length and modified time catch the photo being replaced at the same path.
        val photoPart = if (photo.exists()) {
            "${photo.absolutePath}:${photo.length()}:${photo.lastModified()}"
        } else {
            facePhotoPath
        }
        val body = listOf(
            profile?.gender.orEmpty(),
            profile?.height ?: 0,
            profile?.weight ?: 0,
            profile?.bust ?: 0,
            profile?.waist ?: 0,
            profile?.hips ?: 0
        ).joinToString("/")
        return "$photoPart|$body"
    }

    /** True when [model] was built from these inputs and its file is still there. */
    fun isValid(model: VirtualModel?, profile: UserProfile?, facePhotoPath: String): Boolean {
        if (model == null || !model.existsOnDisk()) return false
        return model.signature == of(profile, facePhotoPath)
    }
}
