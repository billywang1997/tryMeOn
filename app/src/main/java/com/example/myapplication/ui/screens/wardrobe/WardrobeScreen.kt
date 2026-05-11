package com.example.myapplication.ui.screens.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.BasicWardrobeProvider
import com.example.myapplication.ui.components.FashionImage
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.domain.model.ClothingCategory
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.ui.theme.Ash
import com.example.myapplication.ui.theme.Ink
import com.example.myapplication.ui.theme.InkLight
import com.example.myapplication.ui.theme.Mist
import com.example.myapplication.ui.theme.Paper
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Shuffle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeScreen(
    repository: WardrobeRepository,
    contentPadding: PaddingValues,
    claudeApiService: com.example.myapplication.data.remote.ClaudeApiService? = null,
    apiKey: String = "",
    onNavigateToAnalysis: () -> Unit = {},
    onNavigateToSwipe: () -> Unit = {},
    onNavigateToCapsule: () -> Unit = {},
    onNavigateToDna: () -> Unit = {},
    onNavigateToCost: () -> Unit = {},
    onNavigateToStyleExplorer: () -> Unit = {}
) {
    val vm: WardrobeViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            WardrobeViewModel(repository) as T
    })

    val allClothing by vm.allClothing.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()

    val filtered = selectedCategory?.let { cat -> allClothing.filter { it.category == cat } } ?: allClothing
    val basicFiltered = selectedCategory?.let { BasicWardrobeProvider.byCategory(it) } ?: BasicWardrobeProvider.items

    var showBatchAdd by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "WARDROBE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Light
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${allClothing.size} ITEMS", style = MaterialTheme.typography.labelMedium, color = Ash)
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onNavigateToSwipe, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Shuffle, null, tint = Ash, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onNavigateToAnalysis, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Analytics, null, tint = Ash, modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider(color = Mist)

            // Feature strip
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { FeatureChip("🃏 Style Builder", onNavigateToSwipe) }
                item { FeatureChip("📊 Analysis", onNavigateToAnalysis) }
                item { FeatureChip("💊 Capsule", onNavigateToCapsule) }
                item { FeatureChip("🧬 Style DNA", onNavigateToDna) }
                item { FeatureChip("💰 Cost Tracker", onNavigateToCost) }
                item { FeatureChip("🎨 Style Explorer", onNavigateToStyleExplorer) }
            }

            HorizontalDivider(color = Mist)

            // Category filter
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val cats: List<ClothingCategory?> = listOf(null) + ClothingCategory.entries
                items(cats) { cat ->
                    val isSelected = cat == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Ink else Paper)
                            .border(1.dp, if (isSelected) Ink else Mist, RoundedCornerShape(20.dp))
                            .clickable { vm.selectCategory(cat) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            (cat?.label ?: "All").uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) Color.White else Ash
                        )
                    }
                }
            }

            HorizontalDivider(color = Mist)

            // Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (filtered.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                        GridSectionLabel("MY ITEMS  ·  ${filtered.size}")
                    }
                    items(filtered, key = { it.id }) { item ->
                        ClothingCard(
                            item = item,
                            onDelete = { vm.deleteClothing(item) },
                            onUpdateCategory = { cat -> vm.updateCategory(item, cat) },
                            onToggleFavorite = { vm.toggleFavorite(item) }
                        )
                    }
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    BasicsHeader()
                }
                items(basicFiltered, key = { "basic_${it.id}" }) { item ->
                    ClothingCard(item = item, onDelete = null, isBasic = true)
                }
            }
        }

        FloatingActionButton(
            onClick = { showBatchAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            containerColor = Ink,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add item")
        }
    }

    if (showBatchAdd) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showBatchAdd = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val batchVm: com.example.myapplication.ui.screens.addclothing.BatchAddViewModel =
                viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        com.example.myapplication.ui.screens.addclothing.BatchAddViewModel(
                            repository, claudeApiService, apiKey
                        ) as T
                })
            com.example.myapplication.ui.screens.addclothing.BatchAddFlow(
                viewModel = batchVm,
                onDone = { showBatchAdd = false },
                onSkip = { showBatchAdd = false }
            )
        }
    }
}

@Composable
fun BasicsHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "WARDROBE ESSENTIALS",
                style = MaterialTheme.typography.labelMedium,
                color = Ash
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("Recommended", style = MaterialTheme.typography.labelSmall, color = Ash)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "Timeless pieces that complement any style",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFAAAAAA)
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFF0F0F0))
        )
    }
}

@Composable
fun GridSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Ash,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun ClothingCard(
    item: ClothingItem,
    onDelete: (() -> Unit)?,
    isBasic: Boolean = false,
    onUpdateCategory: ((ClothingCategory) -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(8.dp))
            .background(Paper)
    ) {
        FashionImage(
            model = item.imagePath,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (isBasic) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("Basic", style = MaterialTheme.typography.labelSmall, color = Ash)
            }
        }
        if (onToggleFavorite != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .clickable { onToggleFavorite() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (item.isFavorite) "Unfavorite" else "Favorite",
                    modifier = Modifier.size(13.dp),
                    tint = if (item.isFavorite) Color(0xFFE91E63) else Ash
                )
            }
        }
        if (onDelete != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .clickable { showDeleteConfirm = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(13.dp), tint = Ash)
            }
        }
        // Bottom label — tappable to edit category if onUpdateCategory provided
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.88f))
                .then(if (onUpdateCategory != null) Modifier.clickable { showCategoryPicker = true } else Modifier)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.name.ifEmpty { item.category.label },
                style = MaterialTheme.typography.labelSmall,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onUpdateCategory != null) {
                Icon(Icons.Default.Edit, null, tint = Ash, modifier = Modifier.size(10.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete") },
            text = { Text("Remove this item from your wardrobe?") },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(); showDeleteConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showCategoryPicker && onUpdateCategory != null) {
        var editCategory by remember { mutableStateOf(item.category) }
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("Edit item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ClothingCategory.entries.forEach { cat ->
                        val isCurrent = cat == editCategory
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) Ink else Color.Transparent)
                                .clickable { editCategory = cat }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                cat.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) Color.White else Ink
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateCategory(editCategory)
                        showCategoryPicker = false
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryPicker = false }) { Text("Cancel", color = Ash) }
            }
        )
    }
}

@Composable
fun AddClothingDialog(onDismiss: () -> Unit, onConfirm: (ClothingCategory, String, String, Double) -> Unit) {
    var selectedCategory by remember { mutableStateOf(ClothingCategory.INNER) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("ADD ITEM", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Category", style = MaterialTheme.typography.labelMedium, color = Ash)
                CategoryChips(selected = selectedCategory, onSelect = { selectedCategory = it })
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text("Color (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Purchase price (optional)") },
                    prefix = { Text("$") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedCategory, name, color, price.toDoubleOrNull() ?: 0.0) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Ash) }
        }
    )
}

@Composable
fun FeatureChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Paper)
            .border(1.dp, Mist, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkLight)
    }
}

@Composable
fun CategoryChips(selected: ClothingCategory, onSelect: (ClothingCategory) -> Unit) {
    ClothingCategory.entries.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { cat ->
                val isSelected = cat == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Ink else Paper)
                        .border(1.dp, if (isSelected) Ink else Mist, RoundedCornerShape(6.dp))
                        .clickable { onSelect(cat) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        cat.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) Color.White else InkLight
                    )
                }
            }
        }
    }
}
