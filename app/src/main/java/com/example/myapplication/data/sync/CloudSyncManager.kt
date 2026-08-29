package com.example.myapplication.data.sync

import com.example.myapplication.AppSettings
import com.example.myapplication.data.local.DataStoreManager
import com.example.myapplication.data.remote.FirestoreRepository
import kotlinx.coroutines.flow.first

/**
 * Pulls cloud data into local storage on a signed-in device. Each data type is
 * restored independently — a non-empty wardrobe no longer blocks restoring,
 * say, a missing wishlist or profile.
 */
class CloudSyncManager(
    private val store: DataStoreManager,
    private val firestoreRepo: FirestoreRepository,
    private val settings: AppSettings? = null
) {

    suspend fun restoreIfEmpty(uid: String) {
        if (store.getAllClothingOnce().isEmpty()) {
            firestoreRepo.loadWardrobe(uid).takeIf { it.isNotEmpty() }
                ?.let { store.importWardrobe(it) }
        }
        if (store.outfitLogsFlow.first().isEmpty()) {
            firestoreRepo.loadHistory(uid).takeIf { it.isNotEmpty() }
                ?.let { store.importOutfitLogs(it) }
        }
        if (store.getProfileOnce() == null) {
            firestoreRepo.loadProfile(uid)?.let { store.importProfile(it) }
        }
        if (store.savedImagesFlow.first().isEmpty()) {
            firestoreRepo.loadSavedImages(uid).takeIf { it.isNotEmpty() }
                ?.let { store.importSavedImages(it) }
        }
        if (store.wishlistFlow.first().isEmpty()) {
            firestoreRepo.loadWishlist(uid).takeIf { it.isNotEmpty() }
                ?.let { store.importWishlist(it) }
        }
        if (settings != null && settings.styleKeywords.isEmpty()) {
            firestoreRepo.loadStyles(uid).takeIf { it.isNotEmpty() }
                ?.let { settings.styleKeywords = it }
        }
    }
}
