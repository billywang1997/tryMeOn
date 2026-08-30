package com.trymeon.app.ui.screens.outfit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingBag
import com.trymeon.app.data.remote.ScraperApiService
import com.trymeon.app.data.remote.ClaudeApiService
import com.trymeon.app.data.remote.EbayItem
import com.trymeon.app.ui.components.landedLabel
import com.trymeon.app.data.remote.SerpApiService
import com.trymeon.app.data.remote.WeatherInfo
import com.trymeon.app.data.repository.UserProfileRepository
import com.trymeon.app.data.repository.WardrobeRepository
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.model.Mood
import com.trymeon.app.domain.model.OutfitScene
import com.trymeon.app.ui.components.FashionImage
import com.trymeon.app.ui.screens.tryon.GarmentThumb
import com.trymeon.app.ui.theme.*
import androidx.compose.ui.text.style.TextAlign
import com.trymeon.app.util.OpenLink

@Composable
fun OutfitScreen(
    wardrobeRepository: WardrobeRepository,
    profileRepository: UserProfileRepository,
    claudeApiService: ClaudeApiService,
    apiKey: String,
    contentPadding: PaddingValues,
    ebayClientId: String = "",
    ebayClientSecret: String = "",
    rapidApiKey: String = "",
    scraperApiKey: String = "",
    serpApiKey: String = "",
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToEmergency: () -> Unit = {},
    onNavigateToRating: () -> Unit = {},
    onNavigateToTryOn: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val outfitContext = LocalContext.current
    var refineOpen by remember { mutableStateOf(false) }
    val amazonService = remember { ScraperApiService() }
    val serpService   = remember { SerpApiService() }
    val vm: OutfitViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            OutfitViewModel(
                wardrobeRepository, profileRepository, claudeApiService, apiKey,
                rapidApiKey,
                serpService, scraperApiKey, serpApiKey,
                catalog = com.trymeon.app.data.sourcing.ShoppingCatalogFactory.create(
                    outfitContext, claudeApiService, apiKey,
                    com.trymeon.app.AppSettings(outfitContext).rapidApiKey
                ),
                priceHint = { com.trymeon.app.AppSettings(outfitContext).priceExpectation.stylistHint }
            ) as T
    })

    val state by vm.uiState.collectAsState()
    var editingCity by remember { mutableStateOf(false) }
    var cityInput by remember { mutableStateOf(state.city) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .padding(bottom = contentPadding.calculateBottomPadding())
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("OOTD", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    state.wardrobe.size.let { if (it > 0) "$it ITEM" + (if (it == 1) "" else "S") else "BASICS" },
                    style = MaterialTheme.typography.labelMedium, color = Ash
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onNavigateToCalendar, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.CalendarMonth, "Outfit calendar", tint = Ash, modifier = Modifier.size(18.dp))
                }
            }
        }
        HorizontalDivider(color = Mist)

        // Weather card
        WeatherCard(
            weather = state.weather,
            isLoading = state.weatherLoading,
            city = state.city,
            editingCity = editingCity,
            cityInput = cityInput,
            onCityInputChange = { cityInput = it },
            onEditCity = { editingCity = true },
            onConfirmCity = {
                vm.setCity(cityInput)
                vm.fetchWeather()
                editingCity = false
            },
            onRefresh = { vm.fetchWeather() }
        )

        Spacer(Modifier.height(16.dp))

        // Occasion picker
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("Select Occasion", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(12.dp))
            OutfitScene.entries.chunked(3).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    row.forEach { scene ->
                        val selected = scene == state.selectedScene
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Ink else Paper)
                                .border(1.dp, if (selected) Ink else Mist, RoundedCornerShape(10.dp))
                                .clickable { vm.selectScene(scene) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                scene.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else Ink
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Mood and "closet only" change the answer but are not needed to get
        // one, and asking for both up front put roughly twenty choices between
        // opening the tab and seeing an outfit. Folded away, and open in a tap.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable { refineOpen = !refineOpen }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (refineOpen) "Fewer options" else "Mood, closet only…",
                style = MaterialTheme.typography.labelMedium, color = Ash
            )
            Text(if (refineOpen) "−" else "+", style = MaterialTheme.typography.bodyMedium, color = Ash)
        }

        AnimatedVisibility(visible = refineOpen) {
            Column {
            Spacer(Modifier.height(16.dp))

            // Mood picker
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Today's Mood", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = onNavigateToEmergency,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("⚡ Emergency", style = MaterialTheme.typography.labelSmall, color = Warm)
                        }
                        TextButton(
                            onClick = onNavigateToRating,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("⭐ Rate", style = MaterialTheme.typography.labelSmall, color = Ash)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Mood.entries) { mood ->
                        val selected = mood == state.mood
                        FilterChip(
                            selected = selected,
                            onClick = { vm.selectMood(if (selected) null else mood) },
                            label = { Text("${mood.emoji} ${mood.label}", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Ink,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Closet Only toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (state.closetOnly) Color(0xFFF0F0F0) else Paper)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Checkroom, null, tint = if (state.closetOnly) Ink else Ash, modifier = Modifier.size(16.dp))
                    Column {
                        Text("Closet Only", style = MaterialTheme.typography.labelLarge, color = Ink)
                        Text(
                            if (state.closetOnly) "Using your wardrobe only" else "Mix wardrobe + basics",
                            style = MaterialTheme.typography.labelSmall, color = Ash
                        )
                    }
                }
                Switch(
                    checked = state.closetOnly,
                    onCheckedChange = { vm.toggleClosetOnly() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Ink)
                )
            }

            }
        }

        Spacer(Modifier.height(16.dp))

        // Generate button
        Box(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
            Button(
                onClick = { vm.generateOutfit() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink,
                    disabledContainerColor = Mist
                )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp, color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("AI is thinking…", style = MaterialTheme.typography.labelLarge, color = Color.White)
                } else {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Generate \"${state.selectedScene.label}\" Outfit", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }

        if (state.error.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF0F0))
                    .padding(14.dp)
            ) {
                Text(state.error, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB00020))
            }
        }

        if (state.suggestion.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Warm, modifier = Modifier.size(14.dp))
                    Text("${state.selectedScene.label}  LOOK", style = MaterialTheme.typography.titleMedium, color = Ink)
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Paper)
                        .padding(18.dp)
                ) {
                    FormattedSuggestionText(text = state.suggestion)
                }
            }

            // Items used strip
            if (state.outfitItems.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                val isEssentials = state.outfitItems.any { it.id < 0 }
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("OUTFIT PICKS", style = MaterialTheme.typography.labelMedium, color = Ash)
                        if (isEssentials) {
                            TextButton(
                                onClick = onNavigateToTryOn,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Buy similar →", style = MaterialTheme.typography.labelSmall, color = Warm)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.outfitItems.take(12)) { item ->
                            OutfitItemChip(item = item, isEssential = isEssentials, onClick = if (isEssentials) onNavigateToTryOn else null)
                        }
                    }
                }
            }
        }

        if (state.essentialsHints.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            state.essentialsHints.forEach { hint ->
                EssentialsHintBanner(
                    message = hint,
                    onNavigateToTryOn = onNavigateToTryOn,
                    onDismiss = { vm.dismissEssentialsHints() },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
        }

        if (state.imageLoading || state.outfitImagePath != null) {
            Spacer(Modifier.height(16.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Text("AI Outfit Board", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Paper)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.imageLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Ash)
                            Text("Generating outfit board…", style = MaterialTheme.typography.labelSmall, color = Ash)
                        }
                    } else if (state.outfitImagePath != null) {
                        FashionImage(
                            model = state.outfitImagePath,
                            contentDescription = "Outfit board",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                if (state.outfitImagePath != null && !state.imageLoading) {
                    Spacer(Modifier.height(12.dp))
                    if (state.imageSaved) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToProfile() }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Saved to My Looks — tap to view",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF4CAF50)
                            )
                        }
                        if (state.promptSignInToBackup) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Sign in to back up your looks to the cloud",
                                style = MaterialTheme.typography.labelSmall,
                                color = Ash,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToProfile() }
                                    .padding(vertical = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { vm.saveOutfitImage() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
                        ) {
                            Text("Save to My Looks", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = Mist, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(24.dp))

        CompleteLookSection(state = state, vm = vm)

        Spacer(Modifier.height(32.dp))
    }
}

// ── Essentials Hint Banner ────────────────────────────────────────────────────

@Composable
private fun EssentialsHintBanner(
    message: String,
    onNavigateToTryOn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFFF8E1))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("💡", style = MaterialTheme.typography.bodySmall)
        Text(
            message,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF795548),
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onNavigateToTryOn,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("Essentials →", style = MaterialTheme.typography.labelSmall, color = Warm)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Default.Close, "Close", tint = Ash, modifier = Modifier.size(14.dp))
        }
    }
}

