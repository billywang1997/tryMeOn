package com.example.myapplication.ui.screens.capsule

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

data class CapsuleUiState(
    val wardrobe: List<ClothingItem> = emptyList(),
    val result: String = "",
    val isLoading: Boolean = false,
    val error: String = "",
    val generated: Boolean = false
)

class CapsuleViewModel(
    private val wardrobeRepository: WardrobeRepository,
    private val claudeService: ClaudeApiService,
    private val apiKey: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(CapsuleUiState())
    val uiState: StateFlow<CapsuleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            wardrobeRepository.getAllClothing().collect { items ->
                _uiState.value = _uiState.value.copy(wardrobe = items)
            }
        }
    }

    fun generate() {
        val clothes = _uiState.value.wardrobe.ifEmpty { BasicWardrobeProvider.items }
        _uiState.value = _uiState.value.copy(isLoading = true, error = "", result = "")
        viewModelScope.launch {
            try {
                val result = claudeService.generateCapsuleWardrobe(apiKey, clothes)
                _uiState.value = _uiState.value.copy(result = result, isLoading = false, generated = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Something went wrong", isLoading = false)
            }
        }
    }
}
