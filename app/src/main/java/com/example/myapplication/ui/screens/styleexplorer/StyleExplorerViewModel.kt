package com.example.myapplication.ui.screens.styleexplorer

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.BasicWardrobeProvider
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.domain.model.ClothingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FashionStyle(
    val id: String,
    val name: String,
    val emoji: String,
    val tagline: String,
    val keywords: String,
    val tags: List<String>,
    val colorA: Color,
    val colorB: Color
)

val ALL_STYLES = listOf(
    FashionStyle("minimal", "Minimalist", "🖤", "Less is more",
        "Neutral colors, basics, clean lines, high-quality fabric, max 3-color outfits",
        listOf("Black & White", "Basics", "No prints", "Clean"),
        Color(0xFF1A1A1A), Color(0xFF757575)),

    FashionStyle("streetwear", "Streetwear", "🎧", "Attitude is the outfit",
        "Oversized silhouettes, athletic elements, hoodies, sneakers, big logos, layering",
        listOf("Oversized", "Athletic", "Sneakers", "Cool"),
        Color(0xFF0D47A1), Color(0xFF311B92)),

    FashionStyle("french", "French Elegance", "🥐", "Effortless chic",
        "Stripes, trench coat, beret, simple cuts, navy blue, white shirt, cigarette pants",
        listOf("Stripes", "Trench coat", "Refined", "Relaxed"),
        Color(0xFF1565C0), Color(0xFFC62828)),

    FashionStyle("academia", "Academia", "📚", "Elegant & intellectual",
        "Plaid, blazer, turtleneck sweater, Oxford shoes, vest, earth tones",
        listOf("Plaid", "Blazer", "Intellectual", "Vintage"),
        Color(0xFF4E342E), Color(0xFF827717)),

    FashionStyle("romantic", "Romantic", "🌸", "Softness is strength",
        "Floral prints, lace, pink tones, ruffles, dresses, bows, lightweight fabrics",
        listOf("Floral", "Dresses", "Pink", "Feminine"),
        Color(0xFFE91E8C), Color(0xFFFF8A65)),

    FashionStyle("athleisure", "Athleisure", "🏃", "Comfort is fashion",
        "Joggers, sweatshirts, sneakers, yoga pants, color-block design, performance fabrics",
        listOf("Athletic", "Comfortable", "Casual", "Energetic"),
        Color(0xFF1B5E20), Color(0xFF0288D1)),

    FashionStyle("vintage", "Vintage", "🎞️", "Beauty through time",
        "Retro prints, high-waist designs, flare pants, leather elements, earth tones, 70s–90s style",
        listOf("Retro", "High-waist", "Earth tones", "Classic"),
        Color(0xFF6D4C41), Color(0xFFBF360C)),

    FashionStyle("dark", "Dark/Gothic", "🖤", "Black is an attitude",
        "All-black outfits, leather, metal accessories, layering, deconstructed design, sharp cuts",
        listOf("All-black", "Leather", "Metallic", "Sharp"),
        Color(0xFF212121), Color(0xFF37474F)),

    FashionStyle("japanese", "Japanese Minimalism", "🌿", "Clean & natural",
        "Earth tones, cotton & linen, relaxed silhouettes, simple prints, layered styling, natural feel",
        listOf("Cotton/Linen", "Natural", "Relaxed", "Fresh"),
        Color(0xFF795548), Color(0xFF558B2F)),

    FashionStyle("business", "Business Elite", "💼", "Professional with style",
        "Suit sets, dress shirts, neutral colors, refined accessories, dress shoes, sharp cuts",
        listOf("Suits", "Professional", "Sharp", "Refined"),
        Color(0xFF1A237E), Color(0xFF37474F)),

    FashionStyle("coastal", "Coastal/Beach", "🌊", "Always on vacation",
        "Prints, linen, straw hat, relaxed shorts, sandals, fresh color palette, beachy vibes",
        listOf("Prints", "Linen", "Breezy", "Vacation"),
        Color(0xFF0277BD), Color(0xFF00695C)),

    FashionStyle("boho", "Boho", "🌻", "Free spirit",
        "Fringe, ethnic prints, layered jewelry, embroidery, flowy maxi skirts, lots of accessories",
        listOf("Ethnic prints", "Accessories", "Free", "Mixed"),
        Color(0xFF6A1B9A), Color(0xFFE65100))
)

data class StyleExplorerUiState(
    val wardrobe: List<ClothingItem> = emptyList(),
    val results: Map<String, String> = emptyMap(),
    val loading: Set<String> = emptySet(),
    val expandedStyle: String? = null
)

class StyleExplorerViewModel(
    private val wardrobeRepository: WardrobeRepository,
    private val claudeService: ClaudeApiService,
    private val apiKey: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(StyleExplorerUiState())
    val uiState: StateFlow<StyleExplorerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            wardrobeRepository.getAllClothing().collect { items ->
                _uiState.value = _uiState.value.copy(wardrobe = items)
            }
        }
    }

    fun expand(styleId: String) {
        val current = _uiState.value.expandedStyle
        _uiState.value = _uiState.value.copy(
            expandedStyle = if (current == styleId) null else styleId
        )
        if (current != styleId && !_uiState.value.results.containsKey(styleId)) {
            analyze(styleId)
        }
    }

    private fun analyze(styleId: String) {
        val style = ALL_STYLES.find { it.id == styleId } ?: return
        val clothes = _uiState.value.wardrobe.ifEmpty { BasicWardrobeProvider.items }
        _uiState.value = _uiState.value.copy(
            loading = _uiState.value.loading + styleId
        )
        viewModelScope.launch {
            try {
                val result = claudeService.matchStyleToWardrobe(
                    apiKey, style.name, style.keywords, clothes
                )
                _uiState.value = _uiState.value.copy(
                    results = _uiState.value.results + (styleId to result),
                    loading = _uiState.value.loading - styleId
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    results = _uiState.value.results + (styleId to "Analysis failed: ${e.message}"),
                    loading = _uiState.value.loading - styleId
                )
            }
        }
    }
}
