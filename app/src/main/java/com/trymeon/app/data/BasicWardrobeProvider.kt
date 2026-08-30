package com.trymeon.app.data

import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.ClothingItem

object BasicWardrobeProvider {

    // amazon: → AmazonImageSearchService (clean product shots, free with PA API)
    // google: → GoogleImageSearchService (tries ASOS/The Iconic first, then all-web)
    private fun a(query: String) = "amazon:$query"
    private fun g(query: String) = "google:$query"

    val femaleItems: List<ClothingItem> = listOf(
        // Tops
        ClothingItem(id = -1,  imagePath = a("white t-shirt women"),                    category = ClothingCategory.INNER,     name = "White T-Shirt",    color = "white"),
        ClothingItem(id = -2,  imagePath = a("navy blue t-shirt women"),                category = ClothingCategory.INNER,     name = "Navy T-Shirt",     color = "navy"),
        ClothingItem(id = -3,  imagePath = g("white button shirt ghost mannequin"),     category = ClothingCategory.INNER,     name = "White Shirt",      color = "white"),
        ClothingItem(id = -4,  imagePath = a("black turtleneck sweater women"),         category = ClothingCategory.INNER,     name = "Black Turtleneck", color = "black"),
        ClothingItem(id = -5,  imagePath = g("breton stripe shirt ghost mannequin"),    category = ClothingCategory.INNER,     name = "Striped Shirt",    color = "navy stripe"),
        // Outerwear
        ClothingItem(id = -6,  imagePath = g("black blazer ghost mannequin"),           category = ClothingCategory.OUTERWEAR, name = "Black Blazer",     color = "black"),
        ClothingItem(id = -7,  imagePath = g("beige trench coat ghost mannequin"),      category = ClothingCategory.OUTERWEAR, name = "Trench Coat",      color = "beige"),
        ClothingItem(id = -8,  imagePath = a("grey hoodie women fleece"),               category = ClothingCategory.OUTERWEAR, name = "Grey Hoodie",      color = "grey"),
        ClothingItem(id = -9,  imagePath = g("denim jacket ghost mannequin product"),   category = ClothingCategory.OUTERWEAR, name = "Denim Jacket",     color = "blue"),
        // Bottoms
        ClothingItem(id = -10, imagePath = a("blue skinny jeans women"),                category = ClothingCategory.PANTS,     name = "Blue Jeans",       color = "blue"),
        ClothingItem(id = -11, imagePath = a("khaki chino trousers women"),             category = ClothingCategory.PANTS,     name = "Khaki Trousers",   color = "khaki"),
        ClothingItem(id = -12, imagePath = g("black tailored trousers ghost mannequin"),category = ClothingCategory.PANTS,     name = "Black Trousers",   color = "black"),
        ClothingItem(id = -13, imagePath = g("black mini skirt ghost mannequin"),       category = ClothingCategory.PANTS,     name = "Black Skirt",      color = "black"),
        // Dresses
        ClothingItem(id = -14, imagePath = g("floral wrap dress ghost mannequin"),      category = ClothingCategory.DRESS,     name = "Floral Dress",     color = "multicolor"),
        ClothingItem(id = -15, imagePath = g("black slip dress ghost mannequin"),       category = ClothingCategory.DRESS,     name = "Black Dress",      color = "black"),
        // Shoes
        ClothingItem(id = -16, imagePath = a("white sneakers women casual"),            category = ClothingCategory.SHOES,     name = "White Sneakers",   color = "white"),
        ClothingItem(id = -17, imagePath = a("black leather oxford shoes women"),       category = ClothingCategory.SHOES,     name = "Black Shoes",      color = "black"),
        ClothingItem(id = -18, imagePath = a("white canvas sneakers women"),            category = ClothingCategory.SHOES,     name = "Canvas Sneakers",  color = "white"),
        // Accessories
        ClothingItem(id = -19, imagePath = a("cat eye sunglasses women"),               category = ClothingCategory.ACCESSORY, name = "Sunglasses",       color = "black"),
        ClothingItem(id = -20, imagePath = a("black leather belt women"),               category = ClothingCategory.ACCESSORY, name = "Black Belt",       color = "black"),
        // Bags
        ClothingItem(id = -21, imagePath = a("canvas tote bag women"),                  category = ClothingCategory.BAG,       name = "Tote Bag",         color = "natural"),
        ClothingItem(id = -22, imagePath = a("black leather shoulder bag women"),       category = ClothingCategory.BAG,       name = "Black Handbag",    color = "black"),
    )

