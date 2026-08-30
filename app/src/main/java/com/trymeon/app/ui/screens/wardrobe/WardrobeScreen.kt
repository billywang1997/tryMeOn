package com.trymeon.app.ui.screens.wardrobe

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
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trymeon.app.data.BasicWardrobeProvider
import com.trymeon.app.data.remote.EbayApiService
import com.trymeon.app.data.remote.SerpApiService
import com.trymeon.app.data.repository.MarketRepository
import com.trymeon.app.data.repository.OutfitLogRepository
import com.trymeon.app.data.repository.UserProfileRepository
import com.trymeon.app.data.repository.WardrobeRepository
import com.trymeon.app.data.repository.WishlistRepository
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.model.OutfitLog
import com.trymeon.app.domain.model.UserProfile
import com.trymeon.app.ui.components.CompleteTheLookSheet
import com.trymeon.app.ui.components.DailySurpriseCard
import com.trymeon.app.ui.components.DailySurpriseEngine
import com.trymeon.app.ui.components.FashionImage
import com.trymeon.app.ui.components.ShakeOutfitSheet
import com.trymeon.app.ui.components.rememberShakeDetector
import com.trymeon.app.ui.components.rollRandomOutfit
import com.trymeon.app.ui.theme.Ash
import com.trymeon.app.ui.theme.Ink
import com.trymeon.app.ui.theme.InkLight
import com.trymeon.app.ui.theme.Mist
import com.trymeon.app.ui.theme.Paper
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Shuffle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeScreen(
    repository: WardrobeRepository,
    contentPadding: PaddingValues,
    claudeApiService: com.trymeon.app.data.remote.ClaudeApiService? = null,
    apiKey: String = "",
    logRepository: OutfitLogRepository? = null,
    profileRepository: UserProfileRepository? = null,
    wishlistRepository: WishlistRepository? = null,
    marketRepository: MarketRepository? = null,
    ebayClientId: String = "",
    ebayClientSecret: String = "",
    serpApiKey: String = "",
    ebayAffiliateCampaignId: String = "",
    onNavigateToCost: () -> Unit = {},
    onNavigateToAudit: () -> Unit = {},
    onNavigateToWishlist: () -> Unit = {},
    onNavigateToStreak: () -> Unit = {}
) {
    val vm: WardrobeViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            WardrobeViewModel(repository) as T
    })

    val allClothing by vm.allClothing.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()
    val logs by (logRepository?.getLogs() ?: kotlinx.coroutines.flow.flowOf(emptyList<OutfitLog>())).collectAsState(emptyList())
    val profile by (profileRepository?.getProfile() ?: kotlinx.coroutines.flow.flowOf<UserProfile?>(null)).collectAsState(null)

    val filtered = selectedCategory?.let { cat -> allClothing.filter { it.category == cat } } ?: allClothing
    val basicFiltered = selectedCategory?.let { BasicWardrobeProvider.byCategory(it) } ?: BasicWardrobeProvider.items

    var showBatchAdd by remember { mutableStateOf(false) }
    var completeLookItem by remember { mutableStateOf<ClothingItem?>(null) }
    var sellItem by remember { mutableStateOf<ClothingItem?>(null) }
    var shakeOutfit by remember { mutableStateOf<List<ClothingItem>?>(null) }
    val wardrobeContext = LocalContext.current
    val scope = rememberCoroutineScope()

    // Lightweight weather fetch so the Daily Surprise can offer a weather-based pick.
    var weather by remember { mutableStateOf<com.trymeon.app.data.remote.WeatherInfo?>(null) }
    LaunchedEffect(Unit) {
        val city = com.trymeon.app.data.remote.WeatherService.detectCity()
        if (city.isNotBlank()) weather = com.trymeon.app.data.remote.WeatherService.fetch(city)
    }

    val surpriseCard = remember(allClothing, logs, weather) {
        DailySurpriseEngine.pick(
            items = allClothing,
            logs = logs,
            weatherTempC = weather?.tempC,
            weatherCondition = weather?.descriptionZh
        )
    }

    val ebayService = remember { EbayApiService() }
    val serpService = remember { SerpApiService() }

    rememberShakeDetector {
        if (allClothing.size >= 2 && shakeOutfit == null && completeLookItem == null) {
            shakeOutfit = rollRandomOutfit(allClothing)
        }
    }

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
                    if (allClothing.size >= 2) {
                        IconButton(
                            onClick = { shakeOutfit = rollRandomOutfit(allClothing) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("🎲", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            HorizontalDivider(color = Mist)

            // Daily Surprise hero
            DailySurpriseCard(
                card = surpriseCard,
                onClick = {
                    when (val c = surpriseCard) {
                        is com.trymeon.app.ui.components.SurpriseCard.ForgottenGem -> completeLookItem = c.item
                        is com.trymeon.app.ui.components.SurpriseCard.MostWornThisMonth -> completeLookItem = c.item
                        is com.trymeon.app.ui.components.SurpriseCard.WeatherPick -> completeLookItem = c.item
                        is com.trymeon.app.ui.components.SurpriseCard.OnThisDay -> onNavigateToStreak()
                        is com.trymeon.app.ui.components.SurpriseCard.Streak -> onNavigateToStreak()
                        com.trymeon.app.ui.components.SurpriseCard.Empty -> showBatchAdd = true
                    }
                }
            )

            // Feature strip
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { FeatureChip("✨ AI Audit", onNavigateToAudit) }
                item { FeatureChip("🤍 Wishlist", onNavigateToWishlist) }
                item { FeatureChip("🔥 Streaks", onNavigateToStreak) }
                item { FeatureChip("💰 Cost Tracker", onNavigateToCost) }
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
                            onToggleFavorite = { vm.toggleFavorite(item) },
                            onClick = { completeLookItem = item },
                            onSell = if (marketRepository != null) { { sellItem = item } } else null
                        )
                    }
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    BasicsHeader()
                }
                items(basicFiltered, key = { "basic_${it.id}" }) { item ->
                    ClothingCard(item = item, onDelete = null, isBasic = true, onClick = { completeLookItem = item })
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
            val batchVm: com.trymeon.app.ui.screens.addclothing.BatchAddViewModel =
                viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        com.trymeon.app.ui.screens.addclothing.BatchAddViewModel(
                            repository, claudeApiService, apiKey
                        ) as T
                })
            com.trymeon.app.ui.screens.addclothing.BatchAddFlow(
                viewModel = batchVm,
                onDone = { showBatchAdd = false },
                onSkip = { showBatchAdd = false }
            )
        }
    }

    completeLookItem?.let { anchor ->
        if (claudeApiService != null && apiKey.isNotBlank()) {
            CompleteTheLookSheet(
                anchorItem = anchor,
                wardrobe = allClothing,
                gender = profile?.gender.orEmpty(),
                apiKey = apiKey,
                claudeService = claudeApiService,
                ebayService = ebayService,
                ebayClientId = ebayClientId,
                ebayClientSecret = ebayClientSecret,
                serpService = serpService,
                serpApiKey = serpApiKey,
                ebayAffiliateCampaignId = ebayAffiliateCampaignId,
                wishlistRepository = wishlistRepository,
                outfitLogs = logs,
                catalog = com.trymeon.app.data.sourcing.ShoppingCatalogFactory.create(
                    wardrobeContext, claudeApiService, apiKey,
                    com.trymeon.app.AppSettings(wardrobeContext).rapidApiKey
                ),
                onDismiss = { completeLookItem = null }
            )
        } else {
            completeLookItem = null
        }
    }

    shakeOutfit?.let { roll ->
        ShakeOutfitSheet(
            initial = roll,
            wardrobe = allClothing,
            onLogOutfit = { log -> logRepository?.save(log) },
            onDismiss = { shakeOutfit = null }
        )
    }

    sellItem?.let { item ->
        if (marketRepository != null) {
            com.trymeon.app.ui.components.SellItemDialog(
                item = item,
                repository = marketRepository,
                onDismiss = { sellItem = null },
                onPosted = { sellItem = null }
            )
        } else {
            sellItem = null
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
    onToggleFavorite: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onSell: (() -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(8.dp))
            .background(Paper)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onSell != null) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                            .clickable { onSell() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Sell, "Sell", modifier = Modifier.size(13.dp), tint = Ash)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .clickable { showDeleteConfirm = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(13.dp), tint = Ash)
                }
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
