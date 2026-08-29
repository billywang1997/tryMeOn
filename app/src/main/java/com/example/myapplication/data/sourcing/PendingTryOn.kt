package com.example.myapplication.data.sourcing

import com.example.myapplication.data.remote.EbayItem
import com.example.myapplication.data.remote.TaobaoItem
import com.example.myapplication.domain.model.ClothingCategory

/**
 * A garment handed from the sourcing tab to the try-on tab.
 *
 * The combination is the point: Taobao has its own AI try-on and does not offer
 * it to overseas buyers, and nothing that quotes a landed price will also show
 * you wearing the thing. Joining the two is the one move neither side can copy.
 *
 * A holder rather than a navigation argument because the payload is an image URL
 * plus a Chinese title — long, and full of characters that make a mess of a
 * route string. It is consumed once so a later visit to the tab starts clean.
 */
object PendingTryOn {

    data class Garment(
        val item: EbayItem,
        val category: ClothingCategory
    )

    @Volatile
    private var pending: Garment? = null

    fun offer(item: TaobaoItem, category: ClothingCategory, priceCny: Double, landedAud: Double) {
        pending = Garment(
            item = EbayItem(
                itemId = item.itemId,
                title = item.title,
                // Landed, not the sticker: the sticker is the number that misleads.
                price = "%.2f".format(landedAud),
                currency = "AUD",
                imageUrl = item.imageUrl,
                itemWebUrl = item.itemUrl,
                source = "Taobao"
            ),
            category = category
        )
    }

    /** Returns the waiting garment, if any, and clears it. */
    fun consume(): Garment? = pending.also { pending = null }

    fun clear() { pending = null }
}
