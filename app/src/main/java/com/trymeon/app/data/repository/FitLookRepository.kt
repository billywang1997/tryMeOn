package com.trymeon.app.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.trymeon.app.domain.model.FitLook
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File

private const val TAG = "FitLookRepo"
private const val COL = "fit_looks"

/**
 * Shared fit looks: everyone's to read, each one only its wearer's to write.
 *
 * Same shape as [MarketRepository], and resolved on use for the same reason —
 * a phone with no Firebase should lose this strip, not the app. One recent
 * page is read and matched on the device rather than queried by body, which
 * keeps it to a single ordered index and lets the matching rule live in
 * plain, testable Kotlin.
 */
class FitLookRepository {

    private val db: FirebaseFirestore?
        get() = runCatching { FirebaseFirestore.getInstance() }.getOrNull()

    private val storage: FirebaseStorage?
        get() = runCatching { FirebaseStorage.getInstance() }.getOrNull()

    /** The most recent shared looks, newest first. Empty without a cloud. */
    fun observeRecent(limit: Long = 300): Flow<List<FitLook>> = callbackFlow {
        val store = db ?: run { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        val reg = store.collection(COL)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "observe error: ${err.message}"); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull { it.toObject(FitLook::class.java) }.orEmpty())
            }
        awaitClose { reg.remove() }
    }

    fun observeMine(uid: String): Flow<List<FitLook>> = callbackFlow {
        val store = db ?: run { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        val reg = store.collection(COL)
            .whereEqualTo("uid", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "observeMine error: ${err.message}"); return@addSnapshotListener }
                trySend(
                    snap?.documents?.mapNotNull { it.toObject(FitLook::class.java) }.orEmpty()
                        .sortedByDescending { it.createdAt }
                )
            }
        awaitClose { reg.remove() }
    }

    /**
     * Uploads the image and publishes the look. [localImage] may already be an
     * https URL, in which case it is used as is.
     */
    suspend fun post(look: FitLook, localImage: String): Result<String> = runCatching {
        val store = db ?: error("Sharing a fit needs a signed-in cloud account")
        val ref = store.collection(COL).document()
        val url = if (localImage.startsWith("http")) localImage else {
            val file = File(localImage)
            if (!file.exists()) error("The image is no longer on this phone")
            val bucket = storage ?: error("Sharing a fit needs a signed-in cloud account")
            val r = bucket.reference.child("fit_looks/${look.uid}/${ref.id}.jpg")
            r.putFile(Uri.fromFile(file)).await()
            r.downloadUrl.await().toString()
        }
        ref.set(look.copy(id = ref.id, imageUrl = url, keywords = FitLook.keywordsOf(look.garment))).await()
        ref.id
    }

    suspend fun delete(look: FitLook): Result<Unit> = runCatching {
        val store = db ?: error("No cloud")
        store.collection(COL).document(look.id).delete().await()
        // Best effort: a stale image with no document pointing at it is unreachable anyway.
        runCatching { storage?.reference?.child("fit_looks/${look.uid}/${look.id}.jpg")?.delete()?.await() }
        Unit
    }
}