// ── Outfit Item Chip ─────────────────────────────────────────────────────────

@Composable
private fun OutfitItemChip(item: ClothingItem, isEssential: Boolean, onClick: (() -> Unit)?) {
    val badge = if (isEssential) "Essential" else "Closet"
    val badgeColor = if (isEssential) Warm else Ink
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .width(72.dp)
            .then(onClick?.let { click -> Modifier.clickable { click() } } ?: Modifier)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Paper)
                .border(1.dp, Mist, RoundedCornerShape(10.dp))
        ) {
            FashionImage(
                model = item.imagePath,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(badgeColor)
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(badge, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
        Text(
            item.name.ifEmpty { item.category.label },
            style = MaterialTheme.typography.labelSmall,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Formatted AI Output ───────────────────────────────────────────────────────

@Composable
private fun FormattedSuggestionText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        text.lines().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Spacer(Modifier.height(4.dp))
                // Section header: **Something** or **Something:**
                line.startsWith("**") && (line.endsWith("**") || line.endsWith("**:")) -> {
                    val clean = line.replace(Regex("\\*\\*"), "").trimEnd(':').trim()
                    if (clean.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(clean, style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold, color = Ink)
                    }
                }
                // Item line: - **Label**: description
                line.matches(Regex("[-•] \\*\\*.+\\*\\*:.*")) -> {
                    val inner = line.removePrefix("- ").removePrefix("• ")
                    val boldStart = inner.indexOf("**")
                    val boldEnd = inner.indexOf("**", boldStart + 2)
                    if (boldStart >= 0 && boldEnd > boldStart) {
                        val label = inner.substring(boldStart + 2, boldEnd)
                        val rest = inner.substring(boldEnd + 2).trimStart(':', ' ')
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Ink)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                            Text(rest, style = MaterialTheme.typography.bodySmall, color = Ink,
                                modifier = Modifier.weight(1f))
                        }
                    } else {
                        Text(stripBold(line), style = MaterialTheme.typography.bodySmall, color = Ink)
                    }
                }
                // Plain bullet
                line.startsWith("- ") || line.startsWith("• ") -> {
                    val content = line.drop(2).trim()
                    Row(modifier = Modifier.padding(start = 4.dp), verticalAlignment = Alignment.Top) {
                        Text("·", style = MaterialTheme.typography.bodySmall, color = Ash,
                            modifier = Modifier.width(14.dp))
                        Text(stripBold(content), style = MaterialTheme.typography.bodySmall, color = Ash)
                    }
                }
                // Numbered point: 1. or 2. etc.
                line.matches(Regex("\\d+\\. .*")) -> {
                    val num = line.substringBefore(". ")
                    val content = line.substringAfter(". ")
                    Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(num, style = MaterialTheme.typography.labelSmall, color = Ash,
                            modifier = Modifier.width(16.dp))
                        Text(stripBold(content), style = MaterialTheme.typography.bodySmall, color = Ink,
                            modifier = Modifier.weight(1f))
                    }
                }
                else -> Text(stripBold(line), style = MaterialTheme.typography.bodySmall, color = Ink)
            }
        }
    }
}

