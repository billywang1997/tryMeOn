package com.example.myapplication.domain.sourcing

import com.example.myapplication.domain.model.ClothingCategory

/**
 * Nobody shopping for a jacket knows its packed dimensions, but freight is
 * priced on exactly that. These presets let the landed cost be quoted from a
 * category alone, and get overwritten whenever the AI or the listing gives us
 * something better.
 *
 * Figures are for a single garment as a forwarder would compress and bag it.
 * They are estimates and are meant to be tuned against real invoices.
 */
object ParcelPresets {

    fun forCategory(category: ClothingCategory, quantity: Int = 1): Parcel = when (category) {
        ClothingCategory.INNER     -> parcel(28.0, 22.0, 5.0, 300, quantity)
        ClothingCategory.OUTERWEAR -> parcel(35.0, 28.0, 12.0, 900, quantity)
        ClothingCategory.PANTS     -> parcel(30.0, 24.0, 7.0, 500, quantity)
        ClothingCategory.DRESS     -> parcel(32.0, 26.0, 8.0, 450, quantity)
        ClothingCategory.SHOES     -> parcel(33.0, 22.0, 13.0, 1000, quantity)
        ClothingCategory.ACCESSORY -> parcel(20.0, 15.0, 5.0, 200, quantity)
        ClothingCategory.BAG       -> parcel(35.0, 28.0, 15.0, 800, quantity)
    }

    /**
     * Extra units stack rather than duplicating the parcel: two shirts in one
     * bag are thicker, not twice as long. Getting this wrong is what makes a
     * multi-item quote look absurd.
     */
    private fun parcel(l: Double, w: Double, h: Double, grams: Int, quantity: Int): Parcel {
        val n = quantity.coerceAtLeast(1)
        return Parcel(
            lengthCm = l,
            widthCm = w,
            heightCm = h * n,
            actualGrams = grams * n
        )
    }
}
