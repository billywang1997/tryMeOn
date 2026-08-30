package com.trymeon.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.model.OutfitLog
import com.trymeon.app.domain.model.SavedImage
import com.trymeon.app.domain.model.UserProfile
import com.trymeon.app.domain.model.WishlistItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wardrobe_store")

class DataStoreManager(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val KEY_CLOTHING     = stringPreferencesKey("clothing_items")
        private val KEY_PROFILE      = stringPreferencesKey("user_profile")
        private val KEY_NEXT_ID      = stringPreferencesKey("next_id")
        private val KEY_OUTFIT_LOGS  = stringPreferencesKey("outfit_logs")
        private val KEY_SAVED_IMAGES = stringPreferencesKey("saved_images")
        private val KEY_WISHLIST     = stringPreferencesKey("wishlist_items")
    }

    // --- Clothing ---
    val clothingFlow: Flow<List<ClothingItem>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_CLOTHING] ?: return@map emptyList()
        val type = object : TypeToken<List<ClothingItemDto>>() {}.type
        val dtos: List<ClothingItemDto> = gson.fromJson(json, type) ?: emptyList()
        dtos.map { it.toDomain() }
    }

    suspend fun addClothing(item: ClothingItem): Long {
        var assignedId = 0L
        context.dataStore.edit { prefs ->
            val current = readClothingList(prefs).toMutableList()
            val nextId = (prefs[KEY_NEXT_ID]?.toLongOrNull() ?: 1L)
            assignedId = nextId
            current.add(0, item.copy(id = nextId))
            prefs[KEY_CLOTHING] = gson.toJson(current.map { it.toDto() })
            prefs[KEY_NEXT_ID] = (nextId + 1).toString()
        }
        return assignedId
    }

    suspend fun deleteClothing(itemId: Long) {
        context.dataStore.edit { prefs ->
            val current = readClothingList(prefs).filter { it.id != itemId }
            prefs[KEY_CLOTHING] = gson.toJson(current.map { it.toDto() })
        }
    }

    suspend fun updateClothing(item: ClothingItem) {
        context.dataStore.edit { prefs ->
            val current = readClothingList(prefs).map { if (it.id == item.id) item else it }
            prefs[KEY_CLOTHING] = gson.toJson(current.map { it.toDto() })
        }
    }

    suspend fun toggleFavorite(itemId: Long) {
        context.dataStore.edit { prefs ->
            val current = readClothingList(prefs).map {
                if (it.id == itemId) it.copy(isFavorite = !it.isFavorite) else it
            }
            prefs[KEY_CLOTHING] = gson.toJson(current.map { it.toDto() })
        }
    }

    suspend fun getAllClothingOnce(): List<ClothingItem> {
        var result = emptyList<ClothingItem>()
        context.dataStore.edit { prefs ->
            result = readClothingList(prefs)
        }
        return result
    }

    private fun readClothingList(prefs: Preferences): List<ClothingItem> {
        val json = prefs[KEY_CLOTHING] ?: return emptyList()
        val type = object : TypeToken<List<ClothingItemDto>>() {}.type
        val dtos: List<ClothingItemDto> = gson.fromJson(json, type) ?: emptyList()
        return dtos.map { it.toDomain() }
    }

    // --- Profile ---
    val profileFlow: Flow<UserProfile?> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_PROFILE] ?: return@map null
        gson.fromJson(json, UserProfileDto::class.java)?.toDomain()
    }

    suspend fun saveProfile(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PROFILE] = gson.toJson(profile.toDto())
        }
    }

    // --- Outfit Logs ---
    val outfitLogsFlow: Flow<List<OutfitLog>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_OUTFIT_LOGS] ?: return@map emptyList()
        val type = object : TypeToken<List<OutfitLogDto>>() {}.type
        val dtos: List<OutfitLogDto> = gson.fromJson(json, type) ?: emptyList()
        dtos.map { OutfitLog(it.date, it.itemIds, it.note) }
    }

    suspend fun saveOutfitLog(log: OutfitLog) {
        context.dataStore.edit { prefs ->
            val type = object : TypeToken<List<OutfitLogDto>>() {}.type
            val current: MutableList<OutfitLogDto> =
                gson.fromJson(prefs[KEY_OUTFIT_LOGS] ?: "[]", type) ?: mutableListOf()
            current.removeIf { it.date == log.date }
            current.add(OutfitLogDto(log.date, log.itemIds, log.note))
            prefs[KEY_OUTFIT_LOGS] = gson.toJson(current)
        }
    }

    suspend fun deleteOutfitLog(date: String) {
        context.dataStore.edit { prefs ->
            val type = object : TypeToken<List<OutfitLogDto>>() {}.type
            val current: MutableList<OutfitLogDto> =
                gson.fromJson(prefs[KEY_OUTFIT_LOGS] ?: "[]", type) ?: mutableListOf()
            current.removeIf { it.date == date }
            prefs[KEY_OUTFIT_LOGS] = gson.toJson(current)
        }
    }

    // --- Saved Images ---
    val savedImagesFlow: Flow<List<SavedImage>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_SAVED_IMAGES] ?: return@map emptyList()
        val type = object : TypeToken<List<SavedImageDto>>() {}.type
        val dtos: List<SavedImageDto> = gson.fromJson(json, type) ?: emptyList()
        dtos.mapNotNull { dto ->
            val path = dto.path ?: return@mapNotNull null
            SavedImage(dto.id, path, dto.type ?: "look", dto.label ?: "", dto.note ?: "", dto.createdAt)
        }
    }

    suspend fun saveImage(image: SavedImage) {
        context.dataStore.edit { prefs ->
            val type = object : TypeToken<List<SavedImageDto>>() {}.type
            val current: MutableList<SavedImageDto> =
                gson.fromJson(prefs[KEY_SAVED_IMAGES] ?: "[]", type) ?: mutableListOf()
            current.add(0, SavedImageDto(image.id, image.path, image.type, image.label, image.note, image.createdAt))
            prefs[KEY_SAVED_IMAGES] = gson.toJson(current)
        }
    }

    suspend fun deleteSavedImage(imageId: Long) {
        context.dataStore.edit { prefs ->
            val type = object : TypeToken<List<SavedImageDto>>() {}.type
            val current: MutableList<SavedImageDto> =
                gson.fromJson(prefs[KEY_SAVED_IMAGES] ?: "[]", type) ?: mutableListOf()
            current.removeIf { it.id == imageId }
            prefs[KEY_SAVED_IMAGES] = gson.toJson(current)
        }
    }

    /**
     * Generated try-on / outfit images are written to the volatile [Context.getCacheDir],
     * which the OS may purge at any time. Copy such a file into persistent [Context.getFilesDir]
     * so saved looks survive cache eviction. No-op for remote URLs or already-persistent paths.
     */
    fun persistLookImage(path: String): String {
        if (!path.startsWith("/")) return path  // remote URL
        val src = File(path)
        if (!src.exists()) return path
        if (!src.absolutePath.startsWith(context.cacheDir.absolutePath)) return path  // already persistent
        return try {
            val dir = File(context.filesDir, "saved_looks").apply { mkdirs() }
            val dest = File(dir, src.name)
            src.copyTo(dest, overwrite = true)
            dest.absolutePath
        } catch (e: Exception) {
            path
        }
    }

    // --- Wishlist ---
    val wishlistFlow: Flow<List<WishlistItem>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_WISHLIST] ?: return@map emptyList()
        val type = object : TypeToken<List<WishlistItem>>() {}.type
        gson.fromJson<List<WishlistItem>>(json, type) ?: emptyList()
    }

    suspend fun saveWishlistItem(item: WishlistItem) {
        context.dataStore.edit { prefs ->
            val type = object : TypeToken<List<WishlistItem>>() {}.type
            val current: MutableList<WishlistItem> =
                gson.fromJson(prefs[KEY_WISHLIST] ?: "[]", type) ?: mutableListOf()
            current.removeAll { it.id == item.id }
            current.add(0, item)
            prefs[KEY_WISHLIST] = gson.toJson(current)
        }
    }

    suspend fun removeWishlistItem(itemId: String) {
        context.dataStore.edit { prefs ->
            val type = object : TypeToken<List<WishlistItem>>() {}.type
            val current: MutableList<WishlistItem> =
                gson.fromJson(prefs[KEY_WISHLIST] ?: "[]", type) ?: mutableListOf()
            current.removeAll { it.id == itemId }
            prefs[KEY_WISHLIST] = gson.toJson(current)
        }
    }

    suspend fun updateWishlistItem(item: WishlistItem) = saveWishlistItem(item)

    suspend fun importWishlist(items: List<WishlistItem>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WISHLIST] = gson.toJson(items)
        }
    }

    // --- Bulk import (restore from cloud) ---

    suspend fun importWardrobe(items: List<ClothingItem>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CLOTHING] = gson.toJson(items.map { it.toDto() })
            val maxId = items.maxOfOrNull { it.id } ?: 0L
            prefs[KEY_NEXT_ID] = (maxId + 1).toString()
        }
    }

    suspend fun importOutfitLogs(logs: List<OutfitLog>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_OUTFIT_LOGS] = gson.toJson(logs.map { OutfitLogDto(it.date, it.itemIds, it.note) })
        }
    }

    suspend fun importSavedImages(images: List<SavedImage>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SAVED_IMAGES] = gson.toJson(images.map { SavedImageDto(it.id, it.path, it.type, it.label, it.note, it.createdAt) })
        }
    }

    suspend fun importProfile(profile: UserProfile) = saveProfile(profile)

    suspend fun getProfileOnce(): UserProfile? {
        var result: UserProfile? = null
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_PROFILE] ?: return@edit
            result = gson.fromJson(json, UserProfileDto::class.java)?.toDomain()
        }
        return result
    }
}