private fun stripBold(text: String) = text.replace(Regex("\\*\\*"), "")

// ── Complete My Look ──────────────────────────────────────────────────────────

@Composable
private fun CompleteLookSection(state: OutfitUiState, vm: OutfitViewModel) {
    val allCategories = listOf(
        ClothingCategory.INNER, ClothingCategory.OUTERWEAR, ClothingCategory.PANTS,
        ClothingCategory.DRESS, ClothingCategory.SHOES, ClothingCategory.ACCESSORY, ClothingCategory.BAG
    )
    var expandedCategory by remember { mutableStateOf<ClothingCategory?>(null) }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("COMPLETE MY LOOK", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text("Select pieces you have — AI fills the rest",
                    style = MaterialTheme.typography.labelSmall, color = Ash)
            }
            if (state.completeLookPinned.isNotEmpty() || state.completeLookResults.isNotEmpty()) {
                TextButton(onClick = { vm.clearCompleteLook(); expandedCategory = null },
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Reset", style = MaterialTheme.typography.labelSmall, color = Ash)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Category chips
        allCategories.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                row.forEach { cat ->
                    val isPinned = cat in state.completeLookPinned
                    val pinnedItem = state.completeLookPinned[cat]
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPinned) Ink else Paper)
                            .border(1.dp, if (isPinned) Ink else Mist, RoundedCornerShape(8.dp))
                            .clickable {
                                vm.toggleCompleteLookCategory(cat)
                                expandedCategory = if (!isPinned) cat else if (expandedCategory == cat) null else expandedCategory
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (pinnedItem != null) {
                                Box(modifier = Modifier.size(16.dp).clip(RoundedCornerShape(3.dp))) {
                                    FashionImage(model = pinnedItem.imagePath, contentDescription = null,
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                            }
                            Text(cat.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPinned) Color.White else Ash)
                        }
                    }
                }
            }
        }

        // Wardrobe item picker for expanded category
        val showPicker = expandedCategory?.takeIf { it in state.completeLookPinned }
        if (showPicker != null) {
            val catItems = state.wardrobe.filter { it.category == showPicker }
            if (catItems.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Pick a specific ${showPicker.label.lowercase()} (optional)",
                    style = MaterialTheme.typography.labelSmall, color = Ash)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(catItems, key = { it.id }) { item ->
                        val isChosen = state.completeLookPinned[showPicker]?.id == item.id
                        GarmentThumb(
                            item = item,
                            isSelected = isChosen,
                            onClick = { vm.selectCompleteLookItem(showPicker, if (isChosen) null else item) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (state.completeLookPinned.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            val missing = allCategories.filter { it !in state.completeLookPinned }
            val missingLabel = if (missing.isEmpty()) "All selected" else "AI will suggest: ${missing.joinToString(", ") { it.label }}"
            Text(missingLabel, style = MaterialTheme.typography.labelSmall, color = if (missing.isEmpty()) Color(0xFFB00020) else Warm)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { vm.completeMyLook() },
                enabled = !state.completeLookLoading && missing.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, disabledContainerColor = Mist)
            ) {
                if (state.completeLookLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("AI is thinking…", style = MaterialTheme.typography.labelLarge, color = Color.White)
                } else {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Complete My Look", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }

        if (state.completeLookError.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(state.completeLookError, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB00020))
        }
    }

    // Results
    if (state.completeLookResults.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        state.completeLookResults.forEach { rec ->
            CompleteLookRecCard(rec = rec)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CompleteLookRecCard(rec: CompleteLookRec) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(rec.category.label.uppercase(),
                style = MaterialTheme.typography.labelLarge, color = Ink)
            Text(rec.suggestion,
                style = MaterialTheme.typography.labelSmall, color = Warm,
                modifier = Modifier.weight(1f).padding(start = 12.dp))
        }
        if (rec.reason.isNotEmpty()) {
            Text(rec.reason, style = MaterialTheme.typography.bodySmall, color = Ash)
        }
        Spacer(Modifier.height(10.dp))

        // Your wardrobe
        if (rec.wardrobeMatches.isNotEmpty()) {
            SourceRow(label = "Your Wardrobe") {
                rec.wardrobeMatches.forEach { item ->
                    GarmentThumb(item = item, isSelected = false, onClick = {})
                }
            }
        }

        // Essentials
        if (rec.essentialMatches.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SourceRow(label = "Essentials") {
                rec.essentialMatches.forEach { item ->
                    GarmentThumb(item = item, isSelected = false, onClick = {})
                }
            }
        }

        // Online shop
        Spacer(Modifier.height(10.dp))
        SourceRow(label = "Shop Online") {
            if (rec.onlineLoading) {
                Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Ash)
                }
            } else {
                rec.onlineItems.take(8).forEach { item ->
                    OnlineItemThumb(item = item)
                }
            }
        }
    }
}

