package com.example.myapplication.data.repository

import com.example.myapplication.data.local.DataStoreManager
import com.example.myapplication.data.remote.FirebaseStorageRepository
import com.example.myapplication.data.remote.FirestoreRepository
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.domain.model.SavedImage
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

    suspend fun saveImage(image: SavedImage) {
        // Upload local file to Storage if not already a URL
        val currentUid = uid
        val cloudPath = if (currentUid != null && storageRepo != null) {
            storageRepo.uploadSavedImage(currentUid, image.id, image.path)
                .getOrNull()?.takeIf { it.isNotEmpty() } ?: image.path
        } else image.path
        val saved = image.copy(path = cloudPath)
        store.saveImage(saved)
        currentUid?.let { firestoreRepo?.saveSavedImage(it, saved) }
    }

    suspend fun deleteSavedImage(imageId: Long) {
        store.deleteSavedImage(imageId)
        val currentUid = uid ?: return
        firestoreRepo?.deleteSavedImage(currentUid, imageId)
        storageRepo?.deleteSavedImage(currentUid, imageId)
    }
}
