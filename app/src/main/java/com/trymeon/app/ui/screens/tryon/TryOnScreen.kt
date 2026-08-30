package com.trymeon.app.ui.screens.tryon

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.trymeon.app.data.BasicWardrobeProvider
import com.trymeon.app.data.remote.ScraperApiService
import com.trymeon.app.data.remote.ClaudeApiService
import com.trymeon.app.data.remote.EbayItem
import com.trymeon.app.ui.components.landedLabel
import com.trymeon.app.data.remote.ReplicateApiService
import com.trymeon.app.data.repository.UserProfileRepository
import com.trymeon.app.data.repository.WardrobeRepository
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.share.LookCard
import com.trymeon.app.share.ShareCardRenderer
import com.trymeon.app.ui.components.FashionImage
import androidx.compose.ui.text.style.TextAlign
import com.trymeon.app.ui.theme.Ash
import com.trymeon.app.ui.theme.Ink
import com.trymeon.app.ui.theme.Mist
import com.trymeon.app.ui.theme.Paper
import com.trymeon.app.ui.theme.Warm
import com.trymeon.app.util.OpenLink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryOnScreen(
    wardrobeRepository: WardrobeRepository,
    profileRepository: UserProfileRepository,
    claudeApiService: ClaudeApiService,
    apiKey: String,
    replicateApiKey: String,
    contentPadding: PaddingValues,
    ebayClientId: String = "",
    ebayClientSecret: String = "",
    rapidApiKey: String = "",
    scraperApiKey: String = "",
    serpApiKey: String = "",
    ebayAffiliateCampaignId: String = "",
    amazonAssociateTag: String = "",
    styleKeywords: Set<String> = emptySet()
) {
    val context = LocalContext.current
    val replicateService = remember { ReplicateApiService() }
    val vm: TryOnViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            TryOnViewModel(
                wardrobeRepository, profileRepository,
                claudeApiService, replicateService,
                apiKey, replicateApiKey,
                amazonAssociateTag = amazonAssociateTag,
                styleKeywords = styleKeywords,
                catalog = com.trymeon.app.data.sourcing.ShoppingCatalogFactory.create(
                    context, claudeApiService, apiKey, rapidApiKey
                ),
                virtualModels = com.trymeon.app.data.tryon.VirtualModelStore(
                    com.trymeon.app.AppSettings(context),
                    claudeApiService,
                    apiKey,
                    fashn = replicateService,
                    fashnKey = replicateApiKey
                ),
                priceExpectation = { com.trymeon.app.AppSettings(context).priceExpectation }
            ) as T
    })

    val state by vm.uiState.collectAsState()

    // The ViewModel picks this up in init, but only on first creation — coming
    // back to an already-built tab would otherwise drop the garment silently.
    LaunchedEffect(Unit) {
        com.trymeon.app.data.sourcing.PendingTryOn.consume()?.let {
            vm.selectExternalGarment(it.item, it.category)
        }
    }

    val allItems = if (state.wardrobe.isEmpty()) BasicWardrobeProvider.items else state.wardrobe
    val selectedIds = state.selectedClothingIds

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setUserPhoto(context, uri)
    }

    val effectivePhotoPath = state.userPhotoPath.takeIf { it.isNotEmpty() }
        ?: state.profile?.bodyImagePath?.takeIf { it.isNotEmpty() }
        ?: state.profile?.faceImagePath?.takeIf { it.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .padding(bottom = contentPadding.calculateBottomPadding())
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("TRY ON", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light)
            Text("AI POWERED", style = MaterialTheme.typography.labelMedium, color = Warm)
        }
        HorizontalDivider(color = Mist)
        Spacer(Modifier.height(24.dp))

        // Step 01: face photo
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            StepLabel("01", "Upload Your Photo")
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Paper)
                    .clickable { photoPicker.launch("image/*") }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(Mist),
                    contentAlignment = Alignment.Center
                ) {
                    if (effectivePhotoPath != null) {
                        FashionImage(model = effectivePhotoPath, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Ash, modifier = Modifier.size(24.dp))
                    }
                }
                // weight(1f) prevents this column from being squeezed by the right badge
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (effectivePhotoPath != null) "Photo uploaded" else "Tap to upload",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        if (effectivePhotoPath != null) "Full-body photos give more accurate results"
                        else "Full-body photo recommended\nA face close-up also works",
                        style = MaterialTheme.typography.bodySmall, color = Ash
                    )
                }
                if (effectivePhotoPath != null) {
                    Text("Change", style = MaterialTheme.typography.labelMedium, color = Ash)
                }
            }
        }

        if (effectivePhotoPath != null) {
            Spacer(Modifier.height(20.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                YourModel(
                    portraitPath = state.virtualModelPath,
                    building = state.buildingModel,
                    onRegenerate = { vm.regenerateModel() }
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Step 02: garment selection
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepLabel("02", "Select Garments")
            Spacer(Modifier.width(12.dp))
            val totalSelected = state.selectedGarments.size
            if (totalSelected > 0) {
                Text("$totalSelected selected", style = MaterialTheme.typography.labelMedium, color = Warm)
            } else {
                Text("from any tab below", style = MaterialTheme.typography.labelSmall, color = Ash)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Garment sub-tabs
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                GarmentTabChip(
                    label = "My Wardrobe",
                    selected = state.garmentTab == GarmentTab.WARDROBE,
                    onClick = { vm.setGarmentTab(GarmentTab.WARDROBE) }
                )
            }
            item {
                GarmentTabChip(
                    label = "Essentials",
                    selected = state.garmentTab == GarmentTab.ESSENTIALS,
                    onClick = { vm.setGarmentTab(GarmentTab.ESSENTIALS) }
                )
            }
            item {
                GarmentTabChip(
                    label = "Shop",
                    selected = state.garmentTab == GarmentTab.EBAY,
                    onClick = { vm.setGarmentTab(GarmentTab.EBAY) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Content per tab
        when (state.garmentTab) {
            GarmentTab.WARDROBE    -> WardrobeGarmentSection(allItems, selectedIds, vm)
            GarmentTab.ESSENTIALS  -> EssentialsGarmentSection(state, vm)
            GarmentTab.EBAY        -> EbayGarmentSection(state, vm)
        }

        Spacer(Modifier.height(28.dp))

        // Try on button
        // Selected garments summary chips (cross-tab)
        if (state.selectedGarments.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(state.selectedGarments.values.toList(), key = { it.slotKey }) { garment ->
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(Ink).padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "${garment.slotKey.lowercase().replaceFirstChar { it.uppercase() }}: ${garment.displayLabel}",
                            style = MaterialTheme.typography.labelSmall, color = Color.White
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
            val canStart = !state.isLoading && state.selectedGarments.isNotEmpty()
            Button(
                onClick = { vm.startTryOn() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = canStart,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, disabledContainerColor = Mist)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp, color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(state.loadingStep.ifEmpty { "Generating..." }, style = MaterialTheme.typography.labelLarge, color = Color.White)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Start AI Try-On", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }

        if (state.error.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF0F0)).padding(14.dp)) {
                Text(state.error, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB00020))
            }
        }

        if (state.resultViews.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            TryOnResult(
                views = state.resultViews,
                generatingViews = state.generatingViews,
                analysis = state.analysis,
                credits = state.shareCredits,
                imageSaved = state.imageSaved,
                promptSignInToBackup = state.promptSignInToBackup,
                onSave = { path -> vm.saveImage(path) }
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GarmentTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Ink else Paper)
            .border(1.dp, if (selected) Ink else Mist, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else Ash
        )
    }
}

@Composable
private fun WardrobeGarmentSection(
    allItems: List<ClothingItem>,
    selectedIds: Set<Long>,
    vm: TryOnViewModel
) {
    val categoryOrder = listOf(
        ClothingCategory.INNER, ClothingCategory.OUTERWEAR, ClothingCategory.PANTS,
        ClothingCategory.DRESS, ClothingCategory.SHOES, ClothingCategory.ACCESSORY, ClothingCategory.BAG
    )
    val grouped = allItems.groupBy { it.category }
    val unsupported = setOf(ClothingCategory.ACCESSORY)
    val state by vm.uiState.collectAsState()

    categoryOrder.forEach { cat ->
        val items = grouped[cat] ?: return@forEach
        val isSupported = cat !in unsupported
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(cat.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (isSupported) Ink else Ash,
                    modifier = Modifier.weight(1f))
                if (!isSupported) Text("Not supported", style = MaterialTheme.typography.labelSmall, color = Ash)
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.id }) { item ->
                    GarmentThumb(item = item, isSelected = item.id in selectedIds, enabled = isSupported,
                        onClick = { if (isSupported) vm.toggleClothingItem(item, GarmentTab.WARDROBE) })
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EssentialsGarmentSection(state: TryOnUiState, vm: TryOnViewModel) {
    val selectedIds = state.selectedClothingIds
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.essentialsCategories.forEach { catState ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(catState.label.ifEmpty { catState.category.label }.uppercase(), style = MaterialTheme.typography.labelMedium, color = Ink)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Basic", style = MaterialTheme.typography.labelSmall, color = Ash)
                        IconButton(
                            onClick = { vm.refreshEssentialsCategory(catState.category) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Ash, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(catState.items, key = { it.id }) { item ->
                        EssentialsThumb(
                            item = item,
                            isSelected = item.id in selectedIds,
                            isFavorited = item.id in state.favoriteEssentialIds,
                            onSelect = { vm.toggleClothingItem(item, GarmentTab.ESSENTIALS) },
                            onShopSimilar = { vm.shopForSimilar(item) },
                            onFavorite = { vm.addEssentialToFavorites(item) }
                        )
                    }
                }
            }
        }
    }

    // Shop Similar bottom sheet
    if (state.shopSimilarItem != null) {
        ModalBottomSheet(onDismissRequest = { vm.closeShopSimilar() }) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Shop Similar", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text(state.shopSimilarItem.name.ifEmpty { state.shopSimilarItem.category.label },
                            style = MaterialTheme.typography.labelSmall, color = Ash)
                    }
                    TextButton(onClick = { vm.closeShopSimilar() }) {
                        Text("Done", style = MaterialTheme.typography.labelMedium, color = Ash)
                    }
                }
                when {
                    state.shopSimilarLoading -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Ink)
                            Text("Searching…", style = MaterialTheme.typography.labelSmall, color = Ash)
                        }
                    }
                    state.shopSimilarError.isNotEmpty() ->
                        Text(state.shopSimilarError, style = MaterialTheme.typography.bodySmall, color = Ash)
                    state.shopSimilarResults.isEmpty() ->
                        Text("No results found", style = MaterialTheme.typography.bodySmall, color = Ash)
                    else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.shopSimilarResults, key = { it.itemId }) { item ->
                            val context = LocalContext.current
                            val catLabel = state.shopSimilarItem!!.category.label.lowercase()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.width(92.dp)
                            ) {
                                EbayGarmentThumb(
                                    item = item,
                                    isSelected = item.itemId in state.selectedEbayIds,
                                    isFavorited = item.itemId in state.favoriteEbayIds,
                                    onClick = { vm.toggleEbayItem(item, catLabel) },
                                    onFavorite = { vm.addEbayToFavorites(item, catLabel) }
                                )
                                if (item.source.isNotEmpty()) {
                                    Text(
                                        item.source,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Ash,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (item.itemWebUrl.isNotEmpty()) {
                                    Text(
                                        "Buy →",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Warm,
                                        modifier = Modifier.clickable { OpenLink.open(context, item.itemWebUrl) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EssentialsThumb(
    item: ClothingItem,
    isSelected: Boolean,
    isFavorited: Boolean,
    onSelect: () -> Unit,
    onShopSimilar: () -> Unit,
    onFavorite: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.width(92.dp)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Paper)
                .then(if (isSelected) Modifier.border(2.dp, Ink, RoundedCornerShape(12.dp))
                      else Modifier.border(1.dp, Mist, RoundedCornerShape(12.dp)))
                .clickable(onClick = onSelect)
        ) {
            FashionImage(
                model = item.imagePath,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isSelected) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .size(20.dp).background(Ink, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            IconButton(
                onClick = onFavorite,
                enabled = !isFavorited,
                modifier = Modifier.align(Alignment.TopStart).size(28.dp)
            ) {
                Icon(
                    if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorited) Color(0xFFE91E63) else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            item.name,
            style = MaterialTheme.typography.labelSmall,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Shop similar →",
            style = MaterialTheme.typography.labelSmall,
            color = Warm,
            modifier = Modifier.clickable(onClick = onShopSimilar)
        )
    }
}

@Composable
private fun EbayGarmentSection(state: TryOnUiState, vm: TryOnViewModel) {
    when {
        state.ebayRecoLoading -> {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Ink)
                    Text("AI is analyzing your wardrobe for the best secondhand picks…", style = MaterialTheme.typography.labelSmall, color = Ash)
                }
            }
        }
        state.ebayRecoError.isNotEmpty() -> {
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.ebayRecoError, style = MaterialTheme.typography.labelSmall, color = Ash)
                TextButton(onClick = { vm.refreshEbayRecommendations() }) { Text("Reload", color = Ink) }
            }
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Hint + refresh
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pick one top and one bottom to try on together", style = MaterialTheme.typography.labelSmall, color = Ash)
                    IconButton(onClick = { vm.refreshEbayRecommendations() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Ash, modifier = Modifier.size(18.dp))
                    }
                }

                state.ebayCategories.forEachIndexed { index, cat ->
                    EbayCategoryRow(
                        category = cat,
                        selectedIds = state.selectedEbayIds,
                        favoriteIds = state.favoriteEbayIds,
                        onToggle = { item -> vm.toggleEbayItem(item, cat.name) },
                        onFavorite = { item -> vm.addEbayToFavorites(item, cat.name) },
                        onRefresh = { vm.refreshEbayCategory(index) }
                    )
                }

                val ebaySelected = state.selectedGarments.values.filter { it.ebayItem != null }
                if (ebaySelected.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp)).background(Paper).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Selected outfit", style = MaterialTheme.typography.labelMedium, color = Ash)
                        ebaySelected.forEach { g ->
                            Text("· ${g.ebayItem!!.title.take(30)}…", style = MaterialTheme.typography.labelSmall, color = Ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EbayCategoryRow(
    category: EbayTryOnCategory,
    selectedIds: Set<String>,
    favoriteIds: Set<String>,
    onToggle: (EbayItem) -> Unit,
    onFavorite: (EbayItem) -> Unit,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(category.name, style = MaterialTheme.typography.labelMedium, color = Ink, fontWeight = FontWeight.Medium)
                Text(category.reason, style = MaterialTheme.typography.labelSmall, color = Ash, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh ${category.name}", tint = Ash, modifier = Modifier.size(16.dp))
            }
        }
        when {
            category.loading -> Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Ink)
                Text("Customising for you…", style = MaterialTheme.typography.labelSmall, color = Ash)
            }
            category.items.isEmpty() -> Text(
                if (category.error.isNotEmpty()) category.error else "No items available",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.labelSmall, color = Ash
            )
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(category.items, key = { it.itemId }) { item ->
                    val context = LocalContext.current
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.width(92.dp)
                    ) {
                        EbayGarmentThumb(
                            item = item,
                            isSelected = item.itemId in selectedIds,
                            isFavorited = item.itemId in favoriteIds,
                            onClick = { onToggle(item) },
                            onFavorite = { onFavorite(item) }
                        )
                        if (item.source.isNotEmpty()) {
                            Text(
                                item.source,
                                style = MaterialTheme.typography.labelSmall,
                                color = Ash,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (item.itemWebUrl.isNotEmpty()) {
                            Text(
                                "Buy →",
                                style = MaterialTheme.typography.labelSmall,
                                color = Warm,
                                modifier = Modifier.clickable { OpenLink.open(context, item.itemWebUrl) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EbayGarmentThumb(
    item: EbayItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    isFavorited: Boolean = false,
    onFavorite: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Paper)
            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Ink else Mist, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (isSelected) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    .size(20.dp).background(Ink, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        if (onFavorite != null) {
            IconButton(
                onClick = onFavorite,
                enabled = !isFavorited,
                modifier = Modifier.align(Alignment.TopStart).size(24.dp)
            ) {
                Icon(
                    if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorited) Color(0xFFE91E63) else Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        if (item.price.isNotEmpty()) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f)).padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(item.landedLabel(), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}


@Composable
fun StepLabel(number: String, title: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number, style = MaterialTheme.typography.labelMedium, color = Ash)
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

@Composable
fun GarmentThumb(item: ClothingItem, isSelected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)).background(Paper)
            .then(if (isSelected) Modifier.border(2.dp, Ink, RoundedCornerShape(10.dp))
                  else Modifier.border(1.dp, Mist, RoundedCornerShape(10.dp)))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        FashionImage(
            model = item.imagePath, contentDescription = item.name,
            modifier = Modifier.fillMaxSize().then(if (!enabled) Modifier.background(Color.White.copy(alpha = 0.5f)) else Modifier),
            contentScale = ContentScale.Crop, alpha = if (enabled) 1f else 0.4f
        )
        if (isSelected) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                .size(20.dp).background(Ink, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

private val VIEW_LABELS = listOf("Front", "Side")

@Composable
fun TryOnResult(
    views: List<String>,
    generatingViews: Boolean,
    analysis: String,
    credits: List<ShareCardRenderer.Credit> = emptyList(),
    imageSaved: Boolean = false,
    promptSignInToBackup: Boolean = false,
    onSave: ((String) -> Unit)? = null
) {
    val pagerState = rememberPagerState { views.size.coerceAtLeast(1) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var autoRotating by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf("") }

    // Auto-rotate: advance one page every 1.2 seconds, loop
    LaunchedEffect(autoRotating, views.size) {
        if (autoRotating && views.size > 1) {
            while (autoRotating) {
                delay(1200)
                val next = (pagerState.currentPage + 1) % views.size
                pagerState.animateScrollToPage(next, animationSpec = tween(600))
            }
        }
    }
    // Stop auto-rotate if user swipes manually
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) autoRotating = false
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("RESULT", style = MaterialTheme.typography.titleMedium, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (generatingViews) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = Ash)
                        Text("Generating more angles…", style = MaterialTheme.typography.labelSmall, color = Ash)
                    }
                }
                // 360 rotate button — only shown when there are multiple views
                if (views.size > 1) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (autoRotating) Ink else Paper)
                            .border(1.dp, if (autoRotating) Ink else Mist, RoundedCornerShape(8.dp))
                            .clickable { autoRotating = !autoRotating }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Rotate90DegreesCw, contentDescription = "Rotate",
                                modifier = Modifier.size(12.dp),
                                tint = if (autoRotating) Color.White else Ash
                            )
                            Text("360°", style = MaterialTheme.typography.labelSmall,
                                color = if (autoRotating) Color.White else Ash)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // View label tabs
        if (views.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                views.indices.forEach { i ->
                    val label = VIEW_LABELS.getOrElse(i) { "View ${i + 1}" }
                    val selected = pagerState.currentPage == i
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) Ink else Paper)
                            .clickable { scope.launch { autoRotating = false; pagerState.animateScrollToPage(i) } }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Color.White else Ash)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paper)) {
                FashionImage(
                    model = views[page], contentDescription = VIEW_LABELS.getOrElse(page) { "Try-on result" },
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.6f),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Page dots
        if (views.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                views.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (pagerState.currentPage == i) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (pagerState.currentPage == i) Ink else Mist)
                    )
                }
            }
        }

        if (onSave != null) {
            Spacer(Modifier.height(12.dp))
            if (imageSaved) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Saved to My Looks",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF4CAF50)
                    )
                }
                if (promptSignInToBackup) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Sign in from the Profile tab to back up your looks to the cloud",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ash,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val currentPath = views.getOrElse(pagerState.currentPage) { views.first() }
                OutlinedButton(
                    onClick = { onSave(currentPath) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
                ) {
                    Text("Save to My Looks", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Sharing is the app's only free distribution channel, so it gets the
        // filled button and every generated angle — a front/side pair on a
        // branded card reads as a lookbook, a lone screenshot reads as nothing.
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    sharing = true
                    shareError = ""
                    val shared = LookCard.share(
                        context = context,
                        paths = views,
                        credits = credits,
                        caption = "Styled with Wardrobe AI"
                    )
                    if (!shared) shareError = "Couldn't build the share card — try again"
                    sharing = false
                }
            },
            enabled = !sharing && views.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink)
        ) {
            if (sharing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White
                )
            } else {
                Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (views.size > 1) "Share this look" else "Share",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        if (shareError.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                shareError, style = MaterialTheme.typography.labelSmall, color = Ash,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
        }

        if (analysis.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Paper).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Warm, modifier = Modifier.size(14.dp))
                    Text("AI Style Tips", style = MaterialTheme.typography.labelLarge, color = Ink)
                }
                Text(analysis, style = MaterialTheme.typography.bodySmall, color = Ash)
            }
        }
    }
}

