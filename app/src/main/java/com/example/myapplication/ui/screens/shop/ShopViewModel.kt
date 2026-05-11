package com.example.myapplication.ui.screens.shop

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.BasicWardrobeProvider
import com.example.myapplication.data.remote.AliExpressApiService
import com.example.myapplication.data.remote.AliExpressItem
import com.example.myapplication.data.remote.AmazonApiService
import com.example.myapplication.data.remote.AmazonItem
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.remote.EbayApiService
import com.example.myapplication.data.remote.EbayItem
import com.example.myapplication.data.remote.ReplicateApiService
import com.example.myapplication.data.remote.SheinApiService
import com.example.myapplication.data.remote.SheinItem
import com.example.myapplication.data.remote.TaobaoApiService
import com.example.myapplication.data.remote.TaobaoItem
import com.example.myapplication.data.remote.VintedApiService
import com.example.myapplication.data.remote.VintedItem
import com.example.myapplication.data.repository.UserProfileRepository
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.domain.model.ClothingCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class ShopRecommendation(
    val title: String,
    val reason: String,
    val searchQuery: String,
    val items: List<EbayItem> = emptyList(),
    val loading: Boolean = false,
    val error: String = ""
)

enum class ShopTab { RECOMMENDED, SEARCH }
enum class ShopPlatform { ALL, EBAY, AMAZON, TAOBAO, SHEIN, VINTED, ALIEXPRESS }

data class ShopUiState(
    val activeTab: ShopTab = ShopTab.RECOMMENDED,
    val activePlatform: ShopPlatform = ShopPlatform.ALL,
    // Recommendations
    val recommendations: List<ShopRecommendation> = emptyList(),
    val recoLoading: Boolean = false,
    val recoError: String = "",
    // Search
    val query: String = "",
    val selectedCategory: ClothingCategory? = null,
    val ebayResults: List<EbayItem> = emptyList(),
    val amazonResults: List<AmazonItem> = emptyList(),
    val taobaoResults: List<TaobaoItem> = emptyList(),
    val sheinResults: List<SheinItem> = emptyList(),
    val vintedResults: List<VintedItem> = emptyList(),
    val aliExpressResults: List<AliExpressItem> = emptyList(),
    val ebayLoading: Boolean = false,
    val amazonLoading: Boolean = false,
    val taobaoLoading: Boolean = false,
    val sheinLoading: Boolean = false,
    val vintedLoading: Boolean = false,
    val aliExpressLoading: Boolean = false,
    val ebayError: String = "",
    val amazonError: String = "",
    val taobaoError: String = "",
    val sheinError: String = "",
    val vintedError: String = "",
    val aliExpressError: String = "",
    val hasSearched: Boolean = false,
    // Detail sheet (eBay items)
    val selectedItem: EbayItem? = null,
    val showDetailSheet: Boolean = false,
    val aiSuggestion: String? = null,
    val aiLoading: Boolean = false,
    // Try-on
    val userImagePath: String? = null,
    val tryOnResult: String? = null,
    val tryOnLoading: Boolean = false,
    val tryOnError: String = ""
) {
    val isLoading get() = ebayLoading || amazonLoading || taobaoLoading || sheinLoading || vintedLoading || aliExpressLoading
    val hasResults get() = ebayResults.isNotEmpty() || amazonResults.isNotEmpty() || taobaoResults.isNotEmpty() ||
        sheinResults.isNotEmpty() || vintedResults.isNotEmpty() || aliExpressResults.isNotEmpty()
}

