package com.example.myapplication.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.domain.model.OutfitScene
import com.example.myapplication.domain.model.UserProfile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

// --- Retrofit API interface (OpenAI) ---
interface OpenAiApi {
    @POST("v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Body request: OpenAiRequest
    ): OpenAiResponse
}

// --- Request/Response models ---
data class OpenAiRequest(
    val model: String = "gpt-4o",
    val messages: List<OpenAiMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 2048
)

data class OpenAiMessage(
    val role: String,
    val content: List<OpenAiContent>
)

data class OpenAiContent(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    val url: String,
    val detail: String = "low"
)

data class OpenAiResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: ChoiceMessage
)

data class ChoiceMessage(
    val content: String?
)

data class ImageGenerationData(
    val url: String? = null,
    @SerializedName("b64_json") val b64Json: String? = null
)

data class ImageGenerationResponse(
    val data: List<ImageGenerationData>? = null
)

// --- Service ---
class ClaudeApiService(private val context: Context) {

    private val httpClient by lazy {
        RelayHttp.builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private val api: OpenAiApi by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = RelayHttp.builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiApi::class.java)
    }

    suspend fun generateOutfitSuggestion(
        apiKey: String,
        clothes: List<ClothingItem>,
        scene: OutfitScene,
        profile: UserProfile?,
        weatherContext: String = "",
        closetOnly: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val contents = mutableListOf<OpenAiContent>()

        val profileDesc = if (profile != null && profile.height > 0) {
            "User info: ${profile.gender}, height ${profile.height}cm, weight ${profile.weight}kg" +
                if (profile.bust > 0) ", measurements ${profile.bust}/${profile.waist}/${profile.hips}cm" else ""
        } else ""

        val clothingDesc = clothes.joinToString("\n") {
            "- ${it.category.label}: ${it.name.ifEmpty { it.category.label }}${if (it.color.isNotEmpty()) " (${it.color})" else ""}"
        }

        val weatherLine = if (weatherContext.isNotEmpty()) "\nCurrent weather: $weatherContext\n" else ""

        val closetRule = if (closetOnly)
            "\nIMPORTANT: ONLY use items from the wardrobe list above. Do NOT suggest any items not listed. If a key piece is missing, acknowledge the gap instead of substituting.\n"
        else ""

        val point4 = if (closetOnly)
            "4. Note any missing key pieces (e.g. no top, no outerwear for cold weather) without suggesting alternatives"
        else
            "4. If no suitable pieces are available, suggest basic essentials to add"

        val prompt = """
You are a professional fashion stylist. Based on the wardrobe below, recommend the best outfit for the "${scene.label}" occasion.

$profileDesc$weatherLine$closetRule
Wardrobe:
$clothingDesc

Please provide:
1. Recommended outfit (list the specific pieces with their exact names from the wardrobe)
2. Why this combination suits the ${scene.label} occasion
3. Styling tips for the best effect
$point4
        """.trimIndent()

        contents.add(OpenAiContent(type = "text", text = prompt))

        // Attach up to 4 clothing images
        clothes.take(4).forEach { item ->
            val base64 = encodeImageToBase64(item.imagePath)
            if (base64 != null) {
                contents.add(OpenAiContent(
                    type = "image_url",
                    imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64")
                ))
            }
        }

        val request = OpenAiRequest(
            messages = listOf(OpenAiMessage(role = "user", content = contents))
        )

        val response = api.chat(auth = "Bearer $apiKey", request = request)
        response.choices.firstOrNull()?.message?.content ?: "Unable to get outfit suggestions. Please check your network connection."
    }


    suspend fun getEmergencyOutfit(
        apiKey: String,
        situation: String,
        clothes: List<ClothingItem>
    ): String = withContext(Dispatchers.IO) {
        val clothingDesc = clothes.joinToString("\n") {
            "- ${it.category.label}: ${it.name.ifEmpty { it.category.label }}${if (it.color.isNotEmpty()) " (${it.color})" else ""}"
        }
        val prompt = """
I have ${situation}. Please immediately pick the best outfit from my wardrobe below:
1. Exactly which pieces to wear (list them directly)
2. One sentence on why this works
3. Any details to note (e.g. layering tips, accessories)

Wardrobe:
$clothingDesc

Be concise and direct, under 150 words.
        """.trimIndent()
        val request = OpenAiRequest(
            messages = listOf(OpenAiMessage("user", listOf(OpenAiContent("text", prompt)))),
            maxTokens = 400
        )
        api.chat("Bearer $apiKey", request).choices.firstOrNull()?.message?.content ?: "Unable to get suggestions"
    }

    suspend fun rateOutfit(
        apiKey: String,
        imageBase64: String,
        profile: UserProfile?
    ): String = withContext(Dispatchers.IO) {
        val profileHint = if (profile != null && profile.height > 0)
            "User: height ${profile.height}cm, weight ${profile.weight}kg, ${profile.gender}. " else ""
        val prompt = """
${profileHint}Please rate this outfit and give feedback.
Return strictly in the following format (scores 1-10):
Color Harmony: X/10
Occasion Fit: X/10
Overall Look: X/10
Highlight: (one sentence)
Suggestion: (one sentence)
        """.trimIndent()
        val contents = listOf(
            OpenAiContent("text", prompt),
            OpenAiContent("image_url", imageUrl = ImageUrl("data:image/jpeg;base64,$imageBase64", "low"))
        )
        val request = OpenAiRequest(
            messages = listOf(OpenAiMessage("user", contents)),
            maxTokens = 300
        )
        api.chat("Bearer $apiKey", request).choices.firstOrNull()?.message?.content ?: "Unable to rate"
    }


    suspend fun detectClothingCategory(apiKey: String, imageBase64: String): com.example.myapplication.domain.model.ClothingCategory = withContext(Dispatchers.IO) {
        val contents = listOf(
            OpenAiContent("text", "What type of clothing is in this image? Reply with EXACTLY one word from: Top, Outerwear, Bottoms, Dress, Shoes, Bag, Accessory. No other text."),
            OpenAiContent("image_url", imageUrl = ImageUrl("data:image/jpeg;base64,$imageBase64", "low"))
        )
        val request = OpenAiRequest(messages = listOf(OpenAiMessage("user", contents)), maxTokens = 10)
        val raw = try { api.chat("Bearer $apiKey", request).choices.firstOrNull()?.message?.content?.trim() ?: "" }
        catch (e: Exception) { "" }
        when {
            raw.contains("outerwear", true) || raw.contains("jacket", true) || raw.contains("coat", true) -> com.example.myapplication.domain.model.ClothingCategory.OUTERWEAR
            raw.contains("bottom", true) || raw.contains("pants", true) || raw.contains("skirt", true) || raw.contains("trouser", true) -> com.example.myapplication.domain.model.ClothingCategory.PANTS
            raw.contains("dress", true) -> com.example.myapplication.domain.model.ClothingCategory.DRESS
            raw.contains("shoes", true) || raw.contains("sneaker", true) || raw.contains("boot", true) -> com.example.myapplication.domain.model.ClothingCategory.SHOES
            raw.contains("bag", true) || raw.contains("purse", true) || raw.contains("handbag", true) -> com.example.myapplication.domain.model.ClothingCategory.BAG
            raw.contains("accessory", true) || raw.contains("accessories", true) -> com.example.myapplication.domain.model.ClothingCategory.ACCESSORY
            else -> com.example.myapplication.domain.model.ClothingCategory.INNER
        }
    }

    suspend fun inferGenderFromWardrobe(apiKey: String, clothes: List<ClothingItem>): String = withContext(Dispatchers.IO) {
        if (clothes.isEmpty()) return@withContext ""
        val desc = clothes.joinToString(", ") { "${it.category.label}: ${it.name.ifBlank { it.category.label }}" }
        val prompt = "Based only on the following clothing items, infer the owner's most likely gender. Reply with exactly one word: Female, Male, or Other.\n\nClothing: $desc"
        val request = OpenAiRequest(
            messages = listOf(OpenAiMessage("user", listOf(OpenAiContent("text", prompt)))),
            maxTokens = 5
        )
        val raw = api.chat("Bearer $apiKey", request).choices.firstOrNull()?.message?.content?.trim() ?: ""
        when {
            raw.contains("Female", ignoreCase = true) -> "Female"
            raw.contains("Male", ignoreCase = true)   -> "Male"
            else -> ""
        }
    }

    suspend fun inferGenderFromPhoto(apiKey: String, imagePath: String): String = withContext(Dispatchers.IO) {
        val base64 = encodeImageToBase64(imagePath) ?: return@withContext ""
        val contents = listOf(
            OpenAiContent("text", "Based on the person's appearance in the photo, determine their gender. Reply only with: Female, Male, or Other. Do not include anything else."),
            OpenAiContent("image_url", imageUrl = ImageUrl("data:image/jpeg;base64,$base64", "low"))
        )
        val request = OpenAiRequest(messages = listOf(OpenAiMessage("user", contents)), maxTokens = 10)
        val raw = api.chat("Bearer $apiKey", request).choices.firstOrNull()?.message?.content?.trim() ?: ""
        when {
            raw.contains("Female", ignoreCase = true) -> "Female"
            raw.contains("Male", ignoreCase = true) -> "Male"
            else -> "Other"
        }
    }

    suspend fun getCompleteLookRecommendations(
        apiKey: String,
        pinnedItems: Map<com.example.myapplication.domain.model.ClothingCategory, ClothingItem?>,
        missingCategories: List<com.example.myapplication.domain.model.ClothingCategory>,
        profile: UserProfile?
    ): String = withContext(Dispatchers.IO) {
        val profileDesc = if (profile != null && profile.height > 0)
            "User: ${profile.gender}, ${profile.height}cm, ${profile.weight}kg" else ""
        val pinnedDesc = pinnedItems.entries.joinToString("\n") { (cat, item) ->
            "- ${cat.label}: ${item?.run { "${color} ${name}".trim().ifBlank { category.label } } ?: cat.label}"
        }
        val categoryNames = missingCategories.joinToString(", ") { it.name }
        val prompt = """
You are a fashion stylist. Complete this outfit.

Selected pieces:
$pinnedDesc
$profileDesc

Recommend items for missing categories: ${missingCategories.joinToString(", ") { it.label }}

Return ONLY a valid JSON array, no markdown:
[{"category":"PANTS","suggestion":"navy slim chinos","searchQuery":"navy slim chinos","reason":"navy pairs cleanly with white"}]

"category" must be one of: $categoryNames
        """.trimIndent()
        val request = OpenAiRequest(
            model = "gpt-4o-mini",
            messages = listOf(OpenAiMessage("user", listOf(OpenAiContent("text", prompt)))),
            maxTokens = 512
        )
        api.chat("Bearer $apiKey", request).choices.firstOrNull()?.message?.content ?: "[]"
    }

    suspend fun getTryOnEbayRecommendations(
        apiKey: String,
        clothes: List<ClothingItem>,
        gender: String = "",
        styleKeywords: Set<String> = emptySet()
    ): String = withContext(Dispatchers.IO) {
        val normalizedGender = when (gender.trim().lowercase()) {
            "female", "f" -> "Female"
            "male", "m"   -> "Male"
            else          -> gender
        }
        val gw = when (normalizedGender) { "Female" -> "woman"; "Male" -> "man"; else -> "" }

        val clothingDesc = clothes.joinToString("\n") {
            buildString {
                append("- ${it.category.label}: ${it.name.ifEmpty { it.category.label }}")
                if (it.color.isNotEmpty()) append(" (${it.color})")
                if (it.brand.isNotEmpty()) append(" [${it.brand}]")
                if (it.notes.isNotEmpty()) append(" — ${it.notes.take(40)}")
            }
        }

        // Wardrobe style signals
        val dominantColors = clothes.mapNotNull { it.color.takeIf { c -> c.isNotEmpty() } }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            .take(3).joinToString(", ") { it.key }
        val wornBrands = clothes.mapNotNull { it.brand.takeIf { b -> b.isNotEmpty() } }
            .distinct().take(4).joinToString(", ")

        // Categories that already have many items — avoid over-recommending
        val saturated = clothes.groupBy { it.category }.entries
            .filter { it.value.size >= 3 }.joinToString(", ") { it.key.label }

        val styleCtx = buildString {
            if (styleKeywords.isNotEmpty()) appendLine("User style preferences: ${styleKeywords.joinToString(", ")}")
            if (dominantColors.isNotEmpty()) appendLine("Dominant wardrobe colours: $dominantColors")
            if (wornBrands.isNotEmpty()) appendLine("Brands they already wear: $wornBrands")
            if (saturated.isNotEmpty()) appendLine("Already well-stocked (avoid recommending more): $saturated")
        }.trim()

        val genderLine = if (gw.isNotEmpty())
            "USER GENDER: $gender — every search query MUST start with \"$gw\""
        else
            "USER GENDER: unknown — infer appropriate gender from wardrobe context"

        val prompt = """
You are a personal stylist. Recommend 6 garment categories for this user to try on next.

$genderLine

$styleCtx

Current wardrobe:
$clothingDesc

Rules:
- Search queries must match the user's actual style preferences and colour palette above
- Recommend specific styles, not generic items (e.g. "$gw oversized linen shirt" not "$gw shirt")
- Complement gaps in the wardrobe; do not duplicate saturated categories
- Every query starts with "$gw"

Return EXACTLY 6 lines, nothing else:
CAT:top|reason (under 10 words)|$gw [specific style + item]
CAT:jacket|reason (under 10 words)|$gw [specific style + item]
CAT:bottoms|reason (under 10 words)|$gw [specific style + item]
CAT:set|reason (under 10 words)|$gw [specific style + item]
CAT:shoes|reason (under 10 words)|$gw [specific style + item]
CAT:bag|reason (under 10 words)|$gw [specific style + item]
        """.trimIndent()
        val request = OpenAiRequest(
            messages = listOf(OpenAiMessage("user", listOf(OpenAiContent("text", prompt)))),
            maxTokens = 300
        )
        api.chat("Bearer $apiKey", request).choices.firstOrNull()?.message?.content ?: ""
    }

    // Complete the Look: given one wardrobe item, return 2-3 missing pieces to complete an outfit.
    // Returns lines formatted as: "QUERY|<short search query>|<one-line reason>"
    suspend fun completeTheLook(
        apiKey: String,
        anchorItem: ClothingItem,
        clothes: List<ClothingItem>,
        gender: String = ""
    ): String = withContext(Dispatchers.IO) {
        val wardrobeDesc = clothes.take(40).joinToString("\n") {
            "- ${it.category.label}: ${it.name.ifEmpty { it.category.label }}${if (it.color.isNotEmpty()) " (${it.color})" else ""}"
        }
        val genderHint = if (gender.isNotBlank()) "User gender: $gender. Prioritize $gender items." else ""
        val anchorDesc = "${anchorItem.category.label}: ${anchorItem.name.ifEmpty { anchorItem.category.label }}${if (anchorItem.color.isNotEmpty()) " (${anchorItem.color})" else ""}"
        val prompt = """
You're a senior fashion stylist. The user has this anchor item: $anchorDesc

Their wardrobe:
$wardrobeDesc

$genderHint

Pick 2-3 pieces that would COMPLETE a head-to-toe outfit around this anchor. If the anchor is a top, recommend bottoms / shoes / outerwear; if it's a bottom, recommend tops / shoes / outerwear. Each piece must be a different category from the anchor and from each other.

For CATEGORY, use EXACTLY one of: INNER (tops/shirts), OUTERWEAR, PANTS (bottoms/skirts), DRESS, SHOES, ACCESSORY, BAG.

Return EXACTLY this format, one per line, no extra text:
QUERY|<CATEGORY>|<2-5 word shopping search query>|<short one-line reason, under 60 chars>

Example:
QUERY|INNER|white slim crew tee|Clean base layer to balance the print
QUERY|PANTS|cream wool trousers|Adds warmth and a tailored finish
QUERY|SHOES|tan leather loafers|Grounds the look with a smart finish
        """.trimIndent()
        val request = OpenAiRequest(
            messages = listOf(OpenAiMessage("user", listOf(OpenAiContent("text", prompt)))),
            maxTokens = 400
        )
        api.chat("Bearer $apiKey", request).choices.firstOrNull()?.message?.content ?: ""
    }

    // Dupe Finder: given an item, return budget-friendly look-alike search queries.
    // Format per line: "DUPE|<2-5 word query>|<why it's a good dupe, under 60 chars>"
    // Plus one line: "TIER|<estimated price range of the original, e.g. $120-180>"

    /**
     * English wish → the Chinese phrases a Taobao seller would actually have
     * typed, plus a parcel estimate so freight can be quoted before anything is
     * bought.
     *
     * Asking for seller vocabulary rather than a translation is the whole trick:
     * "cropped linen blazer" translated literally returns nothing, while
     * "亚麻小西装 短款" returns the listing. The parcel figures matter because
     * air freight bills volume, and a user has no idea what their coat measures
     * folded.
     */
    /**
     * Pieces this wardrobe is missing, phrased as things to search for.
     *
     * Deliberately lighter than [closetAudit]: no score, no palette, no prose —
     * just the shortest route from "here is what I own" to "here is what to buy
     * and what it lands at". The gap the user already accepts is the strongest
     * possible seed for a sourcing search, and it needs no typing at all.
     */
    suspend fun closetGapQueries(
        apiKey: String,
        clothes: List<ClothingItem>,
        gender: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (clothes.isEmpty()) return@withContext ""
        val inventory = clothes
            .groupBy { it.category.label }
            .entries.joinToString("\n") { (cat, items) ->
                "$cat (${items.size}): " + items.take(12).joinToString(", ") {
                    listOf(it.color, it.name).filter(String::isNotBlank).joinToString(" ")
                }
            }
        val genderHint = if (gender.isNotBlank()) "Shopper gender: $gender." else ""
        val prompt = """
Here is someone's entire wardrobe:

$inventory

$genderHint

Name the 4 pieces that would create the most new outfits with what they already own. Judge by how many existing items each one combines with, not by what is fashionable. Skip anything they clearly already have.

Each query must read like something typed into a shop search — concrete about cut, fabric and colour — because it will be used to search a Chinese marketplace.

Return EXACTLY this format, no extra text:
GAP|<2-5 word shopping query in English>|<how many existing pieces it works with and why, under 60 chars>
GAP|...
GAP|...
GAP|...
        """.trimIndent()
        val request = OpenAiRequest(
            messages = listOf(OpenAiMessage("user", listOf(OpenAiContent("text", prompt)))),
            maxTokens = 350
        )
        api.chat("Bearer $apiKey", request).choices.firstOrNull()?.message?.content ?: ""
    }

    suspend fun sourcingQuery(
        apiKey: String,
        englishDescription: String,
        gender: String = "",
        categoryHint: String = ""
    ): String = withContext(Dispatchers.IO) {
        val genderHint = if (gender.isNotBlank()) "Shopper gender: $gender." else ""
        val catHint = if (categoryHint.isNotBlank()) "Likely category: $categoryHint." else ""
        val prompt = """
You help an English-speaking shopper in Australia buy from Taobao/1688, where they cannot read or search in Chinese.

They want: "$englishDescription"
$genderHint $catHint

Write the Chinese search phrases a Taobao SELLER would put in the listing title — not a literal translation. Use the marketing vocabulary Chinese sellers actually use (fabric, silhouette, style-tribe and fit words). Order them best-match first. Keep each phrase 3-10 characters plus optional modifiers, the way a real search box is used.

Then estimate how the item ships: its packed weight in grams and folded dimensions in cm. Air freight is billed on volume, so these must be the compressed parcel, not the garment laid flat.

Finish with one line of advice specific to buying THIS from a Chinese seller (sizing runs, fabric traps, common misdescriptions).

Return EXACTLY this format, no extra text:
CN|<best Chinese search phrase>
CN|<second phrase, different angle>
CN|<third phrase, broader>
EN|<what you understood they want, under 60 chars>
CAT|<one of INNER OUTERWEAR PANTS DRESS SHOES ACCESSORY BAG>
PARCEL|<grams>|<length cm>|<width cm>|<height cm>
NOTE|<buying advice, under 80 chars>
        """.trimIndent()
        val request = OpenAiRequest(
            messages = listOf(OpenAiMessage("user", listOf(OpenAiContent("text", prompt)))),
            maxTokens = 400
        )
        api.chat("Bearer $apiKey", request).choices.firstOrNull()?.message?.content ?: ""
    }

    // Trending Now: season + style aware list of trending pieces.
    // Format per line: "TREND|<2-5 word query>|<one-line why it's trending, under 65 chars>"

    // Gift Mode: recipient questionnaire → 6 gift queries with reasons.
    // Returns lines: "GIFT|<short search query>|<reason>"

    // Style Twin: returns 2-3 celebrity style matches with % + reasoning.
    // Format per line: "TWIN|<celebrity name>|<percentage>|<one-line reason>"
    // Plus a final line: "SHOP|<query1>|<query2>|<query3>" for shopping their look.
    suspend fun styleTwin(
        apiKey: String,
        clothes: List<ClothingItem>,
        styleKeywords: Set<String> = emptySet(),
        gender: String = ""
    ): String = withContext(Dispatchers.IO) {
        val wardrobeDesc = clothes.take(40).joinToString("\n") {
            "- ${it.category.label}: ${it.name.ifEmpty { it.category.label }}${if (it.color.isNotEmpty()) " (${it.color})" else ""}"
        }
        val keywordHint = if (styleKeywords.isNotEmpty()) "Stated style preferences: ${styleKeywords.joinToString(", ")}" else ""
        val genderHint = if (gender.isNotBlank()) "Gender: $gender (pick celebrities of matching gender)" else ""
        val prompt = """
You're a fashion editor. Analyze this wardrobe and find the user's "style twins" — 2-3 well-known celebrities whose signature style most closely matches.

Wardrobe:
$wardrobeDesc

$keywordHint
$genderHint

Return EXACTLY this format, no extra text:
TWIN|<celebrity name>|<percentage 0-100>|<one short reason, under 70 chars>
TWIN|...
TWIN|...
SHOP|<query1>|<query2>|<query3>

The 3 SHOP queries are search terms for items that capture the top celebrity's signature look. Each query 2-5 words.

Example:
TWIN|Hailey Bieber|62|Oversized blazers, baggy denim, minimalist neutrals
TWIN|Phoebe Philo|24|Clean lines, monochrome palette, structured tailoring
TWIN|Sofia Richie|14|Quiet luxury, soft tones, polished basics
SHOP|oversized black blazer|baggy straight jeans|white ribbed tank
        """.trimIndent()
        val request = OpenAiRequest(
            messages = listOf(OpenAiMessage("user", listOf(OpenAiContent("text", prompt)))),
            maxTokens = 500
        )
        api.chat("Bearer $apiKey", request).choices.firstOrNull()?.message?.content ?: ""
    }

    // Closet Audit: returns a structured report with sections.
    // Format:
    //   SCORE|<0-100>
    //   PALETTE|<color>,<color>,<color>,...
    //   STRENGTH|<one paragraph>
    //   GAPS|<gap1>;<gap2>;<gap3>
    //   BUY|<query>|<reason>
    //   BUY|<query>|<reason>
    //   ... up to 6 BUY lines
    suspend fun closetAudit(
        apiKey: String,
        clothes: List<ClothingItem>,
        profile: UserProfile?,
        styleKeywords: Set<String> = emptySet()
    ): String = withContext(Dispatchers.IO) {
        val wardrobeDesc = clothes.take(60).joinToString("\n") {
            "- ${it.category.label}: ${it.name.ifEmpty { it.category.label }}${if (it.color.isNotEmpty()) " (${it.color})" else ""}${if (it.brand.isNotEmpty()) " by ${it.brand}" else ""}"
        }
        val profileLine = if (profile != null && profile.height > 0)
            "User: ${profile.gender}, height ${profile.height}cm, weight ${profile.weight}kg." else ""
        val styleLine = if (styleKeywords.isNotEmpty()) "Stated style preferences: ${styleKeywords.joinToString(", ")}" else ""
        val prompt = """
You're a luxury personal stylist conducting a closet audit. Analyze this wardrobe and produce a professional report.

$profileLine
$styleLine

Wardrobe (${clothes.size} items):
$wardrobeDesc

Return EXACTLY this format, no extra commentary outside the structure:
SCORE|<wardrobe versatility score, 0-100 integer>
PALETTE|<comma-separated 4-6 dominant colors as plain English, e.g. "cream, charcoal, camel, off-white">
STRENGTH|<one short paragraph (2 sentences max) on what this wardrobe does well>
GAPS|<gap1>;<gap2>;<gap3>
BUY|<2-5 word shopping query>|<one-line reason under 70 chars why this fills a gap>
BUY|...
BUY|...
BUY|...
BUY|...
BUY|...

Provide exactly 6 BUY suggestions. Each should be high-impact for this specific wardrobe — not generic. Use precise descriptive queries (color, fabric, silhouette).
        """.trimIndent()
        val request = OpenAiRequest(
            messages = listOf(OpenAiMessage("user", listOf(OpenAiContent("text", prompt)))),
            maxTokens = 900
        )
        api.chat("Bearer $apiKey", request).choices.firstOrNull()?.message?.content ?: ""
    }


    // Generate a virtual try-on image using gpt-image-1.
    // faceImagePath: local file path or https URL of the person's photo.
    // garments: list of (imagePath/URL, role label) — e.g. ("path/to/shirt.jpg", "White Shirt top").
    // Returns the saved cache file path on success.
    // viewAngle: "front" | "back" | "side"
    // bodyReferencePath: optional full-body photo for accurate proportions (separate from face photo)
    /**
     * Build the reusable full-body portrait behind every try-on.
     *
     * Generated once from the user's photo and measurements, in plain neutral
     * clothing, so it can be dressed repeatedly. Separating this from the
     * try-on itself is what makes the person come out the same every time —
     * asking for a person and an outfit in one shot re-invents the person.
     */
    suspend fun generateModelPortrait(
        apiKey: String,
        facePhotoPath: String,
        profile: UserProfile?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = "Full-length studio portrait of one person, head to toe, " +
                "facing the camera in a relaxed neutral stance. " +
                "image[0] is the reference — keep this person's face, hair and skin tone exactly as shown. " +
                buildBodyDesc(profile) +
                "Dress them in plain fitted neutral grey underlayers with no pattern, branding or accessories. " +
                "Plain light grey seamless background, soft even studio lighting, sharp focus, no props, no text."

            val faceBytes = loadImageBytes(facePhotoPath)
                ?: return@withContext Result.failure(Exception("Unable to read the reference photo"))

            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", "gpt-image-1")
                .addFormDataPart("prompt", prompt)
                .addFormDataPart("n", "1")
                .addFormDataPart("size", "1024x1536")
                .addFormDataPart("image[]", "face.jpg", faceBytes.toRequestBody("image/jpeg".toMediaType()))

            val req = Request.Builder()
                .url("https://api.openai.com/v1/images/edits")
                .header("Authorization", "Bearer $apiKey")
                .post(builder.build())
                .build()

            val resp = httpClient.newCall(req).execute()
            val bodyStr = resp.body?.string() ?: ""
            Log.d("ModelPortrait", "status=${resp.code} body_prefix=${bodyStr.take(160)}")
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("Portrait generation failed ${resp.code}: $bodyStr"))
            }

            val imgData = Gson().fromJson(bodyStr, ImageGenerationResponse::class.java).data?.firstOrNull()
                ?: return@withContext Result.failure(Exception("No portrait returned"))

            // Stable filename: the portrait is a kept asset, not a cache entry,
            // and leaving a new file per attempt would fill the app's storage.
            val outFile = File(context.filesDir, "virtual_model.png")
            when {
                imgData.b64Json != null -> {
                    FileOutputStream(outFile).use { it.write(Base64.decode(imgData.b64Json, Base64.DEFAULT)) }
                    Result.success(outFile.absolutePath)
                }
                imgData.url != null -> Result.success(imgData.url)
                else -> Result.failure(Exception("No valid portrait returned"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to build the model portrait: ${e.message}"))
        }
    }

    suspend fun generateTryOnImage(
        apiKey: String,
        faceImagePath: String,
        garments: List<Pair<String, String>>,
        profile: UserProfile?,
        viewAngle: String = "front",
        bodyReferencePath: String? = null,
        /**
         * A previously generated portrait. When present it replaces the raw
         * photo entirely — it already carries the face and the proportions, and
         * reusing one image is what keeps the same person across try-ons.
         */
        modelPortraitPath: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bodyDesc = buildBodyDesc(profile)
            val portrait = modelPortraitPath?.takeIf { it.isNotBlank() && File(it).exists() }
            val identityPath = portrait ?: faceImagePath
            // A portrait already shows the whole body, so a separate reference
            // would only be a second, conflicting source of proportions.
            val hasBodyRef = portrait == null &&
                bodyReferencePath != null && bodyReferencePath != faceImagePath
            val garmentStartIndex = if (hasBodyRef) 2 else 1
            val garmentList = garments.mapIndexed { i, (_, label) -> "image[${garmentStartIndex + i}]: $label" }.joinToString("; ")
            val angleDesc = when (viewAngle) {
                "back" -> "Rear-facing pose, showing the back of the outfit."
                "side" -> "Three-quarter angle, model turned 45 degrees to the right."
                else   -> "Front-facing pose, model looking at the camera."
            }
            val bodyRefNote = if (hasBodyRef)
                "image[1] shows the model's full body — use it to match their exact proportions and figure. "
            else ""

            val identityNote = if (portrait != null)
                "image[0] is the model — keep their face, hair, skin tone and body proportions exactly as shown. "
            else
                "image[0] is the face reference — keep the model's face and hair exactly as shown. "

            val prompt = "Fashion lookbook photo. $angleDesc " +
                identityNote +
                bodyRefNote +
                "Dress the model in: $garmentList. " +
                bodyDesc +
                "Match each garment's exact color, pattern, and texture from its reference image. " +
                // Listing photos are styled full outfits. Copying the rest of the
                // look would show the shopper wearing things they are not buying.
                // "Neutral basics" was too abstract: the model kept matching the
                // listing's palette, so the filler still read as part of the set.
                // Naming an exact colour is what actually separates the two.
                "Reproduce ONLY the listed garments from the references. Every other " +
                "clothing item must be plain mid-grey (#8A8A8A) with no pattern, print " +
                "or hardware, visibly not part of the same outfit. Do not copy any other " +
                "clothing, accessory or styling detail from the reference images. " +
                "Full-length shot head to toe. Clean studio background, fashion photography lighting."

            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", "gpt-image-1")
                .addFormDataPart("prompt", prompt)
                .addFormDataPart("n", "1")
                .addFormDataPart("size", "1024x1536")

            // image[0]: the kept portrait when there is one, else the raw photo
            val faceBytes = loadImageBytes(identityPath)
                ?: return@withContext Result.failure(Exception("Unable to read the model photo"))
            builder.addFormDataPart("image[]", "model.jpg", faceBytes.toRequestBody("image/jpeg".toMediaType()))

            // image[1]: optional full-body reference for proportions
            if (hasBodyRef) {
                val bodyBytes = loadImageBytes(bodyReferencePath!!)
                if (bodyBytes != null) {
                    builder.addFormDataPart("image[]", "body.jpg", bodyBytes.toRequestBody("image/jpeg".toMediaType()))
                }
            }

            // Garment reference images
            garments.forEachIndexed { i, (path, _) ->
                val bytes = loadImageBytes(path) ?: return@forEachIndexed
                builder.addFormDataPart("image[]", "garment_$i.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            }

            val req = Request.Builder()
                .url("https://api.openai.com/v1/images/edits")
                .header("Authorization", "Bearer $apiKey")
                .post(builder.build())
                .build()

            val resp = httpClient.newCall(req).execute()
            val bodyStr = resp.body?.string() ?: ""
            Log.d("TryOnGen", "status=${resp.code} body_prefix=${bodyStr.take(200)}")

            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("Image generation failed ${resp.code}: $bodyStr"))
            }

            val imgResp = Gson().fromJson(bodyStr, ImageGenerationResponse::class.java)
            val imgData = imgResp.data?.firstOrNull()
                ?: return@withContext Result.failure(Exception("No image data returned"))

            val outFile = File(context.cacheDir, "tryon_${System.currentTimeMillis()}.png")
            when {
                imgData.b64Json != null -> {
                    val bytes = Base64.decode(imgData.b64Json, Base64.DEFAULT)
                    FileOutputStream(outFile).use { it.write(bytes) }
                    Result.success(outFile.absolutePath)
                }
                imgData.url != null -> Result.success(imgData.url)
                else -> Result.failure(Exception("No valid image returned"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to generate try-on image: ${e.message}"))
        }
    }

    // Converts height + weight into descriptive fashion terms gpt-image-1 responds to.
    private fun buildBodyDesc(profile: UserProfile?): String {
        if (profile == null || profile.height <= 0) return ""
        val genderWord = when (profile.gender.trim().lowercase()) {
            "female", "f" -> "woman"
            "male", "m"   -> "man"
            else          -> "person"
        }
        val heightDesc = when {
            profile.height < 155 -> "petite"
            profile.height < 163 -> "short"
            profile.height < 170 -> "average height"
            profile.height < 178 -> "tall"
            else                 -> "very tall"
        }
        val buildDesc = if (profile.weight > 0) {
            val bmi = profile.weight.toFloat() / ((profile.height / 100f) * (profile.height / 100f))
            when {
                bmi < 17.5 -> "very slim"
                bmi < 20   -> "slim"
                bmi < 23   -> "lean"
                bmi < 25   -> "average"
                bmi < 27.5 -> "slightly fuller"
                bmi < 30   -> "fuller figure"
                else       -> "plus-size"
            }
        } else ""
        val desc = listOfNotNull(heightDesc.takeIf { it.isNotEmpty() }, buildDesc.takeIf { it.isNotEmpty() })
            .joinToString(", ")
        return "The model is a $genderWord with a $desc figure. "
    }

    /**
     * Generate a fashion editorial image based on the outfit suggestion.
     * Uses DALL-E 3 with a prompt derived from the scene + AI suggestion text.
     * Returns a local cache file path on success.
     */
    suspend fun generateOutfitBoard(
        apiKey: String,
        scene: com.example.myapplication.domain.model.OutfitScene,
        suggestion: String,
        profile: UserProfile?,
        faceImagePath: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bodyDesc = buildBodyDesc(profile)
            val snippetWords = suggestion.replace("\n", " ").split(" ").take(40).joinToString(" ")
            val outFile = java.io.File(context.cacheDir, "outfit_board_${System.currentTimeMillis()}.png")

            val resolvedFace = faceImagePath?.takeIf { it.isNotBlank() }
            val faceBytes = resolvedFace?.let { loadImageBytes(it) }

            if (faceBytes != null) {
                // Use gpt-image-1 with the user's face photo as the model
                val prompt = "Fashion editorial lookbook. Front-facing full-length pose. " +
                    "image[0] is the model — keep their face, hair and body type exactly as shown. " +
                    "${bodyDesc}Dress the model in a ${scene.label} outfit: $snippetWords. " +
                    "Clean studio background, fashion photography lighting, high fashion magazine style."

                val builder = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("model", "gpt-image-1")
                    .addFormDataPart("prompt", prompt)
                    .addFormDataPart("n", "1")
                    .addFormDataPart("size", "1024x1536")
                builder.addFormDataPart("image[]", "face.jpg", faceBytes.toRequestBody("image/jpeg".toMediaType()))

                val req = Request.Builder()
                    .url("https://api.openai.com/v1/images/edits")
                    .header("Authorization", "Bearer $apiKey")
                    .post(builder.build())
                    .build()

                val resp = httpClient.newCall(req).execute()
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("Image generation failed ${resp.code}: $bodyStr"))

                val imgResp = Gson().fromJson(bodyStr, ImageGenerationResponse::class.java)
                val imgData = imgResp.data?.firstOrNull()
                    ?: return@withContext Result.failure(Exception("No image returned"))
                return@withContext when {
                    imgData.b64Json != null -> {
                        val bytes = Base64.decode(imgData.b64Json, Base64.DEFAULT)
                        java.io.FileOutputStream(outFile).use { it.write(bytes) }
                        Result.success(outFile.absolutePath)
                    }
                    imgData.url != null -> Result.success(imgData.url)
                    else -> Result.failure(Exception("No valid image data"))
                }
            }

            // Fallback: DALL-E 3 text-to-image (no user photo)
            val prompt = "Fashion editorial photography. ${bodyDesc}Full-length studio shot for a ${scene.label} occasion. " +
                "Outfit: $snippetWords. Clean white background, natural lighting, high fashion magazine style."
            val jsonBody = """{"model":"dall-e-3","prompt":${com.google.gson.Gson().toJson(prompt)},"n":1,"size":"1024x1024","quality":"standard","response_format":"b64_json"}"""
            val req = okhttp3.Request.Builder()
                .url("https://api.openai.com/v1/images/generations")
                .header("Authorization", "Bearer $apiKey")
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val resp = httpClient.newCall(req).execute()
            val bodyStr = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext Result.failure(Exception("Image generation failed ${resp.code}: $bodyStr"))

            val imgResp = com.google.gson.Gson().fromJson(bodyStr, ImageGenerationResponse::class.java)
            val imgData = imgResp.data?.firstOrNull()
                ?: return@withContext Result.failure(Exception("No image returned"))
            when {
                imgData.b64Json != null -> {
                    val bytes = android.util.Base64.decode(imgData.b64Json, android.util.Base64.DEFAULT)
                    java.io.FileOutputStream(outFile).use { it.write(bytes) }
                    Result.success(outFile.absolutePath)
                }
                imgData.url != null -> Result.success(imgData.url)
                else -> Result.failure(Exception("No valid image data"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Outfit image generation failed: ${e.message}"))
        }
    }

    private fun loadImageBytes(pathOrUrl: String): ByteArray? = try {
        if (pathOrUrl.startsWith("http")) {
            val req = Request.Builder().url(pathOrUrl).build()
            httpClient.newCall(req).execute().use { it.body?.bytes() }
        } else {
            File(pathOrUrl).takeIf { it.exists() }?.readBytes()
        }
    } catch (e: Exception) { null }

    private fun encodeImageToBase64(imagePath: String): String? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) return null
            val bitmap = BitmapFactory.decodeFile(imagePath) ?: return null
            val resized = resizeBitmap(bitmap, 512)
            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxSize && height <= maxSize) return bitmap
        val ratio = maxSize.toFloat() / maxOf(width, height)
        return Bitmap.createScaledBitmap(bitmap, (width * ratio).toInt(), (height * ratio).toInt(), true)
    }
}
