package com.example.myapplication.ui.screens.swipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.BasicWardrobeProvider
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.domain.model.ClothingCategory
import com.example.myapplication.domain.model.ClothingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val CATEGORY_ORDER = listOf(
    ClothingCategory.INNER,
    ClothingCategory.PANTS,
    ClothingCategory.DRESS,
    ClothingCategory.OUTERWEAR,
    ClothingCategory.SHOES,
    ClothingCategory.ACCESSORY,
    ClothingCategory.BAG
)

data class SwipeUiState(
    val categoryIndex: Int = 0,
    val deck: List<ClothingItem> = emptyList(),
    val selected: List<ClothingItem> = emptyList(),
    val done: Boolean = false,
    val allItems: List<ClothingItem> = emptyList()
) {
    val currentCategory: ClothingCategory get() = CATEGORY_ORDER[categoryIndex]
}

class SwipeViewModel(private val wardrobeRepository: WardrobeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SwipeUiState())
    val uiState: StateFlow<SwipeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            wardrobeRepository.getAllClothing().collect { userItems ->
                val all = userItems + BasicWardrobeProvider.items
                _uiState.value = _uiState.value.copy(allItems = all)
                loadCategory(0, all)
            }
        }
    }

    private fun loadCategory(index: Int, all: List<ClothingItem> = _uiState.value.allItems) {
        if (index >= CATEGORY_ORDER.size) {
            _uiState.value = _uiState.value.copy(done = true)
            return
        }
        val cat = CATEGORY_ORDER[index]
        val deck = all.filter { it.category == cat }.shuffled()
        _uiState.value = _uiState.value.copy(categoryIndex = index, deck = deck)
    }

    fun swipeRight() {
        val state = _uiState.value
        val picked = state.deck.firstOrNull() ?: return
        val newDeck = state.deck.drop(1)
        val newSelected = state.selected + picked
        if (newDeck.isEmpty()) {
            _uiState.value = state.copy(deck = newDeck, selected = newSelected)
            loadCategory(state.categoryIndex + 1)
        } else {
            _uiState.value = state.copy(deck = newDeck, selected = newSelected)
        }
    }

    fun swipeLeft() {
        val state = _uiState.value
        val newDeck = state.deck.drop(1)
        if (newDeck.isEmpty()) {
            _uiState.value = state.copy(deck = newDeck)
            loadCategory(state.categoryIndex + 1)
        } else {
            _uiState.value = state.copy(deck = newDeck)
        }
    }

    fun skipCategory() {
        val state = _uiState.value
        loadCategory(state.categoryIndex + 1)
    }

    fun reset() {
        _uiState.value = SwipeUiState(allItems = _uiState.value.allItems)
        loadCategory(0)
    }
}