@Composable
private fun SourceRow(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { content() }
        }
    }
}

@Composable
private fun OnlineItemThumb(item: EbayItem) {
    val context = LocalContext.current
    val sourceBg = when (item.source) {
        "ASOS"       -> Color(0xFF111111)
        "The Iconic" -> Color(0xFFD93F87)
        "Amazon"     -> Color(0xFFFF9900)
        else         -> Color(0xFF0064D2)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.width(80.dp)
    ) {
        Box(
            modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp))
                .background(Paper).border(1.dp, Mist, RoundedCornerShape(10.dp))
                .clickable(enabled = item.itemWebUrl.isNotEmpty()) { OpenLink.open(context, item.itemWebUrl) }
        ) {
            androidx.compose.foundation.lazy.LazyRow {} // workaround: use AsyncImage
            coil.compose.AsyncImage(
                model = item.imageUrl, contentDescription = item.title,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
            if (item.price.isNotEmpty()) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f)).padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.landedLabel(),
                        style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(3.dp)
                    .clip(RoundedCornerShape(3.dp)).background(sourceBg)
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(item.source, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
        Text(item.title.take(18), style = MaterialTheme.typography.labelSmall, color = Ink,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (item.itemWebUrl.isNotEmpty()) {
            Text("Buy →", style = MaterialTheme.typography.labelSmall, color = Warm,
                modifier = Modifier.clickable { OpenLink.open(context, item.itemWebUrl) })
        }
    }
}