/**
 * The model, as something the user owns.
 *
 * Built once from their photo and dressed for every garment after, so it is
 * worth showing rather than hiding inside the try-on step: it is the reason
 * the same person comes back each time, and if it does not look like them,
 * seeing that here is far better than discovering it in a finished look.
 */
@Composable
internal fun YourModel(
    portraitPath: String,
    building: Boolean,
    onRegenerate: () -> Unit
) {
    var showFull by remember { mutableStateOf(false) }
    val canOpen = portraitPath.isNotEmpty() && !building
    if (showFull) FullModelViewer(portraitPath) { showFull = false }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Mist, RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(64.dp).aspectRatio(0.66f)
                .clip(RoundedCornerShape(8.dp)).background(Paper)
                .clickable(enabled = canOpen) { showFull = true },
            contentAlignment = Alignment.Center
        ) {
            when {
                building -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp, color = Ash
                )
                portraitPath.isNotEmpty() -> FashionImage(
                    model = portraitPath, contentDescription = "Your model",
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
                else -> Icon(
                    Icons.Default.AutoAwesome, contentDescription = null,
                    tint = Ash, modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("YOUR MODEL", style = MaterialTheme.typography.labelSmall, color = Warm)
            Text(
                when {
                    building -> "Building it from your photo…"
                    portraitPath.isNotEmpty() -> "Every try-on is this same person · tap to view"
                    else -> "Built from your photo on the first try-on"
                },
                style = MaterialTheme.typography.bodySmall, color = Ash
            )
        }
        if (portraitPath.isNotEmpty() && !building) {
            Text(
                "Redo",
                style = MaterialTheme.typography.labelMedium,
                color = Ink,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onRegenerate)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/** Full-screen look at the model portrait; tap anywhere or the close button to dismiss. */
@Composable
private fun FullModelViewer(portraitPath: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss)
        ) {
            FashionImage(
                model = portraitPath,
                contentDescription = "Your model, full size",
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