class ShopViewModel(
    private val ebayService: EbayApiService,
    private val ebayClientId: String,
    private val ebayClientSecret: String,
    private val wardrobeRepository: WardrobeRepository? = null,
    private val claudeApiService: ClaudeApiService? = null,
    private val replicateApiService: ReplicateApiService? = null,
    private val apiKey: String = "",
    private val replicateApiKey: String = "",
    private val profileRepository: UserProfileRepository? = null,
    private val amazonService: AmazonApiService? = null,
    private val amazonAccessKey: String = "",
    private val amazonSecretKey: String = "",
    private val amazonAssociateTag: String = "",
    private val taobaoService: TaobaoApiService? = null,
    private val rapidApiKey: String = "",
    private val sheinService: SheinApiService? = null,
    private val vintedService: VintedApiService? = null,
    private val aliExpressService: AliExpressApiService? = null
) : ViewModel() {

    private var profileGender: String = ""
    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()

    val amazonEnabled get() = amazonService != null &&
        amazonAccessKey.isNotBlank() && amazonSecretKey.isNotBlank() && amazonAssociateTag.isNotBlank()

    val taobaoEnabled get() = taobaoService != null && rapidApiKey.isNotBlank()
    val sheinEnabled get() = sheinService != null && rapidApiKey.isNotBlank()
    val vintedEnabled get() = vintedService != null && rapidApiKey.isNotBlank()
    val aliExpressEnabled get() = aliExpressService != null && rapidApiKey.isNotBlank()

    init {
        viewModelScope.launch {
            profileRepository?.getProfile()?.collect { p -> profileGender = p?.gender.orEmpty() }
        }
        loadRecommendations()
    }

    fun setTab(tab: ShopTab) { _uiState.value = _uiState.value.copy(activeTab = tab) }

    fun setPlatform(platform: ShopPlatform) {
        _uiState.value = _uiState.value.copy(activePlatform = platform)
    }

    fun refreshRecommendations() = loadRecommendations()

    private fun loadRecommendations() {
        val service = claudeApiService ?: return
        if (apiKey.isBlank()) return
        _uiState.value = _uiState.value.copy(recoLoading = true, recoError = "", recommendations = emptyList())
        viewModelScope.launch {
            val clothes = wardrobeRepository?.getAllClothing()?.first()?.takeIf { it.isNotEmpty() }
                ?: BasicWardrobeProvider.items
            val raw = try {
                service.getShoppingRecommendations(apiKey, clothes, profileGender)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(recoLoading = false, recoError = "Failed to load: ${e.message}")
                return@launch
            }
            val parsed = parseRecommendations(raw)
            if (parsed.isEmpty()) {
                _uiState.value = _uiState.value.copy(recoLoading = false, recoError = "No recommendations yet — add items to your wardrobe first")
                return@launch
            }
            _uiState.value = _uiState.value.copy(recoLoading = false, recommendations = parsed)
            parsed.forEachIndexed { index, reco ->
                launch { fetchRecoItems(index, reco.searchQuery) }
            }
        }
    }

    private suspend fun fetchRecoItems(index: Int, query: String) {
        updateReco(index) { it.copy(loading = true) }
        val result = ebayService.search(ebayClientId, ebayClientSecret, query, limit = 8)
        updateReco(index) { it.copy(loading = false, items = result.getOrNull() ?: emptyList(), error = result.exceptionOrNull()?.message ?: "") }
    }

    private fun updateReco(index: Int, block: (ShopRecommendation) -> ShopRecommendation) {
        val list = _uiState.value.recommendations.toMutableList()
        if (index < list.size) list[index] = block(list[index])
        _uiState.value = _uiState.value.copy(recommendations = list)
    }

    fun setQuery(q: String) { _uiState.value = _uiState.value.copy(query = q) }
    fun setCategory(cat: ClothingCategory?) { _uiState.value = _uiState.value.copy(selectedCategory = cat) }

    fun search() {
        val state = _uiState.value
        val q = buildQuery(state.query, state.selectedCategory)
        if (q.isBlank()) return

        _uiState.value = state.copy(
            ebayResults = emptyList(), amazonResults = emptyList(), taobaoResults = emptyList(),
            sheinResults = emptyList(), vintedResults = emptyList(), aliExpressResults = emptyList(),
            ebayError = "", amazonError = "", taobaoError = "",
            sheinError = "", vintedError = "", aliExpressError = "",
            hasSearched = true
        )

        val searchEbay       = state.activePlatform == ShopPlatform.ALL || state.activePlatform == ShopPlatform.EBAY
        val searchAmazon     = (state.activePlatform == ShopPlatform.ALL || state.activePlatform == ShopPlatform.AMAZON) && amazonEnabled
        val searchTaobao     = (state.activePlatform == ShopPlatform.ALL || state.activePlatform == ShopPlatform.TAOBAO) && taobaoEnabled
        val searchShein      = (state.activePlatform == ShopPlatform.ALL || state.activePlatform == ShopPlatform.SHEIN) && sheinEnabled
        val searchVinted     = (state.activePlatform == ShopPlatform.ALL || state.activePlatform == ShopPlatform.VINTED) && vintedEnabled
        val searchAliExpress = (state.activePlatform == ShopPlatform.ALL || state.activePlatform == ShopPlatform.ALIEXPRESS) && aliExpressEnabled

        viewModelScope.launch {
            val jobs = listOfNotNull(
                if (searchEbay) launch {
                    _uiState.value = _uiState.value.copy(ebayLoading = true)
                    val r = ebayService.search(ebayClientId, ebayClientSecret, q)
                    _uiState.value = _uiState.value.copy(ebayLoading = false, ebayResults = r.getOrNull() ?: emptyList(), ebayError = r.exceptionOrNull()?.message ?: "")
                } else null,
                if (searchAmazon) launch {
                    _uiState.value = _uiState.value.copy(amazonLoading = true)
                    val r = amazonService!!.search(amazonAccessKey, amazonSecretKey, amazonAssociateTag, q)
                    _uiState.value = _uiState.value.copy(amazonLoading = false, amazonResults = r.getOrNull() ?: emptyList(), amazonError = r.exceptionOrNull()?.message ?: "")
                } else null,
                if (searchTaobao) launch {
                    _uiState.value = _uiState.value.copy(taobaoLoading = true)
                    val r = taobaoService!!.search(rapidApiKey, q)
                    _uiState.value = _uiState.value.copy(taobaoLoading = false, taobaoResults = r.getOrNull() ?: emptyList(), taobaoError = r.exceptionOrNull()?.message ?: "")
                } else null,
                if (searchShein) launch {
                    _uiState.value = _uiState.value.copy(sheinLoading = true)
                    val r = sheinService!!.search(rapidApiKey, q)
                    _uiState.value = _uiState.value.copy(sheinLoading = false, sheinResults = r.getOrNull() ?: emptyList(), sheinError = r.exceptionOrNull()?.message ?: "")
                } else null,
                if (searchVinted) launch {
                    _uiState.value = _uiState.value.copy(vintedLoading = true)
                    val r = vintedService!!.search(rapidApiKey, q)
                    _uiState.value = _uiState.value.copy(vintedLoading = false, vintedResults = r.getOrNull() ?: emptyList(), vintedError = r.exceptionOrNull()?.message ?: "")
                } else null,
                if (searchAliExpress) launch {
                    _uiState.value = _uiState.value.copy(aliExpressLoading = true)
                    val r = aliExpressService!!.search(rapidApiKey, q)
                    _uiState.value = _uiState.value.copy(aliExpressLoading = false, aliExpressResults = r.getOrNull() ?: emptyList(), aliExpressError = r.exceptionOrNull()?.message ?: "")
                } else null
            )
            for (job in jobs) job.join()
        }
    }

    // Detail sheet
    fun selectItem(item: EbayItem) {
        _uiState.value = _uiState.value.copy(
            selectedItem = item, showDetailSheet = true,
            aiSuggestion = null, tryOnResult = null, tryOnError = ""
        )
        fetchAiSuggestion(item)
    }

    fun dismissDetail() { _uiState.value = _uiState.value.copy(showDetailSheet = false) }

    private fun fetchAiSuggestion(item: EbayItem) {
        val service = claudeApiService ?: return
        if (apiKey.isBlank()) return
        _uiState.value = _uiState.value.copy(aiLoading = true)
        viewModelScope.launch {
            val clothes = wardrobeRepository?.getAllClothing()?.first()?.takeIf { it.isNotEmpty() }
                ?: BasicWardrobeProvider.items
            val result = try { service.suggestOutfitWithNewItem(apiKey, item.title, item.imageUrl, clothes) }
            catch (e: Exception) { "Analysis failed: ${e.message}" }
            _uiState.value = _uiState.value.copy(aiSuggestion = result, aiLoading = false)
        }
    }

    // Try-on
    fun setUserImage(context: Context, uri: android.net.Uri) {
        viewModelScope.launch {
            val path = saveImageToInternal(context, uri) ?: return@launch
            _uiState.value = _uiState.value.copy(userImagePath = path, tryOnResult = null, tryOnError = "")
        }
    }

    fun startTryOn() {
        val item = _uiState.value.selectedItem ?: return
        val userPath = _uiState.value.userImagePath ?: return
        val garmentUrl = item.imageUrl.takeIf { it.isNotEmpty() } ?: return
        val service = claudeApiService ?: return
        if (apiKey.isBlank()) return
        _uiState.value = _uiState.value.copy(tryOnLoading = true, tryOnResult = null, tryOnError = "")
        viewModelScope.launch {
            val result = service.generateTryOnImage(apiKey, userPath, listOf(Pair(garmentUrl, item.title)), null)
            _uiState.value = _uiState.value.copy(
                tryOnLoading = false,
                tryOnResult = result.getOrNull(),
                tryOnError = result.exceptionOrNull()?.message ?: ""
            )
        }
    }

    private fun parseRecommendations(text: String) =
        text.lines().filter { it.startsWith("ITEM:") }.mapNotNull { line ->
            val parts = line.removePrefix("ITEM:").split("|")
            if (parts.size < 3) return@mapNotNull null
            ShopRecommendation(title = parts[0].trim(), reason = parts[1].trim(), searchQuery = parts[2].trim())
        }

    private fun buildQuery(text: String, cat: ClothingCategory?) =
        listOf(text, cat?.let { categoryKeyword(it) } ?: "").filter { it.isNotBlank() }.joinToString(" ")

    private fun categoryKeyword(cat: ClothingCategory) = when (cat) {
        ClothingCategory.INNER     -> "top shirt"
        ClothingCategory.OUTERWEAR -> "jacket coat"
        ClothingCategory.PANTS     -> "pants trousers"
        ClothingCategory.DRESS     -> "dress"
        ClothingCategory.SHOES     -> "shoes"
        ClothingCategory.ACCESSORY -> "accessory"
        ClothingCategory.BAG       -> "bag handbag"
    }

    private fun saveImageToInternal(context: Context, uri: Uri): String? = try {
        val dir = File(context.filesDir, "tryon_shop").apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        file.absolutePath
    } catch (e: Exception) { null }
}
