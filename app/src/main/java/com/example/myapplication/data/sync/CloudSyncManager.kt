package com.example.myapplication.data.sync

import com.example.myapplication.data.local.DataStoreManager
import com.example.myapplication.data.remote.FirestoreRepository

/**
 * Pulls all cloud data into local DataStore when the local wardrobe is empty but
 * the user has Firestore data (i.e., signed-in on a new device).
 */
class CloudSyncManager(
    private val store: DataStoreManager,
    private val firestoreRepo: FirestoreRepository
) {

    suspend fun restoreIfEmpty(uid: String) {
        val localWardrobe = store.getAllClothingOnce()
        if (localWardrobe.isNotEmpty()) return  // already have local data, skip

        val cloudWardrobe = firestoreRepo.loadWardrobe(uid)
        if (cloudWardrobe.isEmpty()) return  // nothing in cloud either

        store.importWardrobe(cloudWardrobe)

        val logs = firestoreRepo.loadHistory(uid)
        if (logs.isNotEmpty()) store.importOutfitLogs(logs)

        val profile = firestoreRepo.loadProfile(uid)
        if (profile != null) store.importProfile(profile)

        val savedImages = firestoreRepo.loadSavedImages(uid)
        if (savedImages.isNotEmpty()) store.importSavedImages(savedImages)
    }
}
