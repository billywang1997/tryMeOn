package com.example.myapplication.ui.screens.outfit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.BasicWardrobeProvider
import com.example.myapplication.data.remote.ScraperApiService
import com.example.myapplication.data.remote.AsosApiService
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.remote.EbayApiService
import com.example.myapplication.data.remote.EbayItem
import com.example.myapplication.data.remote.SerpApiService
import com.example.myapplication.data.remote.WeatherInfo
import com.example.myapplication.data.remote.WeatherService
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.data.repository.UserProfileRepository
import com.example.myapplication.domain.model.ClothingCategory
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.domain.model.Mood
import com.example.myapplication.domain.model.OutfitScene
import com.example.myapplication.domain.model.SavedImage
import com.example.myapplication.domain.model.UserProfile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── Complete My Look ──────────────────────────────────────────────────────────

data class CompleteLookRec(
    val category: ClothingCategory,
    val suggestion: String,
    val searchQuery: String,
    val reason: String,
    val wardrobeMatches: List<ClothingItem> = emptyList(),
    val essentialMatches: List<ClothingItem> = emptyList(),
    val onlineItems: List<EbayItem> = emptyList(),
    val onlineLoading: Boolean = false
)

private data class CompleteLookRecDto(
    val category: String = "",
    val suggestion: String = "",
    @SerializedName("searchQuery") val searchQuery: String = "",
    val reason: String = ""
)

// ── UI State ──────────────────────────────────────────────────────────────────