// --- DTOs ---
private data class ClothingItemDto(
    val id: Long,
    val imagePath: String,
    val category: String,
    val name: String?,
    val color: String?,
    val brand: String?,
    val notes: String?,
    val price: Double = 0.0,
    val createdAt: Long,
    val isFavorite: Boolean = false,
    val cloudImageUrl: String? = null
) {
    fun toDomain() = ClothingItem(
        id = id,
        imagePath = imagePath ?: "",
        category = ClothingCategory.valueOf(category),
        name = name ?: "",
        color = color ?: "",
        brand = brand ?: "",
        notes = notes ?: "",
        price = price,
        createdAt = createdAt,
        isFavorite = isFavorite,
        cloudImageUrl = cloudImageUrl ?: ""
    )
}

private data class SavedImageDto(
    val id: Long,
    val path: String?,
    val type: String? = null,
    val label: String? = null,
    val note: String? = null,
    val createdAt: Long
)

private data class UserProfileDto(
    val id: Long,
    val gender: String,
    val height: Int,
    val weight: Int,
    val bust: Int,
    val waist: Int,
    val hips: Int,
    val faceImagePath: String,
    val bodyImagePath: String
) {
    fun toDomain() = UserProfile(id, gender, height, weight, bust, waist, hips, faceImagePath, bodyImagePath)
}

private data class OutfitLogDto(val date: String, val itemIds: List<Long>, val note: String)

private fun ClothingItem.toDto() = ClothingItemDto(id, imagePath, category.name, name, color, brand, notes, price, createdAt, isFavorite, cloudImageUrl)
private fun UserProfile.toDto() = UserProfileDto(id, gender, height, weight, bust, waist, hips, faceImagePath, bodyImagePath)
