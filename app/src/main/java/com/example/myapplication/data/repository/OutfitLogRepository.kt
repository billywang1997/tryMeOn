package com.example.myapplication.data.repository

import com.example.myapplication.data.local.DataStoreManager
import com.example.myapplication.data.remote.FirestoreRepository
import com.example.myapplication.domain.model.OutfitLog
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow

class OutfitLogRepository(
    private val store: DataStoreManager,
    private val firestoreRepo: FirestoreRepository? = null
) {
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    fun getLogs(): Flow<List<OutfitLog>> = store.outfitLogsFlow

    suspend fun save(log: OutfitLog) {
        store.saveOutfitLog(log)
        uid?.let { firestoreRepo?.saveOutfitLog(it, log) }
    }

    suspend fun delete(date: String) {
        store.deleteOutfitLog(date)
        uid?.let { firestoreRepo?.deleteOutfitLog(it, date) }
    }
}
