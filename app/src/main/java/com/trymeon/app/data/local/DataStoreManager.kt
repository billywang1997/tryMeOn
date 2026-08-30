package com.trymeon.app.data.local

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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
        dtos.mapNotNull { it.toDomain() }
    }

    suspend fun addClothing(item: ClothingItem): Long {
        var assignedId = 0L
        context.dataStore.edit { prefs ->
            val stored = readStoredClothing(prefs)
            val current = stored.readable.toMutableList()
            val nextId = (prefs[KEY_NEXT_ID]?.toLongOrNull() ?: 1L)
            assignedId = nextId
            current.add(0, item.copy(id = nextId))
            writeClothing(prefs, current, stored.unreadable)
            prefs[KEY_NEXT_ID] = (nextId + 1).toString()
        }
        return assignedId
    }

    suspend fun deleteClothing(itemId: Long) {
        context.dataStore.edit { prefs ->
            val stored = readStoredClothing(prefs)
            writeClothing(prefs, stored.readable.filter { it.id != itemId }, stored.unreadable)
        }
    }

    suspend fun updateClothing(item: ClothingItem) {
        context.dataStore.edit { prefs ->
            val stored = readStoredClothing(prefs)
            writeClothing(
                prefs,
                stored.readable.map { if (it.id == item.id) item else it },
                stored.unreadable
            )
        }
    }

    suspend fun toggleFavorite(itemId: Long) {
        context.dataStore.edit { prefs ->
            val stored = readStoredClothing(prefs)
            writeClothing(
                prefs,
                stored.readable.map {
                    if (it.id == itemId) it.copy(isFavorite = !it.isFavorite) else it
                },
                stored.unreadable
            )
        }
    }

    suspend fun getAllClothingOnce(): List<ClothingItem> {
        var result = emptyList<ClothingItem>()
        context.dataStore.edit { prefs ->
            result = readClothingList(prefs)
        }
        return result
    }

    private fun readClothingList(prefs: Preferences): List<ClothingItem> =
        readStoredClothing(prefs).readable

    /**
     * The stored wardrobe, split into what this build can read and what it
     * cannot.
     *
     * Every write here is a read-modify-write over the whole list, so a row
     * that fails to convert has to survive the round trip explicitly. Drop it
     * on read and adding one garment silently deletes another.
     */
    private fun readStoredClothing(prefs: Preferences): StoredClothing {
        val json = prefs[KEY_CLOTHING] ?: return StoredClothing(emptyList(), emptyList())
        val type = object : TypeToken<List<ClothingItemDto>>() {}.type
        val dtos: List<ClothingItemDto> = gson.fromJson(json, type) ?: emptyList()
        val readable = mutableListOf<ClothingItem>()
        val unreadable = mutableListOf<ClothingItemDto>()
        dtos.forEach { dto -> dto.toDomain()?.let(readable::add) ?: unreadable.add(dto) }
        return StoredClothing(readable, unreadable)
    }

    /** Serialises [items] without losing rows this build could not interpret. */
    private fun writeClothing(prefs: MutablePreferences, items: List<ClothingItem>, kept: List<ClothingItemDto>) {
        prefs[KEY_CLOTHING] = gson.toJson(items.map { it.toDto() } + kept)
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

    // --- Raw access, for tests that need to plant a record this build did not
    // write. Reading storage as it actually is, rather than as the current
    // model describes it, is the only way to check backward compatibility.

    @VisibleForTesting
    suspend fun readRawClothingJson(): String {
        var json = "[]"
        context.dataStore.edit { json = it[KEY_CLOTHING] ?: "[]" }
        return json
    }

    @VisibleForTesting
    suspend fun writeRawClothingJson(json: String) {
        context.dataStore.edit { it[KEY_CLOTHING] = json }
    }

    @VisibleForTesting
    suspend fun readRawWishlistJson(): String {
        var json = "[]"
        context.dataStore.edit { json = it[KEY_WISHLIST] ?: "[]" }
        return json
    }

    @VisibleForTesting
    suspend fun writeRawWishlistJson(json: String) {
        context.dataStore.edit { it[KEY_WISHLIST] = json }
    }

    // --- Wishlist ---
    val wishlistFlow: Flow<List<WishlistItem>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_WISHLIST] ?: return@map emptyList()
        readWishlist(json)
    }

    /**
     * Reads stored wishlist JSON, filling in fields an older build never wrote.
     *
     * Gson does not run Kotlin constructor defaults: a field absent from the
     * JSON is left null even though the declared type is non-null, and nothing
     * checks it again. The screen renders `source.uppercase()`, so a record
     * saved before `source` existed crashes the wishlist rather than showing an
     * unlabelled row. Every other model here goes through a DTO for the same
     * reason; this one is coerced in place.
     */
    private fun readWishlist(json: String): List<WishlistItem> {
        val type = object : TypeToken<List<WishlistItem>>() {}.type
        val raw: List<WishlistItem> = gson.fromJson(json, type) ?: return emptyList()
        return raw.map { it.withDefaults() }
    }

    suspend fun saveWishlistItem(item: WishlistItem) {
        context.dataStore.edit { prefs ->
            // Coerced on the way in, so a legacy record is repaired rather
            // than written back out with its nulls intact.
            val current = readWishlist(prefs[KEY_WISHLIST] ?: "[]").toMutableList()
            current.removeAll { it.id == item.id }
            current.add(0, item)
            prefs[KEY_WISHLIST] = gson.toJson(current)
        }
    }

    suspend fun removeWishlistItem(itemId: String) {
        context.dataStore.edit { prefs ->
            // Coerced on the way in, so a legacy record is repaired rather
            // than written back out with its nulls intact.
            val current = readWishlist(prefs[KEY_WISHLIST] ?: "[]").toMutableList()
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
/**
 * Replaces nulls Gson left behind with the defaults the constructor declares.
 *
 * Written out field by field because that is the only way to be sure: a
 * reflective pass would have to guess which nulls were meant.
 */
@Suppress("USELESS_ELVIS")
private fun WishlistItem.withDefaults() = copy(
    title = title ?: "",
    imageUrl = imageUrl ?: "",
    itemWebUrl = itemWebUrl ?: "",
    source = source ?: "",
    currency = currency ?: "AUD",
    savedPrice = savedPrice ?: "",
    lastSeenPrice = lastSeenPrice ?: "",
    query = query ?: ""
)

/** A stored wardrobe as this build sees it; see [DataStoreManager.readStoredClothing]. */
private data class StoredClothing(
    val readable: List<ClothingItem>,
    val unreadable: List<ClothingItemDto>
)

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
    /**
     * Null when the row cannot be read as a garment.
     *
     * Categories are stored by enum name, and `valueOf` throws on one this
     * build does not have — a downgrade, or a sync from a newer device. The
     * read maps over the whole list, so an exception here costs the user their
     * entire wardrobe to spare them one unreadable row. Dropping the row is
     * the smaller loss and the recoverable one.
     */
    fun toDomain(): ClothingItem? {
        val known = ClothingCategory.entries.firstOrNull { it.name == category } ?: return null
        return ClothingItem(
            id = id,
            imagePath = imagePath ?: "",
            category = known,
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
