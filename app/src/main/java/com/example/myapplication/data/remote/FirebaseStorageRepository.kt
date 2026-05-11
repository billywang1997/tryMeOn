package com.example.myapplication.data.remote

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

class FirebaseStorageRepository {

    private val storage = FirebaseStorage.getInstance()
    private fun ref(path: String) = storage.reference.child(path)

    suspend fun uploadClothingImage(uid: String, itemId: Long, localPath: String): Result<String> = runCatching {
        val file = File(localPath)
        if (!file.exists()) return@runCatching ""
        val r = ref("users/$uid/wardrobe/$itemId.jpg")
        r.putFile(Uri.fromFile(file)).await()
        r.downloadUrl.await().toString()
    }

    suspend fun deleteClothingImage(uid: String, itemId: Long) = runCatching {
        ref("users/$uid/wardrobe/$itemId.jpg").delete().await()
    }

    suspend fun uploadProfilePhoto(uid: String, type: String, localPath: String): Result<String> = runCatching {
        val file = File(localPath)
        if (!file.exists()) return@runCatching ""
        val r = ref("users/$uid/profile/$type.jpg")
        r.putFile(Uri.fromFile(file)).await()
        r.downloadUrl.await().toString()
    }

    /** Uploads a local file. If path is already an https URL, returns it unchanged. */
    suspend fun uploadSavedImage(uid: String, imageId: Long, localPath: String): Result<String> = runCatching {
        if (localPath.startsWith("http")) return@runCatching localPath
        val file = File(localPath)
        if (!file.exists()) return@runCatching ""
        val r = ref("users/$uid/saved/$imageId.jpg")
        r.putFile(Uri.fromFile(file)).await()
        r.downloadUrl.await().toString()
    }

    suspend fun deleteSavedImage(uid: String, imageId: Long) = runCatching {
        ref("users/$uid/saved/$imageId.jpg").delete().await()
    }
}
