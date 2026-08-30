package com.trymeon.app.data.repository

import com.trymeon.app.data.local.DataStoreManager
import com.trymeon.app.data.remote.FirebaseStorageRepository
import com.trymeon.app.data.remote.FirestoreRepository
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.model.SavedImage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow

class WardrobeRepository(
    private val store: DataStoreManager,
    private val firestoreRepo: FirestoreRepository? = null,
    private val storageRepo: FirebaseStorageRepository? = null
) {

    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    fun getAllClothing(): Flow<List<ClothingItem>> = store.clothingFlow

    suspend fun getAllClothingOnce(): List<ClothingItem> = store.getAllClothingOnce()

    suspend fun addClothing(item: ClothingItem) {
        val assignedId = store.addClothing(item)
        val currentUid = uid ?: return
        // Upload image to Storage, then update cloudImageUrl in both local and Firestore
        val cloudUrl = storageRepo?.uploadClothingImage(currentUid, assignedId, item.imagePath)
            ?.getOrNull()?.takeIf { it.isNotEmpty() } ?: ""
        val saved = item.copy(id = assignedId, cloudImageUrl = cloudUrl)
        if (cloudUrl.isNotEmpty()) store.updateClothing(saved)
        firestoreRepo?.saveWardrobeItem(currentUid, saved)
    }

    suspend fun deleteClothing(item: ClothingItem) {
        store.deleteClothing(item.id)
        val currentUid = uid ?: return
        firestoreRepo?.deleteWardrobeItem(currentUid, item.id)
        storageRepo?.deleteClothingImage(currentUid, item.id)
    }

    suspend fun updateClothing(item: ClothingItem) {
        store.updateClothing(item)
        val currentUid = uid ?: return
        firestoreRepo?.saveWardrobeItem(currentUid, item)
    }

    suspend fun toggleFavorite(itemId: Long) {
        store.toggleFavorite(itemId)
        val currentUid = uid ?: return
        val updated = store.getAllClothingOnce().find { it.id == itemId } ?: return
        firestoreRepo?.saveWardrobeItem(currentUid, updated)
    }

    // ── Saved images ──────────────────────────────────────────────────────

    fun getSavedImages(): Flow<List<SavedImage>> = store.savedImagesFlow

    /**
     * Persists a saved look. Returns true when it was also backed up to the cloud
     * backend (Storage + Firestore); false when only stored locally because the
     * user is not signed in.
     */
    suspend fun saveImage(image: SavedImage): Boolean {
        // Copy the generated image out of the volatile cache dir into persistent
        // storage so the saved look survives OS cache eviction.
        val persisted = image.copy(path = store.persistLookImage(image.path))
        // Upload local file to Storage if not already a URL
        val currentUid = uid
        val cloudPath = if (currentUid != null && storageRepo != null) {
            storageRepo.uploadSavedImage(currentUid, persisted.id, persisted.path)
                .getOrNull()?.takeIf { it.isNotEmpty() } ?: persisted.path
        } else persisted.path
        val saved = persisted.copy(path = cloudPath)
        store.saveImage(saved)
        currentUid?.let { firestoreRepo?.saveSavedImage(it, saved) }
        return currentUid != null
    }

    suspend fun deleteSavedImage(imageId: Long) {
        store.deleteSavedImage(imageId)
        val currentUid = uid ?: return
        firestoreRepo?.deleteSavedImage(currentUid, imageId)
        storageRepo?.deleteSavedImage(currentUid, imageId)
    }
}
