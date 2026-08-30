package com.trymeon.app.data.repository

import com.trymeon.app.data.local.DataStoreManager
import com.trymeon.app.data.remote.FirestoreRepository
import com.trymeon.app.domain.model.OutfitLog
import com.trymeon.app.data.auth.CloudIdentity
import kotlinx.coroutines.flow.Flow

class OutfitLogRepository(
    private val store: DataStoreManager,
    private val firestoreRepo: FirestoreRepository? = null
) {
    private val uid get() = CloudIdentity.uid()

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
