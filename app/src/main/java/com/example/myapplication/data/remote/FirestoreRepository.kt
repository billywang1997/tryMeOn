package com.example.myapplication.data.remote

import com.example.myapplication.domain.model.ClothingCategory
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.domain.model.OutfitLog
import com.example.myapplication.domain.model.SavedImage
import com.example.myapplication.domain.model.UserProfile
import com.example.myapplication.domain.model.WishlistItem
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    // ── User meta ────────────────────────────────────────────────────────

    suspend fun saveUserMeta(
        uid: String,
        displayName: String?,
        email: String?,
        isAnonymous: Boolean,
        styles: Set<String>
    ) = runCatching {
        userDoc(uid).set(
            mapOf(
                "displayName" to (displayName ?: ""),
                "email" to (email ?: ""),
                "isAnonymous" to isAnonymous,
                "styles" to styles.toList(),
                "updatedAt" to com.google.firebase.Timestamp.now()
            ),
            SetOptions.merge()
        ).await()
    }

    // ── Profile ──────────────────────────────────────────────────────────

    suspend fun saveProfile(uid: String, profile: UserProfile) = runCatching {
        userDoc(uid).collection("data").document("profile").set(
            mapOf(
                "gender"       to profile.gender,
                "height"       to profile.height,
                "weight"       to profile.weight,
                "bust"         to profile.bust,
                "waist"        to profile.waist,
                "hips"         to profile.hips,
                "faceImageUrl" to profile.faceImagePath,
                "bodyImageUrl" to profile.bodyImagePath,
                "updatedAt"    to com.google.firebase.Timestamp.now()
            )
        ).await()
    }

    suspend fun loadProfile(uid: String): UserProfile? = runCatching {
        val snap = userDoc(uid).collection("data").document("profile").get().await()
        if (!snap.exists()) return@runCatching null
        UserProfile(
            gender        = snap.getString("gender") ?: "",
            height        = (snap.getLong("height") ?: 0).toInt(),
            weight        = (snap.getLong("weight") ?: 0).toInt(),
            bust          = (snap.getLong("bust") ?: 0).toInt(),
            waist         = (snap.getLong("waist") ?: 0).toInt(),
            hips          = (snap.getLong("hips") ?: 0).toInt(),
            faceImagePath = snap.getString("faceImageUrl") ?: "",
            bodyImagePath = snap.getString("bodyImageUrl") ?: ""
        )
    }.getOrNull()

    // ── Wardrobe ─────────────────────────────────────────────────────────

    suspend fun saveWardrobeItem(uid: String, item: ClothingItem) = runCatching {
        userDoc(uid).collection("wardrobe").document(item.id.toString()).set(
            mapOf(
                "id"            to item.id,
                "imagePath"     to item.imagePath,
                "cloudImageUrl" to item.cloudImageUrl,
                "category"      to item.category.name,
                "name"          to item.name,
                "color"         to item.color,
                "brand"         to item.brand,
                "notes"         to item.notes,
                "price"         to item.price,
                "isFavorite"    to item.isFavorite,
                "createdAt"     to item.createdAt
            )
        ).await()
    }

    suspend fun deleteWardrobeItem(uid: String, itemId: Long) = runCatching {
        userDoc(uid).collection("wardrobe").document(itemId.toString()).delete().await()
    }

    suspend fun loadWardrobe(uid: String): List<ClothingItem> = runCatching {
        userDoc(uid).collection("wardrobe").get().await().documents.mapNotNull { doc ->
            runCatching {
                val cloudUrl = doc.getString("cloudImageUrl") ?: ""
                val localPath = doc.getString("imagePath") ?: ""
                ClothingItem(
                    id            = doc.getLong("id") ?: 0,
                    imagePath     = if (cloudUrl.isNotEmpty()) cloudUrl else localPath,
                    cloudImageUrl = cloudUrl,
                    category      = runCatching { ClothingCategory.valueOf(doc.getString("category") ?: "INNER") }.getOrDefault(ClothingCategory.INNER),
                    name          = doc.getString("name") ?: "",
                    color         = doc.getString("color") ?: "",
                    brand         = doc.getString("brand") ?: "",
                    notes         = doc.getString("notes") ?: "",
                    price         = doc.getDouble("price") ?: 0.0,
                    isFavorite    = doc.getBoolean("isFavorite") ?: false,
                    createdAt     = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    // ── Outfit history ───────────────────────────────────────────────────

    suspend fun saveOutfitLog(uid: String, log: OutfitLog) = runCatching {
        userDoc(uid).collection("history").document(log.date).set(
            mapOf(
                "date"    to log.date,
                "itemIds" to log.itemIds,
                "note"    to log.note
            )
        ).await()
    }

    suspend fun deleteOutfitLog(uid: String, date: String) = runCatching {
        userDoc(uid).collection("history").document(date).delete().await()
    }

    suspend fun loadHistory(uid: String): List<OutfitLog> = runCatching {
        userDoc(uid).collection("history").get().await().documents.mapNotNull { doc ->
            runCatching {
                @Suppress("UNCHECKED_CAST")
                OutfitLog(
                    date    = doc.getString("date") ?: doc.id,
                    itemIds = (doc.get("itemIds") as? List<Long>) ?: emptyList(),
                    note    = doc.getString("note") ?: ""
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    // ── Saved images (OOTD boards, try-on results) ───────────────────────

    suspend fun saveSavedImage(uid: String, image: SavedImage) = runCatching {
        userDoc(uid).collection("saved").document(image.id.toString()).set(
            mapOf(
                "id"        to image.id,
                "path"      to image.path,
                "type"      to image.type,
                "label"     to image.label,
                "note"      to image.note,
                "createdAt" to image.createdAt
            )
        ).await()
    }

    suspend fun deleteSavedImage(uid: String, imageId: Long) = runCatching {
        userDoc(uid).collection("saved").document(imageId.toString()).delete().await()
    }

    suspend fun loadSavedImages(uid: String): List<SavedImage> = runCatching {
        userDoc(uid).collection("saved").get().await().documents.mapNotNull { doc ->
            runCatching {
                SavedImage(
                    id        = doc.getLong("id") ?: 0,
                    path      = doc.getString("path") ?: "",
                    type      = doc.getString("type") ?: "look",
                    label     = doc.getString("label") ?: "",
                    note      = doc.getString("note") ?: "",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    // ── Wishlist ─────────────────────────────────────────────────────────

    // Firestore document IDs cannot contain '/'. WishlistItem.id is derived from
    // shop item ids which are otherwise valid, so only '/' needs sanitizing.
    private fun wishlistDocId(rawId: String) = rawId.replace("/", "_")

    suspend fun saveWishlistItem(uid: String, item: WishlistItem) = runCatching {
        userDoc(uid).collection("wishlist").document(wishlistDocId(item.id)).set(item).await()
    }

    suspend fun deleteWishlistItem(uid: String, id: String) = runCatching {
        userDoc(uid).collection("wishlist").document(wishlistDocId(id)).delete().await()
    }

    suspend fun loadWishlist(uid: String): List<WishlistItem> = runCatching {
        userDoc(uid).collection("wishlist").get().await().documents.mapNotNull { doc ->
            runCatching { doc.toObject(WishlistItem::class.java) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    // ── Style preferences ─────────────────────────────────────────────────

    suspend fun loadStyles(uid: String): Set<String> = runCatching {
        @Suppress("UNCHECKED_CAST")
        (userDoc(uid).get().await().get("styles") as? List<String>)?.toSet() ?: emptySet()
    }.getOrDefault(emptySet())
}
