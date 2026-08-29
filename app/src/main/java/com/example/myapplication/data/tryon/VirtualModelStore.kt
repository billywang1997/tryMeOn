package com.example.myapplication.data.tryon

import android.util.Log
import com.example.myapplication.AppSettings
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.domain.model.UserProfile
import com.example.myapplication.domain.tryon.VirtualModel
import com.example.myapplication.domain.tryon.VirtualModelSignature
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps one generated portrait of the user and rebuilds it only when the
 * inputs it was built from change.
 *
 * The reason to persist rather than cache: a portrait costs a paid image
 * generation, and regenerating it per session would spend that repeatedly to
 * get a subtly different person each time. Held under a lock because two
 * try-ons started together would otherwise both find nothing stored and both
 * pay to generate.
 */
class VirtualModelStore(
    private val settings: AppSettings,
    private val claude: ClaudeApiService,
    private val apiKey: String
) {
    private val lock = Mutex()

    fun current(): VirtualModel? {
        val path = settings.virtualModelPath
        if (path.isBlank()) return null
        return VirtualModel(path, settings.virtualModelSignature, settings.virtualModelCreatedAt)
    }

    /** True when a stored portrait still matches this photo and these measurements. */
    fun isFresh(profile: UserProfile?, facePhotoPath: String): Boolean =
        VirtualModelSignature.isValid(current(), profile, facePhotoPath)

    /**
     * The portrait to dress, generating one if needed. Failure is not fatal:
     * try-on falls back to the raw photo, which is what it did before.
     */
    suspend fun ensure(profile: UserProfile?, facePhotoPath: String): VirtualModel? {
        if (facePhotoPath.isBlank() || apiKey.isBlank()) return null
        return lock.withLock {
            current()?.takeIf { VirtualModelSignature.isValid(it, profile, facePhotoPath) }
                ?: generate(profile, facePhotoPath)
        }
    }

    /** Discard and rebuild — for when the user simply dislikes the result. */
    suspend fun regenerate(profile: UserProfile?, facePhotoPath: String): VirtualModel? {
        if (facePhotoPath.isBlank() || apiKey.isBlank()) return null
        return lock.withLock { generate(profile, facePhotoPath) }
    }

    private suspend fun generate(profile: UserProfile?, facePhotoPath: String): VirtualModel? {
        val path = claude.generateModelPortrait(apiKey, facePhotoPath, profile)
            .onFailure { Log.w(TAG, "portrait failed: ${it.message}") }
            .getOrNull() ?: return null

        val model = VirtualModel(
            imagePath = path,
            signature = VirtualModelSignature.of(profile, facePhotoPath),
            createdAtMillis = System.currentTimeMillis()
        )
        settings.virtualModelPath = model.imagePath
        settings.virtualModelSignature = model.signature
        settings.virtualModelCreatedAt = model.createdAtMillis
        return model
    }

    fun clear() {
        settings.virtualModelPath = ""
        settings.virtualModelSignature = ""
        settings.virtualModelCreatedAt = 0L
    }

    private companion object { const val TAG = "VirtualModel" }
}
