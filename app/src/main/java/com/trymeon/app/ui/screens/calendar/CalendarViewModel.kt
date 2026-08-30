package com.trymeon.app.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trymeon.app.data.BasicWardrobeProvider
import com.trymeon.app.data.repository.OutfitLogRepository
import com.trymeon.app.data.repository.WardrobeRepository
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.model.OutfitLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CalendarUiState(
    val currentMonth: LocalDate = LocalDate.now().withDayOfMonth(1),
    val logs: Map<String, OutfitLog> = emptyMap(),
    val allItems: List<ClothingItem> = emptyList(),
    val selectedDate: String? = null
)

class CalendarViewModel(
    private val logRepository: OutfitLogRepository,
    private val wardrobeRepository: WardrobeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            logRepository.getLogs().collect { logs ->
                _uiState.value = _uiState.value.copy(logs = logs.associateBy { it.date })
            }
        }
        viewModelScope.launch {
            wardrobeRepository.getAllClothing().collect { items ->
                val all = items + BasicWardrobeProvider.items
                _uiState.value = _uiState.value.copy(allItems = all)
            }
        }
    }

    fun prevMonth() {
        _uiState.value = _uiState.value.copy(
            currentMonth = _uiState.value.currentMonth.minusMonths(1)
        )
    }

    fun nextMonth() {
        _uiState.value = _uiState.value.copy(
            currentMonth = _uiState.value.currentMonth.plusMonths(1)
        )
    }

    fun selectDate(date: String?) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
    }

    fun logOutfit(date: String, itemIds: List<Long>, note: String = "") {
        viewModelScope.launch {
            logRepository.save(OutfitLog(date, itemIds, note))
        }
    }

    fun deleteLog(date: String) {
        viewModelScope.launch {
            logRepository.delete(date)
        }
    }

    fun itemsForLog(log: OutfitLog): List<ClothingItem> {
        val state = _uiState.value
        return log.itemIds.mapNotNull { id -> state.allItems.find { it.id == id } }
    }
}