@Composable
private fun WeatherCard(
    weather: WeatherInfo?,
    isLoading: Boolean,
    city: String,
    editingCity: Boolean,
    cityInput: String,
    onCityInputChange: (String) -> Unit,
    onEditCity: () -> Unit,
    onConfirmCity: () -> Unit,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Paper)
            .padding(16.dp)
    ) {
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp, color = Ash)
                Spacer(Modifier.width(8.dp))
                Text("Checking the weather…", style = MaterialTheme.typography.bodySmall, color = Ash)
            }
        } else if (editingCity) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = cityInput,
                    onValueChange = onCityInputChange,
                    label = { Text("City") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onConfirmCity,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) { Text("Confirm") }
            }
        } else if (weather != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(weather.emoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "${weather.tempC}°C  ${weather.descriptionZh}",
                                style = MaterialTheme.typography.titleSmall,
                                color = Ink
                            )
                            Text(
                                "${weather.city}  ·  Humidity ${weather.humidity}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Ash
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(weather.dressHint, style = MaterialTheme.typography.labelSmall, color = Warm)
                }
                Row {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Ash, modifier = Modifier.size(16.dp))
                    }
                    TextButton(onClick = onEditCity) {
                        Text("Change city", style = MaterialTheme.typography.labelSmall, color = Ash)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Weather unavailable", style = MaterialTheme.typography.bodySmall, color = Ash)
                Row {
                    TextButton(onClick = onRefresh) { Text("Retry", style = MaterialTheme.typography.labelSmall, color = Ash) }
                    TextButton(onClick = onEditCity) { Text("Change city", style = MaterialTheme.typography.labelSmall, color = Ash) }
                }
            }
        }
    }
}
