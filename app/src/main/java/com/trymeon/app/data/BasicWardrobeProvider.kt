package com.trymeon.app.data

import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.ClothingItem

object BasicWardrobeProvider {

    /**
     * A garment thumbnail query.
     *
     * There used to be two helpers, `a` and `g`, but both resolve down the same
     * path in FashionImage — the only real difference was that one asked for a
     * packshot and the other did not. That difference showed: searching "navy
     * blue t-shirt women" returns a photograph of a person, and the closet
     * filled up with faces and lifestyle shots instead of clothes. Every query
     * asks for the garment on its own now.
     */
    private fun item(query: String) = "google:$query ghost mannequin product photo"

    val femaleItems: List<ClothingItem> = listOf(
        // Tops
        ClothingItem(id = -1,  imagePath = item("white t-shirt women"),                    category = ClothingCategory.INNER,     name = "White T-Shirt",    color = "white"),
        ClothingItem(id = -2,  imagePath = item("navy blue t-shirt women"),                category = ClothingCategory.INNER,     name = "Navy T-Shirt",     color = "navy"),
        ClothingItem(id = -3,  imagePath = item("white button shirt"),     category = ClothingCategory.INNER,     name = "White Shirt",      color = "white"),
        ClothingItem(id = -4,  imagePath = item("black turtleneck sweater women"),         category = ClothingCategory.INNER,     name = "Black Turtleneck", color = "black"),
        ClothingItem(id = -5,  imagePath = item("breton stripe shirt"),    category = ClothingCategory.INNER,     name = "Striped Shirt",    color = "navy stripe"),
        // Outerwear
        ClothingItem(id = -6,  imagePath = item("black blazer"),           category = ClothingCategory.OUTERWEAR, name = "Black Blazer",     color = "black"),
        ClothingItem(id = -7,  imagePath = item("beige trench coat"),      category = ClothingCategory.OUTERWEAR, name = "Trench Coat",      color = "beige"),
        ClothingItem(id = -8,  imagePath = item("grey hoodie women fleece"),               category = ClothingCategory.OUTERWEAR, name = "Grey Hoodie",      color = "grey"),
        ClothingItem(id = -9,  imagePath = item("denim jacket"),   category = ClothingCategory.OUTERWEAR, name = "Denim Jacket",     color = "blue"),
        // Bottoms
        ClothingItem(id = -10, imagePath = item("blue skinny jeans women"),                category = ClothingCategory.PANTS,     name = "Blue Jeans",       color = "blue"),
        ClothingItem(id = -11, imagePath = item("khaki chino trousers women"),             category = ClothingCategory.PANTS,     name = "Khaki Trousers",   color = "khaki"),
        ClothingItem(id = -12, imagePath = item("black tailored trousers"),category = ClothingCategory.PANTS,     name = "Black Trousers",   color = "black"),
        ClothingItem(id = -13, imagePath = item("black mini skirt"),       category = ClothingCategory.PANTS,     name = "Black Skirt",      color = "black"),
        // Dresses
        ClothingItem(id = -14, imagePath = item("floral wrap dress"),      category = ClothingCategory.DRESS,     name = "Floral Dress",     color = "multicolor"),
        ClothingItem(id = -15, imagePath = item("black slip dress"),       category = ClothingCategory.DRESS,     name = "Black Dress",      color = "black"),
        // Shoes
        ClothingItem(id = -16, imagePath = item("white sneakers women casual"),            category = ClothingCategory.SHOES,     name = "White Sneakers",   color = "white"),
        ClothingItem(id = -17, imagePath = item("black leather oxford shoes women"),       category = ClothingCategory.SHOES,     name = "Black Shoes",      color = "black"),
        ClothingItem(id = -18, imagePath = item("white canvas sneakers women"),            category = ClothingCategory.SHOES,     name = "Canvas Sneakers",  color = "white"),
        // Accessories
        ClothingItem(id = -19, imagePath = item("cat eye sunglasses women"),               category = ClothingCategory.ACCESSORY, name = "Sunglasses",       color = "black"),
        ClothingItem(id = -20, imagePath = item("black leather belt women"),               category = ClothingCategory.ACCESSORY, name = "Black Belt",       color = "black"),
        // Bags
        ClothingItem(id = -21, imagePath = item("canvas tote bag women"),                  category = ClothingCategory.BAG,       name = "Tote Bag",         color = "natural"),
        ClothingItem(id = -22, imagePath = item("black leather shoulder bag women"),       category = ClothingCategory.BAG,       name = "Black Handbag",    color = "black"),
    )