data class OutfitUiState(
    val wardrobe: List<ClothingItem> = emptyList(),
    val profile: UserProfile? = null,
    val selectedScene: OutfitScene = OutfitScene.DAILY,
    val suggestion: String = "",
    val isLoading: Boolean = false,
    val error: String = "",
    val weather: WeatherInfo? = null,
    val city: String = "",
    val weatherLoading: Boolean = false,
    val mood: Mood? = null,
    val outfitImagePath: String? = null,
    val imageLoading: Boolean = false,
    val imageSaved: Boolean = false,
    val promptSignInToBackup: Boolean = false,
    val closetOnly: Boolean = false,
    val outfitItems: List<ClothingItem> = emptyList(),
    val essentialsHints: List<String> = emptyList(),
    // Complete My Look
    val completeLookPinned: Map<ClothingCategory, ClothingItem?> = emptyMap(),
    val completeLookLoading: Boolean = false,
    val completeLookResults: List<CompleteLookRec> = emptyList(),
    val completeLookError: String = ""
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class OutfitViewModel(
    private val wardrobeRepository: WardrobeRepository,
    private val profileRepository: UserProfileRepository,
    private val claudeService: ClaudeApiService,
    private val apiKey: String,
    private val ebayService: EbayApiService? = null,
    private val asosService: AsosApiService? = null,
    private val amazonService: ScraperApiService? = null,
    private val ebayClientId: String = "",
    private val ebayClientSecret: String = "",
    private val rapidApiKey: String = "",
    private val serpService: SerpApiService? = null,
    private val scraperApiKey: String = "",
    private val serpApiKey: String = ""
) : ViewModel() {

    private val _uiState = MutableStateFlow(OutfitUiState())
    val uiState: StateFlow<OutfitUiState> = _uiState.asStateFlow()

    private val gson = Gson()

    init {
        viewModelScope.launch {
            wardrobeRepository.getAllClothing().collect { clothes ->
                _uiState.value = _uiState.value.copy(wardrobe = clothes)
            }
        }
        viewModelScope.launch {
            profileRepository.getProfile().collect { profile ->
                _uiState.value = _uiState.value.copy(profile = profile)
            }
        }
        fetchWeatherWithAutoDetect()
    }

    // ── Weather ───────────────────────────────────────────────────────────────

    private fun fetchWeatherWithAutoDetect() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(weatherLoading = true)
            val detectedCity = WeatherService.detectCity()
            val city = detectedCity.ifBlank { _uiState.value.city.ifBlank { "Sydney" } }
            _uiState.value = _uiState.value.copy(city = city)
            val weather = WeatherService.fetch(city)
            _uiState.value = _uiState.value.copy(weather = weather, weatherLoading = false)
        }
    }

    fun setCity(city: String) { _uiState.value = _uiState.value.copy(city = city) }

    fun fetchWeather() {
        val city = _uiState.value.city.ifBlank { "Sydney" }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(weatherLoading = true)
            val weather = WeatherService.fetch(city)
            _uiState.value = _uiState.value.copy(weather = weather, weatherLoading = false)
        }
    }

    // ── Occasion / Mood ───────────────────────────────────────────────────────

    fun selectScene(scene: OutfitScene) { _uiState.value = _uiState.value.copy(selectedScene = scene) }
    fun selectMood(mood: Mood?) { _uiState.value = _uiState.value.copy(mood = mood) }
    fun toggleClosetOnly() { _uiState.value = _uiState.value.copy(closetOnly = !_uiState.value.closetOnly) }
    fun dismissEssentialsHints() { _uiState.value = _uiState.value.copy(essentialsHints = emptyList()) }

    // ── Generate Outfit ───────────────────────────────────────────────────────

    fun generateOutfit() {
        val state = _uiState.value
        if (apiKey.isBlank()) {
            _uiState.value = state.copy(error = "Please set your API Key in the Profile tab")
            return
        }
        val clothesToUse = when {
            state.closetOnly -> state.wardrobe
            state.wardrobe.isEmpty() -> BasicWardrobeProvider.items
            else -> state.wardrobe
        }
        val weatherCtx = state.weather?.let { "${it.tempC}°C, ${it.descriptionZh}, humidity ${it.humidity}%" } ?: ""
        val moodCtx = state.mood?.let { "User's mood today: ${it.emoji} ${it.label}. Suggest an outfit that reflects or enhances this mood." } ?: ""

        _uiState.value = state.copy(isLoading = true, error = "", suggestion = "", outfitImagePath = null, imageSaved = false, promptSignInToBackup = false, essentialsHints = emptyList(), outfitItems = clothesToUse)
        viewModelScope.launch {
            try {
                val result = claudeService.generateOutfitSuggestion(
                    apiKey = apiKey,
                    clothes = clothesToUse,
                    scene = state.selectedScene,
                    profile = state.profile,
                    weatherContext = if (weatherCtx.isNotEmpty() || moodCtx.isNotEmpty()) "$weatherCtx $moodCtx".trim() else "",
                    closetOnly = state.closetOnly
                )
                val hints = if (state.closetOnly) buildEssentialsHints(state) else emptyList()
                val lower = result.lowercase()
                val mentioned = clothesToUse.filter { it.name.isNotEmpty() && lower.contains(it.name.lowercase()) }
                val pickedItems = if (mentioned.size >= 2) mentioned else clothesToUse
                _uiState.value = _uiState.value.copy(suggestion = result, isLoading = false, essentialsHints = hints, outfitItems = pickedItems)
                generateOutfitImage(result)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to get suggestions: ${e.message}", isLoading = false)
            }
        }
    }

    private fun generateOutfitImage(suggestion: String) {
        val state = _uiState.value
        _uiState.value = state.copy(imageLoading = true)
        val facePhoto = state.profile?.faceImagePath?.takeIf { it.isNotBlank() }
            ?: state.profile?.bodyImagePath?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            val result = claudeService.generateOutfitBoard(apiKey, state.selectedScene, suggestion, state.profile, facePhoto)
            _uiState.value = _uiState.value.copy(outfitImagePath = result.getOrNull(), imageLoading = false)
        }
    }

    fun saveOutfitImage() {
        val state = _uiState.value
        val path = state.outfitImagePath ?: return
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val items = if (state.wardrobe.isEmpty()) BasicWardrobeProvider.items else state.wardrobe
        val idList = items.take(12).joinToString(",") { it.id.toString() }
        viewModelScope.launch {
            val backedUp = wardrobeRepository.saveImage(
                SavedImage(
                    path = path, type = "look", label = state.selectedScene.label,
                    note = """{"d":"$date","s":"${state.selectedScene.label}","ids":[$idList]}"""
                )
            )
            _uiState.value = _uiState.value.copy(imageSaved = true, promptSignInToBackup = !backedUp)
        }
    }

    // ── Essentials Hints ─────────────────────────────────────────────────────────

    private fun buildEssentialsHints(state: OutfitUiState): List<String> = buildList {
        if (state.wardrobe.none { it.category == ClothingCategory.INNER })
            add("No tops in your wardrobe — check Essentials for a top to complete your look")
        if (state.wardrobe.none { it.category == ClothingCategory.PANTS } &&
            state.wardrobe.none { it.category == ClothingCategory.DRESS })
            add("No bottoms in your wardrobe — check Essentials for a bottom to complete your look")
        val tempC = state.weather?.tempC
        if (tempC != null && tempC <= 15 && state.wardrobe.none { it.category == ClothingCategory.OUTERWEAR })
            add("It's ${tempC}°C outside and you have no outerwear — check Essentials for a jacket")
    }

    // ── Complete My Look ──────────────────────────────────────────────────────

    private val allCategories = listOf(
        ClothingCategory.INNER, ClothingCategory.OUTERWEAR, ClothingCategory.PANTS,
        ClothingCategory.DRESS, ClothingCategory.SHOES, ClothingCategory.ACCESSORY, ClothingCategory.BAG
    )

    fun toggleCompleteLookCategory(category: ClothingCategory) {
        val current = _uiState.value.completeLookPinned.toMutableMap()
        if (current.containsKey(category)) current.remove(category) else current[category] = null
        _uiState.value = _uiState.value.copy(completeLookPinned = current, completeLookResults = emptyList(), completeLookError = "")
    }

    fun selectCompleteLookItem(category: ClothingCategory, item: ClothingItem?) {
        val current = _uiState.value.completeLookPinned.toMutableMap()
        current[category] = item
        _uiState.value = _uiState.value.copy(completeLookPinned = current)
    }

    fun completeMyLook() {
        val state = _uiState.value
        if (apiKey.isBlank()) {
            _uiState.value = state.copy(completeLookError = "Please set your API Key in the Profile tab")
            return
        }
        val pinned = state.completeLookPinned
        if (pinned.isEmpty()) {
            _uiState.value = state.copy(completeLookError = "Select at least one category you already have")
            return
        }
        val missing = allCategories.filter { it !in pinned }
        if (missing.isEmpty()) {
            _uiState.value = state.copy(completeLookError = "Leave at least one category for AI to recommend")
            return
        }
        _uiState.value = state.copy(completeLookLoading = true, completeLookError = "", completeLookResults = emptyList())
        viewModelScope.launch {
            try {
                val raw = claudeService.getCompleteLookRecommendations(apiKey, pinned, missing, state.profile)
                val recs = parseCompleteLookRecs(raw, missing)

                // Seed with wardrobe/essentials matches immediately
                val initial = recs.map { rec ->
                    rec.copy(
                        wardrobeMatches = state.wardrobe
                            .filter { it.category == rec.category && matchesQuery(it, rec.suggestion) }
                            .take(6)
                            .ifEmpty { state.wardrobe.filter { it.category == rec.category }.take(3) },
                        essentialMatches = BasicWardrobeProvider.byCategory(rec.category)
                            .filter { matchesQuery(it, rec.suggestion) }
                            .take(6)
                            .ifEmpty { BasicWardrobeProvider.byCategory(rec.category).take(3) },
                        onlineLoading = true
                    )
                }
                _uiState.value = _uiState.value.copy(completeLookLoading = false, completeLookResults = initial)

                // Search online for each rec in parallel
                initial.forEachIndexed { index, rec ->
                    launch {
                        val online = searchOnline(rec.searchQuery)
                        val updated = _uiState.value.completeLookResults.toMutableList()
                        if (index < updated.size) {
                            updated[index] = updated[index].copy(onlineItems = online, onlineLoading = false)
                            _uiState.value = _uiState.value.copy(completeLookResults = updated.toList())
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    completeLookLoading = false,
                    completeLookError = "Failed: ${e.message}"
                )
            }
        }
    }

    fun clearCompleteLook() {
        _uiState.value = _uiState.value.copy(
            completeLookPinned = emptyMap(),
            completeLookResults = emptyList(),
            completeLookError = ""
        )
    }

    private suspend fun searchOnline(query: String): List<EbayItem> {
        val ebayD   = viewModelScope.async { ebayService?.search(ebayClientId, ebayClientSecret, query, limit = 6) }
        val asosD   = viewModelScope.async { asosService?.search(rapidApiKey, query, limit = 5) }
        val amazonD = viewModelScope.async { amazonService?.search(scraperApiKey, query, limit = 5) }
        val serpD   = viewModelScope.async { serpService?.search(serpApiKey, query, limit = 8) }
        // Affiliate.wrap skips eBay/Amazon sources (those use their own partner programs).
        fun wrap(items: List<EbayItem>) = items.map {
            it.copy(itemWebUrl = com.example.myapplication.util.Affiliate.wrap(it.itemWebUrl, it.source))
        }
        return wrap(ebayD.await()?.getOrNull() ?: emptyList()) +
               wrap(asosD.await()?.getOrNull() ?: emptyList()) +
               wrap(amazonD.await()?.getOrNull() ?: emptyList()) +
               wrap(serpD.await()?.getOrNull() ?: emptyList())
    }

    private fun matchesQuery(item: ClothingItem, suggestion: String): Boolean {
        val keywords = suggestion.lowercase().split(" ", ",", "-", "_").filter { it.length > 2 }
        val text = "${item.name} ${item.color} ${item.brand} ${item.notes}".lowercase()
        return keywords.any { kw -> text.contains(kw) }
    }

    private fun parseCompleteLookRecs(json: String, fallback: List<ClothingCategory>): List<CompleteLookRec> {
        return try {
            val start = json.indexOf('[')
            val end = json.lastIndexOf(']') + 1
            if (start < 0 || end <= start) return emptyList()
            val type = object : TypeToken<List<CompleteLookRecDto>>() {}.type
            val dtos: List<CompleteLookRecDto> = gson.fromJson(json.substring(start, end), type) ?: emptyList()
            dtos.mapNotNull { dto ->
                val cat = try { ClothingCategory.valueOf(dto.category) } catch (_: Exception) { return@mapNotNull null }
                CompleteLookRec(
                    category = cat,
                    suggestion = dto.suggestion,
                    searchQuery = dto.searchQuery.ifBlank { dto.suggestion },
                    reason = dto.reason
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}
