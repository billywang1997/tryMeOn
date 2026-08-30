package com.trymeon.app.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trymeon.app.data.repository.OutfitLogRepository
import com.trymeon.app.data.repository.WardrobeRepository
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.model.OutfitLog
import com.trymeon.app.ui.components.FashionImage
import com.trymeon.app.ui.theme.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    logRepository: OutfitLogRepository,
    wardrobeRepository: WardrobeRepository,
    onBack: () -> Unit
) {
    val vm: CalendarViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            CalendarViewModel(logRepository, wardrobeRepository) as T
    })
    val state by vm.uiState.collectAsState()

    var showLogSheet by remember { mutableStateOf(false) }
    var logSheetDate by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OUTFIT CALENDAR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = White)
            )
        },
        containerColor = White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Month navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.prevMonth() }) {
                    Icon(Icons.Default.ChevronLeft, "Previous", tint = Ink)
                }
                Text(
                    "${state.currentMonth.year} / ${state.currentMonth.monthValue}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Light,
                    color = Ink
                )
                IconButton(onClick = { vm.nextMonth() }) {
                    Icon(Icons.Default.ChevronRight, "Next", tint = Ink)
                }
            }

            // Weekday headers
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                    Text(
                        day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = Ash
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = Mist)
            Spacer(Modifier.height(4.dp))

            // Calendar grid
            val firstDay = state.currentMonth
            val daysInMonth = firstDay.lengthOfMonth()
            val startDow = firstDay.dayOfWeek.value % 7  // 0=Sun
            val cells = List(startDow) { null } + (1..daysInMonth).map { it }
            val today = LocalDate.now().toString()

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(cells) { day ->
                    if (day == null) {
                        Box(Modifier.aspectRatio(0.8f))
                    } else {
                        val dateStr = "${firstDay.year}-${firstDay.monthValue.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
                        val log = state.logs[dateStr]
                        val isToday = dateStr == today
                        DayCell(
                            day = day,
                            isToday = isToday,
                            log = log,
                            itemsForLog = log?.let { vm.itemsForLog(it) } ?: emptyList(),
                            onClick = {
                                logSheetDate = dateStr
                                showLogSheet = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showLogSheet) {
        val log = state.logs[logSheetDate]
        val logItems = log?.let { vm.itemsForLog(it) } ?: emptyList()

        ModalBottomSheet(
            onDismissRequest = { showLogSheet = false },
            containerColor = White
        ) {
            DayLogSheet(
                date = logSheetDate,
                log = log,
                logItems = logItems,
                allItems = state.allItems,
                onSave = { itemIds, note ->
                    vm.logOutfit(logSheetDate, itemIds, note)
                    showLogSheet = false
                },
                onDelete = {
                    vm.deleteLog(logSheetDate)
                    showLogSheet = false
                },
                onDismiss = { showLogSheet = false }
            )
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    log: OutfitLog?,
    itemsForLog: List<ClothingItem>,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .aspectRatio(0.8f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isToday) Paper else Color.Transparent)
            .border(
                width = if (isToday) 1.dp else 0.dp,
                color = if (isToday) Warm else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(4.dp))
        Text(
            "$day",
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) Warm else Ink,
            fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal
        )
        if (log != null && itemsForLog.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                FashionImage(
                    model = itemsForLog.first().imagePath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        } else if (log != null) {
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Warm)
            )
        }
    }
}

@Composable
private fun DayLogSheet(
    date: String,
    log: OutfitLog?,
    logItems: List<ClothingItem>,
    allItems: List<ClothingItem>,
    onSave: (List<Long>, String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIds by remember(date) { mutableStateOf<Set<Long>>(log?.itemIds?.toHashSet() ?: emptySet()) }
    var note by remember(date) { mutableStateOf(log?.note ?: "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(date, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light)
            if (log != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = Ash)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Select what you wore that day", style = MaterialTheme.typography.labelMedium, color = Ash)
        Spacer(Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.heightIn(min = 80.dp, max = 280.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(allItems) { item ->
                val selected = item.id in selectedIds
                Box(
                    modifier = Modifier
                        .aspectRatio(0.75f)
                        .clip(RoundedCornerShape(6.dp))
                        .border(
                            2.dp,
                            if (selected) Ink else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable {
                            selectedIds = if (selected)
                                selectedIds - item.id
                            else
                                selectedIds + item.id
                        }
                ) {
                    FashionImage(
                        model = item.imagePath,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSave(selectedIds.toList(), note) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
            enabled = selectedIds.isNotEmpty()
        ) {
            Text("Save Outfit Log", style = MaterialTheme.typography.labelLarge)
        }
    }
}
