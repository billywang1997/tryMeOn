package com.trymeon.app.data.remote

/**
 * One product as every shopping surface in the app displays it.
 *
 * The name is historical — it began as an eBay search result — and it now
 * carries rows from whatever source answered, converted to the buyer's currency
 * by [com.trymeon.app.data.sourcing.asProductRow]. It outlived the eBay, ASOS
 * and Vinted services it was defined beside, which is why it lives here now.
 *
 * [price] is the landed total, not a sticker price: showing the sticker is what
 * makes an overseas purchase look like a bargain right up until it arrives.
 */
data class EbayItem(
    val itemId: String = "",
    val title: String = "",
    val price: String = "",
    val currency: String = "AUD",
    val condition: String = "",
    val imageUrl: String = "",
    val itemWebUrl: String = "",
    /** The marketplace this came from, as shown to the user. */
    val source: String = ""
)
