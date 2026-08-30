package com.trymeon.app.ui.components

import com.trymeon.app.data.remote.EbayItem

/**
 * How a product row's price should read on a shopping strip.
 *
 * These strips show one number where the sourcing screen shows a ledger, and
 * the number is the same one: the landed total, freight and tax included. Read
 * bare it is ambiguous — a shopper cannot tell it from a sticker price, which
 * is the exact confusion the whole cost engine exists to remove. One word of
 * context costs nothing and makes the two presentations agree.
 */
fun EbayItem.landedLabel(): String = when {
    price.isBlank() -> ""
    // A row from outside the sourcing pipeline has no landed claim to make.
    currency != "AUD" -> "$currency $price"
    else -> "A$$price delivered"
}
