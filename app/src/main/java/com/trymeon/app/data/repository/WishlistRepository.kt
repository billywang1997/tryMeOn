package com.trymeon.app.data.repository

import com.trymeon.app.data.local.DataStoreManager
import com.trymeon.app.data.remote.EbayItem
import com.trymeon.app.data.remote.FirestoreRepository
import com.trymeon.app.domain.model.WishlistItem
import com.trymeon.app.data.auth.CloudIdentity
import kotlinx.coroutines.flow.Flow

class WishlistRepository(
    private val store: DataStoreManager,
    private val firestoreRepo: FirestoreRepository? = null
) {
    // Null when there is no cloud, rather than throwing: sync is optional
    // and every path below already handles not having a uid.
    private val uid get() = CloudIdentity.uid()

    fun observe(): Flow<List<WishlistItem>> = store.wishlistFlow

    suspend fun add(item: EbayItem, query: String = "") {
        val w = item.toWishlist(query)
        store.saveWishlistItem(w)
        uid?.let { firestoreRepo?.saveWishlistItem(it, w) }
    }

    suspend fun remove(id: String) {
        store.removeWishlistItem(id)
        uid?.let { firestoreRepo?.deleteWishlistItem(it, id) }
    }

    suspend fun update(item: WishlistItem) {
        store.updateWishlistItem(item)
        uid?.let { firestoreRepo?.saveWishlistItem(it, item) }
    }

    companion object {
        fun EbayItem.toWishlist(query: String = ""): WishlistItem {
            val safeId = itemId.ifBlank { "${source}_${title.hashCode()}_${itemWebUrl.hashCode()}" }
            return WishlistItem(
                id = safeId,
                title = title,
                imageUrl = imageUrl,
                itemWebUrl = itemWebUrl,
                source = source,
                currency = currency,
                savedPrice = price,
                lastSeenPrice = price,
                lastCheckedAt = System.currentTimeMillis(),
                query = query
            )
        }
    }
}
