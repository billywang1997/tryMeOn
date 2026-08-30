package com.trymeon.app.ui.screens.tryon

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trymeon.app.data.BasicWardrobeProvider
import com.trymeon.app.data.remote.ScraperApiService
import com.trymeon.app.data.remote.ClaudeApiService
import com.trymeon.app.data.remote.EbayItem
import com.trymeon.app.data.remote.ReplicateApiService
import com.trymeon.app.data.remote.SerpApiService
import com.trymeon.app.data.remote.UnsplashService
import com.trymeon.app.data.repository.UserProfileRepository
import com.trymeon.app.data.tryon.VirtualModelStore
import com.trymeon.app.data.repository.WardrobeRepository
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.model.SavedImage
import com.trymeon.app.domain.model.UserProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private fun normalizeGender(raw: String): String = when (raw.trim().lowercase()) {
    "male", "m" -> "Male"
    "female", "f" -> "Female"
    else -> raw
}

enum class GarmentTab { WARDROBE, ESSENTIALS, EBAY }

data class EbayTryOnCategory(
    val name: String,
    val reason: String,
    val fashnCategory: String,
    val searchQuery: String,
    val items: List<EbayItem> = emptyList(),
    val loading: Boolean = false,
    val error: String = "",
    /** Written by the planner in the same call; empty falls back to translating. */
    val chineseQuery: String = ""
)

data class EssentialsCategoryState(
    val category: ClothingCategory,
    val items: List<ClothingItem>,
    val label: String = ""
)

// Unified cross-tab selection entry — one per garment slot (category)
/** A garment ready to render: where its image is, what it is, and where it goes. */
data class Garment(
    val path: String,
    val label: String,
    /** Null falls back to reading the label, which is a guess. */
    val fashnCategory: String?
)

data class SelectedGarment(
    val slotKey: String,         // ClothingCategory.name
    val source: GarmentTab,
    val clothingItem: ClothingItem? = null,
    val ebayItem: EbayItem? = null,
    val ebayCategory: String = ""
) {
    /** The slot this fills, when it names a real category. */
    val slotCategory: ClothingCategory?
        get() = ClothingCategory.entries.firstOrNull { it.name == slotKey }

    val displayLabel: String get() = when {
        clothingItem != null -> clothingItem.name.ifEmpty { clothingItem.category.label }
        ebayItem != null -> ebayItem.title.take(24)
        else -> ""
    }
}

data class TryOnUiState(
    val wardrobe: List<ClothingItem> = emptyList(),
    val profile: UserProfile? = null,
    val garmentTab: GarmentTab = GarmentTab.WARDROBE,
    // Unified cross-tab selection: slotKey (ClothingCategory.name) → SelectedGarment
    val selectedGarments: Map<String, SelectedGarment> = emptyMap(),
    // eBay tab
    val ebayCategories: List<EbayTryOnCategory> = emptyList(),
    val ebayRecoLoading: Boolean = false,
    val ebayRecoError: String = "",
    // Essentials tab
    val essentialsCategories: List<EssentialsCategoryState> = emptyList(),
    // Shop Similar (triggered by tapping essentials item)
    val shopSimilarItem: ClothingItem? = null,
    val shopSimilarResults: List<EbayItem> = emptyList(),
    val shopSimilarLoading: Boolean = false,
    val shopSimilarError: String = "",
    // Common
    val userPhotoPath: String = "",
    val resultViews: List<String> = emptyList(),
    val generatingViews: Boolean = false,
    val analysis: String = "",
    val isLoading: Boolean = false,
    val loadingStep: String = "",
    val error: String = "",
    val imageSaved: Boolean = false,
    val promptSignInToBackup: Boolean = false,
    /** The kept portrait every try-on is dressed from, once one exists. */
    val virtualModelPath: String = "",
    val buildingModel: Boolean = false,
    val inferredGender: String = "",
    val inferringGender: Boolean = false,
    val favoriteEssentialIds: Set<Long> = emptySet(),
    val favoriteEbayIds: Set<String> = emptySet()
) {
    // "Other" is treated as unset — inferred gender from wardrobe/photo takes precedence
    val effectiveGender: String get() {
        val pg = normalizeGender(profile?.gender ?: "")
        return if (pg == "Male" || pg == "Female") pg
        else normalizeGender(inferredGender)
    }
    val resultImageUrl: String get() = resultViews.firstOrNull() ?: ""

    /** Garment credits printed on the share card, in a stable slot order. */
    val shareCredits: List<com.trymeon.app.share.ShareCardRenderer.Credit>
        get() = ClothingCategory.entries.mapNotNull { category ->
            val garment = selectedGarments[category.name] ?: return@mapNotNull null
            com.trymeon.app.share.ShareCardRenderer.Credit(
                slot = category.label,
                label = garment.displayLabel,
                source = when (garment.source) {
                    GarmentTab.WARDROBE   -> "Closet"
                    GarmentTab.ESSENTIALS -> "Essential"
                    GarmentTab.EBAY       -> "Secondhand"
                }
            )
        }
    val selectedClothingIds: Set<Long> get() = selectedGarments.values.mapNotNull { it.clothingItem?.id }.toSet()
    val selectedEbayIds: Set<String> get() = selectedGarments.values.mapNotNull { it.ebayItem?.itemId }.toSet()
}

