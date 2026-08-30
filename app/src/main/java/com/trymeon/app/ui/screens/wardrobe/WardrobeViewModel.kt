package com.trymeon.app.ui.screens.wardrobe

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trymeon.app.data.repository.WardrobeRepository
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.ClothingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class WardrobeViewModel(private val repository: WardrobeRepository) : ViewModel() {

    private val _allClothing = MutableStateFlow<List<ClothingItem>>(emptyList())
    val allClothing: StateFlow<List<ClothingItem>> = _allClothing.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ClothingCategory?>(null)
    val selectedCategory: StateFlow<ClothingCategory?> = _selectedCategory.asStateFlow()

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllClothing().collect { _allClothing.value = it }
        }
    }

    val filteredClothing: List<ClothingItem>
        get() = _selectedCategory.value?.let { cat ->
            _allClothing.value.filter { it.category == cat }
        } ?: _allClothing.value

    fun selectCategory(category: ClothingCategory?) {
        _selectedCategory.value = category
    }

    fun showAddDialog() { _showAddDialog.value = true }
    fun hideAddDialog() { _showAddDialog.value = false }

    fun addClothing(context: Context, uri: Uri, category: ClothingCategory, name: String, color: String, price: Double = 0.0) {
        viewModelScope.launch {
            val savedPath = saveImageToInternal(context, uri) ?: return@launch
            repository.addClothing(
                ClothingItem(
                    imagePath = savedPath,
                    category = category,
                    name = name,
                    color = color,
                    price = price
                )
            )
        }
    }

    fun deleteClothing(item: ClothingItem) {
        viewModelScope.launch {
            File(item.imagePath).delete()
            repository.deleteClothing(item)
        }
    }

    fun updateCategory(item: ClothingItem, category: ClothingCategory) {
        viewModelScope.launch { repository.updateClothing(item.copy(category = category)) }
    }

    fun updateName(item: ClothingItem, name: String) {
        viewModelScope.launch { repository.updateClothing(item.copy(name = name)) }
    }

    fun toggleFavorite(item: ClothingItem) {
        viewModelScope.launch { repository.toggleFavorite(item.id) }
    }

    private fun saveImageToInternal(context: Context, uri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "wardrobe").apply { mkdirs() }
            val file = File(dir, "${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