    val maleItems: List<ClothingItem> = listOf(
        // Tops
        ClothingItem(id = -101, imagePath = a("white t-shirt men crew neck"),                 category = ClothingCategory.INNER,     name = "White T-Shirt",    color = "white"),
        ClothingItem(id = -102, imagePath = a("navy polo shirt men"),                         category = ClothingCategory.INNER,     name = "Navy Polo",        color = "navy"),
        ClothingItem(id = -103, imagePath = g("white oxford shirt men ghost mannequin"),      category = ClothingCategory.INNER,     name = "Oxford Shirt",     color = "white"),
        ClothingItem(id = -104, imagePath = a("grey crewneck sweatshirt men"),                category = ClothingCategory.INNER,     name = "Grey Crewneck",    color = "grey"),
        ClothingItem(id = -105, imagePath = a("black turtleneck sweater men"),                category = ClothingCategory.INNER,     name = "Black Turtleneck", color = "black"),
        // Outerwear
        ClothingItem(id = -106, imagePath = g("black blazer men ghost mannequin"),            category = ClothingCategory.OUTERWEAR, name = "Black Blazer",     color = "black"),
        ClothingItem(id = -107, imagePath = g("beige trench coat men ghost mannequin"),       category = ClothingCategory.OUTERWEAR, name = "Trench Coat",      color = "beige"),
        ClothingItem(id = -108, imagePath = a("grey hoodie men fleece pullover"),             category = ClothingCategory.OUTERWEAR, name = "Grey Hoodie",      color = "grey"),
        ClothingItem(id = -109, imagePath = g("denim jacket men ghost mannequin"),            category = ClothingCategory.OUTERWEAR, name = "Denim Jacket",     color = "blue"),
        ClothingItem(id = -110, imagePath = g("bomber jacket men ghost mannequin"),           category = ClothingCategory.OUTERWEAR, name = "Bomber Jacket",    color = "black"),
        // Bottoms
        ClothingItem(id = -111, imagePath = a("slim fit jeans men blue"),                     category = ClothingCategory.PANTS,     name = "Blue Jeans",       color = "blue"),
        ClothingItem(id = -112, imagePath = a("khaki chino trousers men slim"),               category = ClothingCategory.PANTS,     name = "Khaki Chinos",     color = "khaki"),
        ClothingItem(id = -113, imagePath = g("black tailored trousers men ghost mannequin"), category = ClothingCategory.PANTS,     name = "Black Trousers",   color = "black"),
        ClothingItem(id = -114, imagePath = a("olive cargo pants men"),                       category = ClothingCategory.PANTS,     name = "Cargo Pants",      color = "olive"),
        // Sets / One-pieces (DRESS slot)
        ClothingItem(id = -115, imagePath = a("navy tracksuit set men"),                      category = ClothingCategory.DRESS,     name = "Tracksuit Set",    color = "navy"),
        ClothingItem(id = -116, imagePath = g("navy suit set men ghost mannequin"),           category = ClothingCategory.DRESS,     name = "Navy Suit",        color = "navy"),
        // Shoes
        ClothingItem(id = -117, imagePath = a("white leather sneakers men"),                  category = ClothingCategory.SHOES,     name = "White Sneakers",   color = "white"),
        ClothingItem(id = -118, imagePath = a("black derby shoes men leather"),               category = ClothingCategory.SHOES,     name = "Black Derby",      color = "black"),
        ClothingItem(id = -119, imagePath = a("brown loafer shoes men leather"),              category = ClothingCategory.SHOES,     name = "Brown Loafers",    color = "brown"),
        // Accessories
        ClothingItem(id = -120, imagePath = a("aviator sunglasses men"),                      category = ClothingCategory.ACCESSORY, name = "Sunglasses",       color = "black"),
        ClothingItem(id = -121, imagePath = a("black leather belt men"),                      category = ClothingCategory.ACCESSORY, name = "Black Belt",       color = "black"),
        // Bags
        ClothingItem(id = -122, imagePath = a("canvas tote bag men"),                         category = ClothingCategory.BAG,       name = "Tote Bag",         color = "natural"),
        ClothingItem(id = -123, imagePath = a("black leather messenger bag men"),             category = ClothingCategory.BAG,       name = "Messenger Bag",    color = "black"),
    )

    // Kept for backward compatibility (used as fallback wardrobe in ViewModel)
    val items: List<ClothingItem> get() = femaleItems

    fun byGender(gender: String): List<ClothingItem> = when (gender) {
        "Male"   -> maleItems
        "Female" -> femaleItems
        else     -> femaleItems
    }

    fun byCategory(category: ClothingCategory) = femaleItems.filter { it.category == category }

    fun byGenderAndCategory(gender: String, category: ClothingCategory) =
        byGender(gender).filter { it.category == category }

    // Button-down shirts vs pull-over tops within INNER
    private fun isButtonShirt(item: ClothingItem): Boolean =
        item.name.contains("Shirt") && !item.name.contains("T-Shirt") && !item.name.contains("Striped")

    fun innerTops(gender: String): List<ClothingItem> =
        byGenderAndCategory(gender, ClothingCategory.INNER).filter { !isButtonShirt(it) }

    fun innerShirts(gender: String): List<ClothingItem> =
        byGenderAndCategory(gender, ClothingCategory.INNER).filter { isButtonShirt(it) }
}
