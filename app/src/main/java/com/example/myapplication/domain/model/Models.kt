package com.example.myapplication.domain.model

enum class ClothingCategory(val label: String) {
    INNER("Top"),
    OUTERWEAR("Outerwear"),
    PANTS("Bottoms"),
    DRESS("Dress"),
    SHOES("Shoes"),
    ACCESSORY("Accessory"),
    BAG("Bag")
}

enum class Mood(val emoji: String, val label: String) {
    HAPPY("😊", "Happy"),
    TIRED("😴", "Tired"),
    ENERGETIC("💪", "Energetic"),
    ROMANTIC("🥰", "Romantic"),
    COOL("😎", "Feeling Cool"),
    STRESSED("😤", "Stressed"),
    PARTY("🎉", "Party Mode")
}

enum class OutfitScene(val label: String) {
    DAILY("Casual"),
    WORK("Work"),
    DATE("Date"),
    SPORT("Sport"),
    FORMAL("Formal"),
    TRAVEL("Travel"),
    PARTY("Party")
}

data class ClothingItem(
    val id: Long = 0,
    val imagePath: String,
    val category: ClothingCategory,
    val name: String = "",
    val color: String = "",
    val brand: String = "",
    val notes: String = "",
    val price: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val cloudImageUrl: String = ""
)

/** Saved generated image (OOTD board, try-on result, etc.) */
data class SavedImage(
    val id: Long = System.currentTimeMillis(),
    val path: String,                  // local file path or https URL
    val type: String = "look",         // "look" | "tryon"
    val label: String = "",
    val note: String = "",             // extra metadata (date, garment sources, etc.)
    val createdAt: Long = System.currentTimeMillis()
)

data class UserProfile(
    val id: Long = 0,
    val gender: String = "",
    val height: Int = 0,
    val weight: Int = 0,
    val bust: Int = 0,
    val waist: Int = 0,
    val hips: Int = 0,
    val faceImagePath: String = "",
    val bodyImagePath: String = ""
)

data class OutfitSuggestion(
    val scene: OutfitScene,
    val description: String,
    val items: List<ClothingItem>,
    val styleAdvice: String
)

data class OutfitLog(
    val date: String,        // "2026-04-22"
    val itemIds: List<Long>,
    val note: String = ""
)

/** A second-hand listing posted by another user (or this user). */
data class MarketListing(
    val id: String = "",
    val sellerUid: String = "",
    val sellerName: String = "",
    val sellerEmail: String = "",     // for buyer contact (mailto)
    val title: String = "",
    val category: String = "",        // ClothingCategory.name
    val color: String = "",
    val brand: String = "",
    val condition: String = "Good",   // Like new / Good / Fair
    val size: String = "",
    val askingPrice: Double = 0.0,
    val originalPrice: Double = 0.0,
    val currency: String = "AUD",
    val imageUrl: String = "",        // public Firebase Storage URL
    val note: String = "",
    val city: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val sold: Boolean = false
)

/** Items the user has "watched" — sourced from any shop card across the app. */
data class WishlistItem(
    val id: String,                // stable: source + sku/title hash
    val title: String,
    val imageUrl: String = "",
    val itemWebUrl: String = "",
    val source: String = "",       // "eBay", "Amazon", "Google Shopping", retailer name
    val currency: String = "AUD",
    val savedPrice: String = "",   // raw numeric string at save time
    val lastSeenPrice: String = "",// last refreshed price
    val lastCheckedAt: Long = 0L,  // ms epoch
    val savedAt: Long = System.currentTimeMillis(),
    val query: String = ""         // query that surfaced this item — used for re-search
)
