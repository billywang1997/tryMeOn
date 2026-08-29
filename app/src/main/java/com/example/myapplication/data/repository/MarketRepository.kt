package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.domain.model.MarketListing
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "MarketRepo"
private const val COL = "market_listings"

class MarketRepository {
    private val db = FirebaseFirestore.getInstance()

    /** All active listings, newest first. */
    fun observeAll(): Flow<List<MarketListing>> = callbackFlow {
        val reg = db.collection(COL)
            .whereEqualTo("sold", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(60)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "observe error: ${err.message}"); return@addSnapshotListener }
                val items = snap?.documents?.mapNotNull { it.toObject(MarketListing::class.java) }.orEmpty()
                trySend(items)
            }
        awaitClose { reg.remove() }
    }

    fun observeMine(uid: String): Flow<List<MarketListing>> = callbackFlow {
        val reg = db.collection(COL)
            .whereEqualTo("sellerUid", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "observeMine error: ${err.message}"); return@addSnapshotListener }
                val items = snap?.documents?.mapNotNull { it.toObject(MarketListing::class.java) }.orEmpty()
                trySend(items)
            }
        awaitClose { reg.remove() }
    }

    suspend fun post(listing: MarketListing): Result<String> = runCatching {
        val ref = db.collection(COL).document()
        val toSave = listing.copy(id = ref.id)
        ref.set(toSave).await()
        ref.id
    }

    suspend fun markSold(id: String): Result<Unit> = runCatching {
        db.collection(COL).document(id).update("sold", true).await()
        Unit
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        db.collection(COL).document(id).delete().await()
        Unit
    }
}