    val maleItems: List<ClothingItem> = listOf(
        // Tops
        ClothingItem(id = -101, imagePath = item("white t-shirt men crew neck"),                 category = ClothingCategory.INNER,     name = "White T-Shirt",    color = "white"),
        ClothingItem(id = -102, imagePath = item("navy polo shirt men"),                         category = ClothingCategory.INNER,     name = "Navy Polo",        color = "navy"),
        ClothingItem(id = -103, imagePath = item("white oxford shirt men"),      category = ClothingCategory.INNER,     name = "Oxford Shirt",     color = "white"),
        ClothingItem(id = -104, imagePath = item("grey crewneck sweatshirt men"),                category = ClothingCategory.INNER,     name = "Grey Crewneck",    color = "grey"),
        ClothingItem(id = -105, imagePath = item("black turtleneck sweater men"),                category = ClothingCategory.INNER,     name = "Black Turtleneck", color = "black"),
        // Outerwear
        ClothingItem(id = -106, imagePath = item("black blazer men"),            category = ClothingCategory.OUTERWEAR, name = "Black Blazer",     color = "black"),
        ClothingItem(id = -107, imagePath = item("beige trench coat men"),       category = ClothingCategory.OUTERWEAR, name = "Trench Coat",      color = "beige"),
        ClothingItem(id = -108, imagePath = item("grey hoodie men fleece pullover"),             category = ClothingCategory.OUTERWEAR, name = "Grey Hoodie",      color = "grey"),
        ClothingItem(id = -109, imagePath = item("denim jacket men"),            category = ClothingCategory.OUTERWEAR, name = "Denim Jacket",     color = "blue"),
        ClothingItem(id = -110, imagePath = item("bomber jacket men"),           category = ClothingCategory.OUTERWEAR, name = "Bomber Jacket",    color = "black"),
        // Bottoms
        ClothingItem(id = -111, imagePath = item("slim fit jeans men blue"),                     category = ClothingCategory.PANTS,     name = "Blue Jeans",       color = "blue"),
        ClothingItem(id = -112, imagePath = item("khaki chino trousers men slim"),               category = ClothingCategory.PANTS,     name = "Khaki Chinos",     color = "khaki"),
        ClothingItem(id = -113, imagePath = item("black tailored trousers men"), category = ClothingCategory.PANTS,     name = "Black Trousers",   color = "black"),
        ClothingItem(id = -114, imagePath = item("olive cargo pants men"),                       category = ClothingCategory.PANTS,     name = "Cargo Pants",      color = "olive"),
        // Sets / One-pieces (DRESS slot)
        ClothingItem(id = -115, imagePath = item("navy tracksuit set men"),                      category = ClothingCategory.DRESS,     name = "Tracksuit Set",    color = "navy"),
        ClothingItem(id = -116, imagePath = item("navy suit set men"),           category = ClothingCategory.DRESS,     name = "Navy Suit",        color = "navy"),
        // Shoes
        ClothingItem(id = -117, imagePath = item("white leather sneakers men"),                  category = ClothingCategory.SHOES,     name = "White Sneakers",   color = "white"),
        ClothingItem(id = -118, imagePath = item("black derby shoes men leather"),               category = ClothingCategory.SHOES,     name = "Black Derby",      color = "black"),
        ClothingItem(id = -119, imagePath = item("brown loafer shoes men leather"),              category = ClothingCategory.SHOES,     name = "Brown Loafers",    color = "brown"),
        // Accessories
        ClothingItem(id = -120, imagePath = item("aviator sunglasses men"),                      category = ClothingCategory.ACCESSORY, name = "Sunglasses",       color = "black"),
        ClothingItem(id = -121, imagePath = item("black leather belt men"),                      category = ClothingCategory.ACCESSORY, name = "Black Belt",       color = "black"),
        // Bags
        ClothingItem(id = -122, imagePath = item("canvas tote bag men"),                         category = ClothingCategory.BAG,       name = "Tote Bag",         color = "natural"),
        ClothingItem(id = -123, imagePath = item("black leather messenger bag men"),             category = ClothingCategory.BAG,       name = "Messenger Bag",    color = "black"),
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