class TryOnViewModel(
    private val wardrobeRepository: WardrobeRepository,
    private val profileRepository: UserProfileRepository,
    private val claudeService: ClaudeApiService,
    private val replicateService: ReplicateApiService,
    private val openAiKey: String,
    private val replicateKey: String,
    private val ebayAffiliateCampaignId: String = "",
    private val amazonAssociateTag: String = "",
    private val styleKeywords: Set<String> = emptySet(),
    /** Null falls back to generating the person alongside the clothes each time. */
    private val virtualModels: VirtualModelStore? = null,
    /** Required: a missing catalog is an empty shop tab with no other symptom. */
    private val catalog: com.trymeon.app.data.sourcing.ShoppingCatalog,
    /** Read per plan, so a change in Profile applies to the next refresh. */
    private val priceExpectation: () -> com.trymeon.app.domain.sourcing.PriceExpectation =
        { com.trymeon.app.domain.sourcing.PriceExpectation.DEFAULT }
) : ViewModel() {

    private val _uiState = MutableStateFlow(TryOnUiState())
    val uiState: StateFlow<TryOnUiState> = _uiState.asStateFlow()

    init {
        // A garment picked in the sourcing tab is why the user came here.
        com.trymeon.app.data.sourcing.PendingTryOn.consume()?.let {
            selectExternalGarment(it.item, it.category)
        }
        viewModelScope.launch {
            wardrobeRepository.getAllClothing().collect { clothes ->
                _uiState.value = _uiState.value.copy(wardrobe = clothes)
                if (clothes.isNotEmpty() && needsGenderInference()) {
                    // Apply instant local heuristic synchronously first
                    val localGender = inferGenderLocally(clothes)
                    if (localGender.isNotEmpty() && _uiState.value.inferredGender.isEmpty()) {
                        _uiState.value = _uiState.value.copy(inferredGender = localGender)
                        if (_uiState.value.garmentTab == GarmentTab.EBAY) loadEbayRecommendations()
                    }
                    // Then kick off API inference for higher accuracy
                    inferGenderFromWardrobeItems(clothes)
                }
            }
        }
        viewModelScope.launch {
            profileRepository.getProfile().collect { profile ->
                val prevGender = _uiState.value.effectiveGender
                _uiState.value = _uiState.value.copy(profile = profile)
                ensureModelForCurrentPhoto()
                val newGender = _uiState.value.effectiveGender
                if (newGender != prevGender && newGender.isNotEmpty()) loadEssentials(newGender)
                // Infer gender from existing profile photo if gender not set
                val photo = profile?.bodyImagePath?.takeIf { it.isNotEmpty() }
                    ?: profile?.faceImagePath?.takeIf { it.isNotEmpty() }
                if (photo != null && needsGenderInference()) {
                    inferGenderFromPhoto(photo)
                }
            }
        }
        loadEssentials()
    }

    private fun loadEssentials(gender: String = _uiState.value.effectiveGender) {
        val g = gender.ifBlank { "Female" }
        val result = mutableListOf<EssentialsCategoryState>()
        result.add(EssentialsCategoryState(ClothingCategory.INNER, BasicWardrobeProvider.innerTops(g), "Tops"))
        val shirts = BasicWardrobeProvider.innerShirts(g)
        if (shirts.isNotEmpty()) result.add(EssentialsCategoryState(ClothingCategory.INNER, shirts, "Shirts"))
        for (cat in listOf(ClothingCategory.OUTERWEAR, ClothingCategory.PANTS, ClothingCategory.DRESS, ClothingCategory.SHOES, ClothingCategory.BAG)) {
            result.add(EssentialsCategoryState(cat, BasicWardrobeProvider.byGenderAndCategory(g, cat)))
        }
        _uiState.value = _uiState.value.copy(essentialsCategories = result)
    }

    // ── Tab ──────────────────────────────────────────────────────────────

    fun setGarmentTab(tab: GarmentTab) {
        _uiState.value = _uiState.value.copy(garmentTab = tab, resultViews = emptyList(), analysis = "", error = "")
        if (tab == GarmentTab.EBAY && _uiState.value.ebayCategories.isEmpty()) {
            loadEbayRecommendations()
        }
    }

    // ── Unified cross-tab selection ───────────────────────────────────────

    fun toggleClothingItem(item: ClothingItem, source: GarmentTab) {
        val slot = item.category.name
        val current = _uiState.value.selectedGarments.toMutableMap()
        val existing = current[slot]
        if (existing?.clothingItem?.id == item.id && existing.source == source) {
            current.remove(slot)
        } else {
            current[slot] = SelectedGarment(slot, source, clothingItem = item)
        }
        _uiState.value = _uiState.value.copy(selectedGarments = current, resultViews = emptyList(), error = "")
    }

    // Keep old name for Wardrobe tab backward compat
    fun toggleItem(item: ClothingItem) = toggleClothingItem(item, GarmentTab.WARDROBE)

    /**
     * Preselect a garment chosen on another screen.
     *
     * Takes the category directly rather than a display name: the sourcing flow
     * already had the model classify the item, and re-deriving it from a Chinese
     * listing title would only lose that.
     */
    fun selectExternalGarment(item: EbayItem, category: ClothingCategory) {
        val current = _uiState.value.selectedGarments.toMutableMap()
        current[category.name] = SelectedGarment(
            slotKey = category.name,
            source = GarmentTab.EBAY,
            ebayItem = item,
            ebayCategory = category.label
        )
        _uiState.value = _uiState.value.copy(
            selectedGarments = current,
            garmentTab = GarmentTab.EBAY,
            resultViews = emptyList(),
            error = ""
        )
    }

    fun toggleEbayItem(item: EbayItem, categoryName: String) {
        val slot = ebayNameToSlot(categoryName)
        val current = _uiState.value.selectedGarments.toMutableMap()
        if (current[slot]?.ebayItem?.itemId == item.itemId) {
            current.remove(slot)
        } else {
            current[slot] = SelectedGarment(slot, GarmentTab.EBAY, ebayItem = item, ebayCategory = categoryName)
        }
        _uiState.value = _uiState.value.copy(selectedGarments = current, resultViews = emptyList(), error = "")
    }

    // ── Essentials ────────────────────────────────────────────────────────

    fun refreshEssentialsCategory(category: ClothingCategory) {
        val gender = _uiState.value.effectiveGender
        val cats = _uiState.value.essentialsCategories.toMutableList()
        if (category == ClothingCategory.INNER) {
            for (i in cats.indices) {
                if (cats[i].category == ClothingCategory.INNER) {
                    val pool = if (cats[i].label == "Shirts") BasicWardrobeProvider.innerShirts(gender)
                               else BasicWardrobeProvider.innerTops(gender)
                    cats[i] = cats[i].copy(items = pool.shuffled())
                }
            }
        } else {
            val idx = cats.indexOfFirst { it.category == category }
            if (idx >= 0) {
                val pool = BasicWardrobeProvider.byGenderAndCategory(gender, category)
                cats[idx] = cats[idx].copy(items = pool.shuffled())
            }
        }
        _uiState.value = _uiState.value.copy(essentialsCategories = cats)
    }

    // ── Shop Similar ──────────────────────────────────────────────────────

    fun shopForSimilar(item: ClothingItem) {
        _uiState.value = _uiState.value.copy(
            shopSimilarItem = item, shopSimilarLoading = true,
            shopSimilarResults = emptyList(), shopSimilarError = ""
        )
        viewModelScope.launch {
            val rawQuery = listOf(item.color, item.name.ifEmpty { item.category.label }).filter { it.isNotBlank() }.joinToString(" ")
            val query = ensureGenderInQuery(rawQuery)
            Log.d("TryOnVM", "shopSimilar: rawQuery='$rawQuery' → finalQuery='$query' effectiveGender=${_uiState.value.effectiveGender} profileGender=${_uiState.value.profile?.gender}")
            val combined = catalog.search(
                englishQuery = query,
                gender = _uiState.value.effectiveGender,
                categoryHint = item.category,
                limit = 16
            )
            _uiState.value = _uiState.value.copy(
                shopSimilarLoading = false,
                shopSimilarResults = combined,
                shopSimilarError = if (combined.isEmpty()) "No results found" else ""
            )
        }
    }

    fun closeShopSimilar() {
        _uiState.value = _uiState.value.copy(
            shopSimilarItem = null, shopSimilarResults = emptyList(),
            shopSimilarError = "", shopSimilarLoading = false
        )
    }

    // ── eBay recommendations ─────────────────────────────────────────────

    fun refreshEbayRecommendations() = loadEbayRecommendations()

    fun refreshEbayCategory(index: Int) {
        val cat = _uiState.value.ebayCategories.getOrNull(index) ?: return
        viewModelScope.launch { fetchEbayCategoryItems(index, cat.searchQuery) }
    }

    private fun loadEbayRecommendations() {
        if (openAiKey.isBlank()) return
        // Sync local heuristic before any API call — so gender is always populated
        if (_uiState.value.effectiveGender.isEmpty()) {
            val localGender = inferGenderLocally(_uiState.value.wardrobe)
            if (localGender.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(inferredGender = localGender)
            }
        }
        val genderForLog = _uiState.value.effectiveGender
        val profileGenderForLog = _uiState.value.profile?.gender ?: "null"
        Log.d("TryOnVM", "loadReco: profileGender=$profileGenderForLog inferredGender=${_uiState.value.inferredGender} effectiveGender=$genderForLog styleKeywords=$styleKeywords")
        _uiState.value = _uiState.value.copy(ebayRecoLoading = true, ebayRecoError = "", ebayCategories = emptyList())
        viewModelScope.launch {
            val clothes = wardrobeRepository.getAllClothing().first().takeIf { it.isNotEmpty() }
                ?: BasicWardrobeProvider.items
            val picked = _uiState.value.selectedGarments.values.map {
                "${it.slotKey.lowercase()}: ${it.displayLabel}"
            }
            val raw = try {
                claudeService.tryOnWardrobePlan(
                    openAiKey, clothes, picked, _uiState.value.effectiveGender, styleKeywords,
                    priceHint = priceExpectation().stylistHint,
                    bodyHint = com.trymeon.app.domain.sourcing.BodyHints.describe(_uiState.value.profile)
                )
            }
            catch (e: Exception) {
                _uiState.value = _uiState.value.copy(ebayRecoLoading = false, ebayRecoError = "Failed to load recommendations: ${e.message}")
                return@launch
            }
            val cats = parseEbayCategories(raw)
            if (cats.isEmpty()) {
                _uiState.value = _uiState.value.copy(ebayRecoLoading = false, ebayRecoError = "No recommendations yet — add items to your wardrobe first")
                return@launch
            }
            _uiState.value = _uiState.value.copy(ebayRecoLoading = false, ebayCategories = cats)
            cats.forEachIndexed { i, cat -> launch { fetchEbayCategoryItems(i, cat.searchQuery) } }
        }
    }

    private suspend fun fetchEbayCategoryItems(index: Int, rawQuery: String) {
        updateEbayCategory(index) { it.copy(loading = true) }
        val query = ensureGenderInQuery(rawQuery)
        // One source now, and the price on the card is what it costs delivered
        // rather than a sticker in someone else's currency.
        val category = _uiState.value.ebayCategories.getOrNull(index)
        val items = catalog.search(
            englishQuery = query,
            gender = _uiState.value.effectiveGender,
            categoryHint = slotToCategory(category?.name),
            limit = 16,
            chineseQuery = category?.chineseQuery
        )
        Log.d("TryOnVM", "category[$index] '$query' -> ${items.size} landed listings")
        updateEbayCategory(index) {
            it.copy(
                loading = false,
                items = items,
                error = if (items.isEmpty()) "No matches on Taobao for this one" else ""
            )
        }
    }

    /** The plan speaks in slot names; sourcing wants a wardrobe category. */
    private fun slotToCategory(slot: String?): ClothingCategory? = when (slot?.lowercase()) {
        "top" -> ClothingCategory.INNER
        "jacket" -> ClothingCategory.OUTERWEAR
        "bottoms" -> ClothingCategory.PANTS
        "set" -> ClothingCategory.DRESS
        "shoes" -> ClothingCategory.SHOES
        "bag" -> ClothingCategory.BAG
        else -> null
    }

    private fun updateEbayCategory(index: Int, block: (EbayTryOnCategory) -> EbayTryOnCategory) {
        val list = _uiState.value.ebayCategories.toMutableList()
        if (index < list.size) list[index] = block(list[index])
        _uiState.value = _uiState.value.copy(ebayCategories = list)
    }

    // ── Photo ────────────────────────────────────────────────────────────

    /**
     * Build the portrait as soon as there is a photo to build it from, rather
     * than on the first try-on. It is something the user owns and should be
     * able to look at before committing to an outfit — and doing it here keeps
     * the wait out of the try-on itself.
     */
    private fun ensureModelForCurrentPhoto() {
        val store = virtualModels ?: return
        val state = _uiState.value
        val photo = state.userPhotoPath.takeIf { it.isNotEmpty() }
            ?: state.profile?.bodyImagePath?.takeIf { it.isNotEmpty() }
            ?: state.profile?.faceImagePath?.takeIf { it.isNotEmpty() }
            ?: return
        if (state.buildingModel) return
        if (store.isFresh(state.profile, photo)) {
            store.current()?.let {
                _uiState.value = _uiState.value.copy(virtualModelPath = it.imagePath)
            }
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(buildingModel = true)
            val model = store.ensure(state.profile, photo)
            _uiState.value = _uiState.value.copy(
                buildingModel = false,
                virtualModelPath = model?.imagePath.orEmpty()
            )
        }
    }

    /** Throw the portrait away and build a new one — for when they dislike it. */
    fun regenerateModel() {
        val store = virtualModels ?: return
        val state = _uiState.value
        val photo = state.userPhotoPath.takeIf { it.isNotEmpty() }
            ?: state.profile?.bodyImagePath?.takeIf { it.isNotEmpty() }
            ?: state.profile?.faceImagePath?.takeIf { it.isNotEmpty() }
            ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(buildingModel = true)
            val model = store.regenerate(state.profile, photo)
            _uiState.value = _uiState.value.copy(
                buildingModel = false,
                virtualModelPath = model?.imagePath.orEmpty(),
                resultViews = emptyList()
            )
        }
    }

    fun setUserPhoto(context: Context, uri: Uri) {
        viewModelScope.launch {
            val path = savePhoto(context, uri) ?: return@launch
            _uiState.value = _uiState.value.copy(userPhotoPath = path, error = "")
            // A new photo means the old portrait is no longer this person.
            ensureModelForCurrentPhoto()
            if (needsGenderInference()) {
                inferGenderFromPhoto(path)
            }
        }
    }

    /** Hard override: user taps a gender chip in the UI. Clears and reloads recommendations. */
    fun overrideGender(gender: String) {
        _uiState.value = _uiState.value.copy(
            inferredGender = gender,
            ebayCategories = emptyList(),
            ebayRecoError = ""
        )
        if (_uiState.value.garmentTab == GarmentTab.EBAY) loadEbayRecommendations()
    }

    /**
     * Remove items whose title clearly belongs to the opposite gender.
     * Google Shopping and eBay don't filter by gender even with gender keywords in the query.
     */
    private fun filterByGender(items: List<EbayItem>): List<EbayItem> {
        val gender = _uiState.value.effectiveGender
        if (gender.isEmpty() || gender == "Other") return items
        return items.filter { item ->
            val t = item.title.lowercase()
            when (gender) {
                "Male" -> !t.contains("women") && !t.contains("woman") &&
                          !t.contains("ladies") && !t.contains("girl") &&
                          !t.contains("female") && !t.contains("femme")
                "Female" -> !t.contains(" men ") && !t.contains("men's") &&
                            !t.contains("mens ") && !t.contains("boys ") &&
                            !t.contains(" male ") && !t.contains("masculin")
                else -> true
            }
        }
    }

    /** Instant, no-API gender guess from wardrobe content. Returns "" if inconclusive. */
    private fun inferGenderLocally(clothes: List<ClothingItem>): String {
        if (clothes.isEmpty()) return ""
        val text = clothes.joinToString(" ") { "${it.name} ${it.notes ?: ""}".lowercase() }
        val hasDress = clothes.any { it.category == ClothingCategory.DRESS }
        val femaleHits = listOf(
            "dress", "skirt", "blouse", "bra", "bikini", "sundress", "midi", "maxi",
            "lingerie", "romper", "camisole", "cami", "floral", "lace", "heel", "pump", "culottes"
        ).count { text.contains(it) } + if (hasDress) 3 else 0
        val maleHits = listOf(
            "suit", "tie", "chino", "blazer", "tuxedo", "waistcoat", "boxer",
            "polo shirt", "oxford shirt", "loafer", "derby"
        ).count { text.contains(it) }
        return when {
            femaleHits >= 2 && femaleHits > maleHits -> "Female"
            maleHits >= 1 && maleHits > femaleHits   -> "Male"
            else -> ""
        }
    }

    private fun needsGenderInference(): Boolean {
        val state = _uiState.value
        val profileGender = state.profile?.gender ?: ""
        // Inference not needed if profile has a real gender choice, or if already inferred/inferring
        return (profileGender.isEmpty() || profileGender == "Other") &&
               state.inferredGender.isEmpty() && !state.inferringGender
    }

    private fun inferGenderFromWardrobeItems(clothes: List<ClothingItem>) {
        if (openAiKey.isBlank()) return
        _uiState.value = _uiState.value.copy(inferringGender = true)
        viewModelScope.launch {
            val gender = try { claudeService.inferGenderFromWardrobe(openAiKey, clothes) } catch (e: Exception) { "" }
            if (gender.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(inferredGender = gender, inferringGender = false)
                // Use effectiveGender so profile gender always takes priority over inferred
                loadEssentials(_uiState.value.effectiveGender)
                if (_uiState.value.garmentTab == GarmentTab.EBAY) loadEbayRecommendations()
            } else {
                _uiState.value = _uiState.value.copy(inferringGender = false)
            }
        }
    }

    private fun inferGenderFromPhoto(imagePath: String) {
        if (openAiKey.isBlank()) return
        _uiState.value = _uiState.value.copy(inferringGender = true)
        viewModelScope.launch {
            val gender = try { claudeService.inferGenderFromPhoto(openAiKey, imagePath) } catch (e: Exception) { "" }
            if (gender.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(inferredGender = gender, inferringGender = false)
                // Use effectiveGender so profile gender always takes priority over inferred
                loadEssentials(_uiState.value.effectiveGender)
                if (_uiState.value.garmentTab == GarmentTab.EBAY) loadEbayRecommendations()
            } else {
                _uiState.value = _uiState.value.copy(inferringGender = false)
            }
        }
    }

    // ── Try-on ────────────────────────────────────────────────────────────

    fun startTryOn() {
        val state = _uiState.value
        val userImagePath = state.userPhotoPath.takeIf { it.isNotEmpty() }
            ?: state.profile?.bodyImagePath?.takeIf { it.isNotEmpty() }
            ?: state.profile?.faceImagePath?.takeIf { it.isNotEmpty() }
        if (userImagePath == null) { _uiState.value = state.copy(error = "Please upload a photo first"); return }
        if (state.selectedGarments.isEmpty()) { _uiState.value = state.copy(error = "Please select at least one garment"); return }
        launchUnifiedTryOn(state, userImagePath)
    }

    private fun launchUnifiedTryOn(state: TryOnUiState, userImagePath: String) {
        _uiState.value = state.copy(
            isLoading = true, error = "", resultViews = emptyList(),
            generatingViews = false, analysis = "", loadingStep = "Preparing garments…"
        )
        val bodyRef = state.profile?.bodyImagePath?.takeIf { it.isNotEmpty() && it != userImagePath }

        viewModelScope.launch {
            val garments = state.selectedGarments.values.mapNotNull { garment ->
                when {
                    garment.clothingItem != null -> {
                        val path = resolveImagePath(garment.clothingItem.imagePath)
                        if (path.isBlank()) null
                        else {
                            val label = listOf(garment.clothingItem.color, garment.clothingItem.category.label, garment.clothingItem.name)
                                .filter { it.isNotBlank() }.joinToString(" ")
                            Garment(path, label, garment.slotCategory?.fashnCategory)
                        }
                    }
                    garment.ebayItem != null -> {
                        val url = garment.ebayItem.imageUrl.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                        Garment(url, garment.ebayItem.title.take(40), garment.slotCategory?.fashnCategory)
                    }
                    else -> null
                }
            }

            if (garments.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, loadingStep = "", error = "No valid garment images found")
                return@launch
            }

            // Build the portrait once; every angle and every future try-on is
            // dressed from it, so the person stays the same and only the clothes
            // are paid for again.
            val portrait = virtualModels?.let { store ->
                if (!store.isFresh(state.profile, userImagePath)) {
                    _uiState.value = _uiState.value.copy(
                        buildingModel = true, loadingStep = "Building your model…"
                    )
                }
                store.ensure(state.profile, userImagePath).also {
                    _uiState.value = _uiState.value.copy(
                        buildingModel = false, virtualModelPath = it?.imagePath.orEmpty()
                    )
                }
            }

            _uiState.value = _uiState.value.copy(loadingStep = "Dressing your model…")

            // FASHN transfers a garment onto a given person instead of drawing a
            // new one, so the face survives. It dresses one item at a time, which
            // suits layering: each step starts from the previous result, and the
            // person is carried through the whole chain.
            val base = portrait?.imagePath ?: userImagePath
            val front = dressWithFashn(base, garments)
                ?: claudeService.generateTryOnImage(
                    openAiKey, userImagePath, garments.map { it.path to it.label }, state.profile, "front", bodyRef,
                    modelPortraitPath = portrait?.imagePath
                )
            if (front.isFailure) {
                _uiState.value = _uiState.value.copy(isLoading = false, loadingStep = "", error = front.exceptionOrNull()?.message ?: "Generation failed")
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                resultViews = listOf(front.getOrThrow()),
                isLoading = false, loadingStep = "", generatingViews = true
            )

            // A second angle can only come from regenerating the person, which is
            // what loses the likeness. One faithful view beats two wrong ones, so
            // the extra angle is offered only when the faithful path was unavailable.
            if (!usedFashn) {
                val views = _uiState.value.resultViews.toMutableList()
                val side = claudeService.generateTryOnImage(
                    openAiKey, userImagePath, garments.map { it.path to it.label }, state.profile, "side", bodyRef,
                    modelPortraitPath = portrait?.imagePath
                )
                if (side.isSuccess) {
                    views.add(side.getOrThrow())
                    _uiState.value = _uiState.value.copy(resultViews = views.toList())
                }
            }
            _uiState.value = _uiState.value.copy(generatingViews = false)
        }
    }

    /** Tracks which path produced the current result, so angles are only offered when they are honest. */
    private var usedFashn = false

    /**
     * Dress [personPath] in each garment in turn, or null when FASHN is not
     * available and the caller should fall back.
     */
    private suspend fun dressWithFashn(
        personPath: String,
        garments: List<Garment>
    ): Result<String>? {
        usedFashn = false
        if (replicateKey.isBlank() || garments.isEmpty()) return null

        var current = personPath
        for ((index, garment) in garments.withIndex()) {
            val (path, label, category) = garment
            _uiState.value = _uiState.value.copy(
                loadingStep = "Dressing your model… ${index + 1}/${garments.size}"
            )
            val step = replicateService.tryOn(replicateKey, current, path, label, category)
            if (step.isFailure) {
                Log.w("TryOnVM", "FASHN step ${index + 1} failed: ${step.exceptionOrNull()?.message}")
                return null
            }
            current = step.getOrThrow()
        }
        usedFashn = true
        return Result.success(current)
    }

    private suspend fun resolveImagePath(path: String): String {
        if (!path.startsWith("unsplash:")) return path
        return UnsplashService.resolveUrl(path.removePrefix("unsplash:")) ?: ""
    }

    // ── Favorites ────────────────────────────────────────────────────────

    fun addEssentialToFavorites(item: ClothingItem) {
        viewModelScope.launch {
            wardrobeRepository.addClothing(item.copy(id = 0, isFavorite = true))
            _uiState.value = _uiState.value.copy(
                favoriteEssentialIds = _uiState.value.favoriteEssentialIds + item.id
            )
        }
    }

    fun addEbayToFavorites(item: EbayItem, categoryName: String) {
        val slot = ebayNameToSlot(categoryName)
        val category = try { ClothingCategory.valueOf(slot) } catch (_: Exception) { ClothingCategory.INNER }
        viewModelScope.launch {
            wardrobeRepository.addClothing(
                ClothingItem(
                    imagePath = item.imageUrl,
                    category = category,
                    name = item.title.take(60),
                    notes = "Secondhand · ${item.price} ${item.currency}",
                    isFavorite = true,
                    cloudImageUrl = item.imageUrl
                )
            )
            _uiState.value = _uiState.value.copy(
                favoriteEbayIds = _uiState.value.favoriteEbayIds + item.itemId
            )
        }
    }

    // ── Save ─────────────────────────────────────────────────────────────

    fun saveImage(path: String) {
        val state = _uiState.value
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val garmentParts = state.selectedGarments.values.map { g ->
            val src = when (g.source) {
                GarmentTab.WARDROBE   -> "Wardrobe"
                GarmentTab.ESSENTIALS -> "Essential"
                GarmentTab.EBAY       -> "Secondhand"
            }
            "$src: ${g.displayLabel}"
        }
        val note = "$date · ${garmentParts.joinToString(", ")}"
        viewModelScope.launch {
            val backedUp = wardrobeRepository.saveImage(SavedImage(path = path, type = "tryon", label = "Try-On", note = note))
            _uiState.value = _uiState.value.copy(imageSaved = true, promptSignInToBackup = !backedUp)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    // ── Affiliate URL helpers ────────────────────────────────────────────────

    private fun applyEbayAffiliate(item: EbayItem): EbayItem {
        if (ebayAffiliateCampaignId.isBlank() || item.itemWebUrl.isBlank()) return item
        val sep = if (item.itemWebUrl.contains('?')) "&" else "?"
        return item.copy(
            itemWebUrl = "${item.itemWebUrl}${sep}mkevt=1&mkcid=1&mkrid=705-53470-19255-0&campid=$ebayAffiliateCampaignId&toolid=10001"
        )
    }

    /** Skimlinks/Sovrn wrap for non-eBay/non-Amazon retailer URLs. No-op for blank URLs. */
    private fun applyAggregatorAffiliate(item: EbayItem): EbayItem {
        if (item.itemWebUrl.isBlank()) return item
        return item.copy(itemWebUrl = com.trymeon.app.util.Affiliate.wrap(item.itemWebUrl, item.source))
    }

    private fun applyAmazonAffiliate(item: EbayItem): EbayItem {
        if (amazonAssociateTag.isBlank() || item.itemWebUrl.isBlank()) return item
        val sep = if (item.itemWebUrl.contains('?')) "&" else "?"
        return item.copy(itemWebUrl = "${item.itemWebUrl}${sep}tag=$amazonAssociateTag")
    }

    private fun ebayNameToSlot(name: String): String = when (name.lowercase()) {
        "top"           -> ClothingCategory.INNER.name
        "jacket"        -> ClothingCategory.OUTERWEAR.name
        "bottoms"       -> ClothingCategory.PANTS.name
        "dress", "set"  -> ClothingCategory.DRESS.name
        "shoes"         -> ClothingCategory.SHOES.name
        "bag"           -> ClothingCategory.BAG.name
        else            -> name.uppercase()
    }

    private fun ensureGenderInQuery(query: String): String {
        val gender = when {
            _uiState.value.effectiveGender == "Male" || _uiState.value.effectiveGender == "Female" ->
                _uiState.value.effectiveGender
            else -> inferGenderLocally(_uiState.value.wardrobe)
        }
        return com.trymeon.app.util.ensureGenderInQuery(query, gender)
    }

    private fun parseEbayCategories(text: String): List<EbayTryOnCategory> =
        TryOnPlanParser.parse(text) { ensureGenderInQuery(it) }

    private fun savePhoto(context: Context, uri: Uri): String? = try {
        val dir = File(context.cacheDir, "tryon").apply { mkdirs() }
        val file = File(dir, "user_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { i -> FileOutputStream(file).use { o -> i.copyTo(o) } }
        file.absolutePath
    } catch (e: Exception) { null }
}
