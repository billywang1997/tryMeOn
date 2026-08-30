package com.trymeon.app.ui.screens.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trymeon.app.data.BasicWardrobeProvider
import com.trymeon.app.data.remote.ClaudeApiService
import com.trymeon.app.data.repository.WardrobeRepository
import com.trymeon.app.domain.model.ClothingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EmergencyUiState(
    val situation: String = "",
    val result: String = "",
    val isLoading: Boolean = false,
    val error: String = "",
    val wardrobe: List<ClothingItem> = emptyList()
)

class EmergencyViewModel(
    private val wardrobeRepository: WardrobeRepository,
    private val claudeService: ClaudeApiService,
    private val apiKey: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmergencyUiState())
    val uiState: StateFlow<EmergencyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            wardrobeRepository.getAllClothing().collect { items ->
                _uiState.value = _uiState.value.copy(wardrobe = items)
            }
        }
    }

    fun setSituation(s: String) { _uiState.value = _uiState.value.copy(situation = s) }

    fun go() {
        val state = _uiState.value
        if (state.situation.isBlank()) return
        val clothes = state.wardrobe.ifEmpty { BasicWardrobeProvider.items }
        _uiState.value = state.copy(isLoading = true, error = "", result = "")
        viewModelScope.launch {
            try {
                val result = claudeService.getEmergencyOutfit(apiKey, state.situation, clothes)
                _uiState.value = _uiState.value.copy(result = result, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Something went wrong", isLoading = false)
            }
        }
    }
}
