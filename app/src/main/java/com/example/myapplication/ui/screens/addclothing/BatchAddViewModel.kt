package com.example.myapplication.ui.screens.addclothing

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.ImageProcessor
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.domain.model.ClothingCategory
import com.example.myapplication.domain.model.ClothingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

data class PendingItem(
    val id: Int,
    val uri: Uri,
    val foreground: Bitmap? = null,
    val processingBg: Boolean = true,
    val selectedCategory: ClothingCategory = ClothingCategory.INNER,
    val aiCategory: ClothingCategory? = null,
    val detectingAi: Boolean = false,
    val selectedBgIndex: Int = 0,
    val name: String = ""
)

val onboardingBgOptions = listOf(
    Triple("White",  android.graphics.Color.WHITE,       0xFFFFFFFF.toInt()),
    Triple("Cream",  0xFFF8F5F0.toInt(),                 0xFFF8F5F0.toInt()),
    Triple("Stone",  0xFFEEE8E0.toInt(),                 0xFFEEE8E0.toInt()),
    Triple("Blush",  0xFFF3E8E8.toInt(),                 0xFFF3E8E8.toInt()),
    Triple("Sage",   0xFFE5EDE5.toInt(),                 0xFFE5EDE5.toInt()),
    Triple("Sky",    0xFFE5EBF3.toInt(),                 0xFFE5EBF3.toInt()),
    Triple("Noir",   0xFF1A1A1A.toInt(),                 0xFF1A1A1A.toInt()),
)

class BatchAddViewModel(
    private val wardrobeRepository: WardrobeRepository,
    private val claudeService: ClaudeApiService? = null,
    private val apiKey: String = ""
) : ViewModel() {

    private val _items = MutableStateFlow<List<PendingItem>>(emptyList())
    val items: StateFlow<List<PendingItem>> = _items.asStateFlow()

    private val _savingAll = MutableStateFlow(false)
    val savingAll: StateFlow<Boolean> = _savingAll.asStateFlow()

    private var nextId = 0

    fun addUris(context: Context, uris: List<Uri>) {
        val newItems = uris.map { uri -> PendingItem(id = nextId++, uri = uri) }
        _items.value = _items.value + newItems
        newItems.forEach { item -> processItem(context, item) }
    }

    private fun processItem(context: Context, item: PendingItem) {
        viewModelScope.launch {
            val raw = ImageProcessor.removeBackground(context, item.uri)
            val fg = raw?.let { ImageProcessor.cropToMainGarment(it) }
            updateItem(item.id) { it.copy(foreground = fg, processingBg = false, detectingAi = fg != null && claudeService != null && apiKey.isNotBlank()) }

            if (fg != null && claudeService != null && apiKey.isNotBlank()) {
                val cat = tryDetectCategory(fg)
                updateItem(item.id) { it.copy(aiCategory = cat, selectedCategory = cat, detectingAi = false) }
            }
        }
    }

    fun removeItem(id: Int) { _items.value = _items.value.filter { it.id != id } }

    fun setCategory(id: Int, cat: ClothingCategory) { updateItem(id) { it.copy(selectedCategory = cat) } }

    fun setName(id: Int, name: String) { updateItem(id) { it.copy(name = name) } }

    fun setBg(id: Int, bgIndex: Int) { updateItem(id) { it.copy(selectedBgIndex = bgIndex) } }

    fun clearAll() { _items.value = emptyList() }

    suspend fun saveAll(context: Context): Int {
        _savingAll.value = true
        var saved = 0
        _items.value.filter { !it.processingBg && it.foreground != null }.forEach { item ->
            val fg = item.foreground ?: return@forEach
            val (_, androidColor, _) = onboardingBgOptions[item.selectedBgIndex.coerceIn(0, onboardingBgOptions.lastIndex)]
            val composed = ImageProcessor.compositeOnColor(fg, androidColor)
            val path = ImageProcessor.saveBitmapToFile(context, composed)
            if (path != null) {
                wardrobeRepository.addClothing(ClothingItem(imagePath = path, category = item.selectedCategory, name = item.name, color = "", price = 0.0))
                saved++
            }
        }
        _savingAll.value = false
        return saved
    }

    private fun updateItem(id: Int, transform: (PendingItem) -> PendingItem) {
        _items.value = _items.value.map { if (it.id == id) transform(it) else it }
    }

    private suspend fun tryDetectCategory(bitmap: Bitmap): ClothingCategory = try {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        claudeService!!.detectClothingCategory(apiKey, b64)
    } catch (e: Exception) { ClothingCategory.INNER }
}
