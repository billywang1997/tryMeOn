package com.trymeon.app.data.repository

import com.trymeon.app.data.local.DataStoreManager
import com.trymeon.app.data.remote.FirebaseStorageRepository
import com.trymeon.app.data.remote.FirestoreRepository
import com.trymeon.app.domain.model.UserProfile
import com.trymeon.app.data.auth.CloudIdentity
import kotlinx.coroutines.flow.Flow

class UserProfileRepository(
    private val store: DataStoreManager,
    private val firestoreRepo: FirestoreRepository? = null,
    private val storageRepo: FirebaseStorageRepository? = null
) {
    // Null when there is no cloud, rather than throwing: sync is optional
    // and every path below already handles not having a uid.
    private val uid get() = CloudIdentity.uid()

    fun getProfile(): Flow<UserProfile?> = store.profileFlow

    suspend fun getProfileOnce(): UserProfile? = store.getProfileOnce()

    /**
     * Saves the profile locally and, when signed in, to Firestore.
     * Local face/body photo paths are uploaded to Storage first so the
     * profile carries cloud URLs that work on any device.
     */
    suspend fun saveProfile(profile: UserProfile) {
        val currentUid = uid
        var p = profile

        if (currentUid != null && storageRepo != null) {
            if (p.faceImagePath.startsWith("/")) {
                storageRepo.uploadProfilePhoto(currentUid, "face", p.faceImagePath)
                    .getOrNull()?.takeIf { it.isNotEmpty() }
                    ?.let { p = p.copy(faceImagePath = it) }
            }
            if (p.bodyImagePath.startsWith("/")) {
                storageRepo.uploadProfilePhoto(currentUid, "body", p.bodyImagePath)
                    .getOrNull()?.takeIf { it.isNotEmpty() }
                    ?.let { p = p.copy(bodyImagePath = it) }
            }
        }

        store.saveProfile(p)
        currentUid?.let { firestoreRepo?.saveProfile(it, p) }
    }
}
