package com.example.myapplication.ui.screens.shop

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myapplication.data.remote.AliExpressApiService
import com.example.myapplication.data.remote.AliExpressItem
import com.example.myapplication.data.remote.AmazonApiService
import com.example.myapplication.data.remote.AmazonItem
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.remote.EbayApiService
import com.example.myapplication.data.remote.EbayItem
import com.example.myapplication.data.remote.ReplicateApiService
import com.example.myapplication.data.remote.SheinApiService
import com.example.myapplication.data.remote.SheinItem
import com.example.myapplication.data.remote.TaobaoApiService
import com.example.myapplication.data.remote.TaobaoItem
import com.example.myapplication.data.remote.VintedApiService
import com.example.myapplication.data.remote.VintedItem
import com.example.myapplication.data.repository.UserProfileRepository
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.domain.model.ClothingCategory
import com.example.myapplication.ui.theme.Ash
import com.example.myapplication.ui.theme.Ink
import com.example.myapplication.ui.theme.InkLight
import com.example.myapplication.ui.theme.Mist
import com.example.myapplication.ui.theme.Paper
import com.example.myapplication.ui.theme.Warm
import java.net.URLEncoder

// eBay Partner Network affiliate link — appends tracking params when campaign ID is set
// Rotation ID 705-53470-19255-0 = eBay AU
private fun ebayAffiliateUrl(originalUrl: String, campaignId: String): String {
    if (campaignId.isBlank() || originalUrl.isBlank()) return originalUrl
    val sep = if ('?' in originalUrl) "&" else "?"
    return "$originalUrl${sep}mkevt=1&mkcid=1&mkrid=705-53470-19255-0&campid=$campaignId&toolid=10001&customid="
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    ebayClientId: String,
    ebayClientSecret: String,
    contentPadding: PaddingValues,
    wardrobeRepository: WardrobeRepository? = null,
    claudeApiService: ClaudeApiService? = null,
    replicateApiService: ReplicateApiService? = null,
    apiKey: String = "",
    replicateApiKey: String = "",
    profileRepository: UserProfileRepository? = null,
    amazonAccessKey: String = "",
    amazonSecretKey: String = "",
    amazonAssociateTag: String = "",
    rapidApiKey: String = "",
    ebayAffiliateCampaignId: String = ""
) {
    val context = LocalContext.current
    val ebayService = remember { EbayApiService() }
    val amazonService = remember { AmazonApiService() }
    val taobaoService = remember { TaobaoApiService() }
    val sheinService = remember { SheinApiService() }
    val vintedService = remember { VintedApiService() }
    val aliExpressService = remember { AliExpressApiService() }

    val vm: ShopViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            ShopViewModel(
                ebayService, ebayClientId, ebayClientSecret,
                wardrobeRepository, claudeApiService, replicateApiService,
                apiKey, replicateApiKey, profileRepository,
                amazonService, amazonAccessKey, amazonSecretKey, amazonAssociateTag,
                taobaoService, rapidApiKey,
                sheinService, vintedService, aliExpressService
            ) as T
    })

    val state by vm.uiState.collectAsState()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setUserImage(context, uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .padding(bottom = contentPadding.calculateBottomPadding())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SHOP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (vm.amazonEnabled) Text("eBay  ·  Amazon  ·  AU", style = MaterialTheme.typography.labelMedium, color = Ash)
                else Text("eBay AU  ·  Pre-owned", style = MaterialTheme.typography.labelMedium, color = Ash)
                if (state.activeTab == ShopTab.RECOMMENDED && !state.recoLoading) {
                    IconButton(onClick = { vm.refreshRecommendations() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, null, tint = Ash, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        TabRow(
            selectedTabIndex = state.activeTab.ordinal,
            containerColor = Color.White,
            contentColor = Ink,
            divider = { HorizontalDivider(color = Mist) }
        ) {
            Tab(selected = state.activeTab == ShopTab.RECOMMENDED, onClick = { vm.setTab(ShopTab.RECOMMENDED) },
                text = { Text("For You", style = MaterialTheme.typography.labelMedium) })
            Tab(selected = state.activeTab == ShopTab.SEARCH, onClick = { vm.setTab(ShopTab.SEARCH) },
                text = { Text("Search", style = MaterialTheme.typography.labelMedium) })
        }

        when (state.activeTab) {
            ShopTab.RECOMMENDED -> RecommendedTab(state, onItemClick = { vm.selectItem(it) })
            ShopTab.SEARCH -> SearchTab(
                state, vm,
                amazonEnabled     = vm.amazonEnabled,
                taobaoEnabled     = vm.taobaoEnabled,
                sheinEnabled      = vm.sheinEnabled,
                vintedEnabled     = vm.vintedEnabled,
                aliExpressEnabled = vm.aliExpressEnabled
            )
        }
    }

    if (state.showDetailSheet && state.selectedItem != null) {
        ModalBottomSheet(
            onDismissRequest = { vm.dismissDetail() },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            ItemDetailSheet(
                item = state.selectedItem!!,
                aiSuggestion = state.aiSuggestion,
                aiLoading = state.aiLoading,
                userImagePath = state.userImagePath,
                tryOnResult = state.tryOnResult,
                tryOnLoading = state.tryOnLoading,
                tryOnError = state.tryOnError,
                canTryOn = replicateApiService != null && replicateApiKey.isNotBlank(),
                onPickPhoto = { imagePicker.launch("image/*") },
                onTryOn = { vm.startTryOn() },
                affiliateCampaignId = ebayAffiliateCampaignId
            )
        }
    }
}

// ─── Recommended Tab ─────────────────────────────────────────────────────────

@Composable
private fun RecommendedTab(state: ShopUiState, onItemClick: (EbayItem) -> Unit) {
    when {
        state.recoLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator(color = Ink, strokeWidth = 1.5.dp)
                Text("AI is analyzing your wardrobe…", style = MaterialTheme.typography.labelMedium, color = Ash)
            }
        }
        state.recoError.isNotEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(state.recoError, style = MaterialTheme.typography.bodySmall, color = Ash)
        }
        else -> LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(state.recommendations) { reco ->
                RecommendationSection(reco = reco, onItemClick = onItemClick)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun RecommendationSection(reco: ShopRecommendation, onItemClick: (EbayItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(reco.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Ink)
            Text(reco.reason, style = MaterialTheme.typography.labelSmall, color = Ash)
        }
        when {
            reco.loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Ink)
                    Text("Searching eBay…", style = MaterialTheme.typography.labelSmall, color = Ash)
                }
            }
            reco.items.isEmpty() -> Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(reco.error.ifEmpty { "No items found" }, style = MaterialTheme.typography.labelSmall, color = Ash)
            }
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(reco.items, key = { it.itemId }) { item ->
                    ShopItemCard(imageUrl = item.imageUrl, title = item.title, price = "${item.currency} ${item.price}",
                        badge = item.condition.take(10), width = 150.dp, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

// ─── Search Tab ───────────────────────────────────────────────────────────────

@Composable
private fun SearchTab(
    state: ShopUiState,
    vm: ShopViewModel,
    amazonEnabled: Boolean,
    taobaoEnabled: Boolean = false,
    sheinEnabled: Boolean = false,
    vintedEnabled: Boolean = false,
    aliExpressEnabled: Boolean = false
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(12.dp))

        // Search bar
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { vm.setQuery(it) },
                placeholder = { Text("Search fashion…", color = Ash) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.search() }),
                trailingIcon = {
                    IconButton(onClick = { keyboard?.hide(); vm.search() }) {
                        Icon(Icons.Default.Search, null, tint = Ink)
                    }
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        // Platform chips
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                PlatformChip(label = "All", selected = state.activePlatform == ShopPlatform.ALL) {
                    vm.setPlatform(ShopPlatform.ALL)
                }
            }
            item {
                PlatformChip(label = "eBay AU", selected = state.activePlatform == ShopPlatform.EBAY) {
                    vm.setPlatform(ShopPlatform.EBAY)
                }
            }
            if (amazonEnabled) {
                item {
                    PlatformChip(label = "Amazon AU", selected = state.activePlatform == ShopPlatform.AMAZON) {
                        vm.setPlatform(ShopPlatform.AMAZON)
                    }
                }
            }
            if (taobaoEnabled) {
                item {
                    PlatformChip(label = "淘宝", selected = state.activePlatform == ShopPlatform.TAOBAO) {
                        vm.setPlatform(ShopPlatform.TAOBAO)
                    }
                }
            }
            if (sheinEnabled) {
                item {
                    PlatformChip(label = "SHEIN", selected = state.activePlatform == ShopPlatform.SHEIN) {
                        vm.setPlatform(ShopPlatform.SHEIN)
                    }
                }
            }
            if (vintedEnabled) {
                item {
                    PlatformChip(label = "Vinted", selected = state.activePlatform == ShopPlatform.VINTED) {
                        vm.setPlatform(ShopPlatform.VINTED)
                    }
                }
            }
            if (aliExpressEnabled) {
                item {
                    PlatformChip(label = "AliExpress", selected = state.activePlatform == ShopPlatform.ALIEXPRESS) {
                        vm.setPlatform(ShopPlatform.ALIEXPRESS)
                    }
                }
            }
            // External links — open in browser
            item {
                ExternalLinkChip(label = "ASOS ↗") {
                    val q = URLEncoder.encode(state.query.ifBlank { "fashion" }, "UTF-8")
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.asos.com/au/search/?q=$q")))
                }
            }
            item {
                ExternalLinkChip(label = "THE ICONIC ↗") {
                    val q = URLEncoder.encode(state.query.ifBlank { "fashion" }, "UTF-8")
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.theiconic.com.au/search/?q=$q")))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        CategoryFilterRow(selected = state.selectedCategory, onSelect = { vm.setCategory(it) })
        Spacer(Modifier.height(8.dp))

        // Results
        val platformHint = buildString {
            append("eBay AU")
            if (amazonEnabled) append(" · Amazon AU")
            if (taobaoEnabled) append(" · 淘宝")
            if (sheinEnabled) append(" · SHEIN")
            if (vintedEnabled) append(" · Vinted")
            if (aliExpressEnabled) append(" · AliExpress")
        }
        when {
            state.isLoading && !state.hasResults -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Ink, strokeWidth = 1.5.dp)
                    Text("Searching…", style = MaterialTheme.typography.labelMedium, color = Ash)
                }
            }
            !state.hasSearched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Search fashion across $platformHint", style = MaterialTheme.typography.bodySmall, color = Ash)
            }
            !state.hasResults && !state.isLoading -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                val errors = listOfNotNull(
                    state.ebayError.takeIf { it.isNotEmpty() }?.let { "eBay: $it" },
                    state.amazonError.takeIf { it.isNotEmpty() }?.let { "Amazon: $it" },
                    state.taobaoError.takeIf { it.isNotEmpty() }?.let { "淘宝: $it" },
                    state.sheinError.takeIf { it.isNotEmpty() }?.let { "SHEIN: $it" },
                    state.vintedError.takeIf { it.isNotEmpty() }?.let { "Vinted: $it" },
                    state.aliExpressError.takeIf { it.isNotEmpty() }?.let { "AliExpress: $it" }
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No results found", style = MaterialTheme.typography.bodyMedium, color = Ash)
                    errors.forEach { err ->
                        Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                // eBay results
                if (state.ebayResults.isNotEmpty() || state.ebayLoading) {
                    item {
                        ResultSectionHeader(
                            platform = "eBay AU",
                            count = state.ebayResults.size,
                            loading = state.ebayLoading,
                            badge = "Pre-owned",
                            badgeColor = Color(0xFF0064D2)
                        )
                    }
                    if (state.ebayLoading) {
                        item { LoadingRow(label = "Searching eBay…") }
                    } else {
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.ebayResults, key = { it.itemId }) { item ->
                                    ShopItemCard(
                                        imageUrl = item.imageUrl, title = item.title,
                                        price = "${item.currency} ${item.price}",
                                        badge = item.condition.take(10), width = 160.dp,
                                        onClick = { vm.selectItem(item) }
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // Amazon results
                if (amazonEnabled && (state.amazonResults.isNotEmpty() || state.amazonLoading)) {
                    item {
                        ResultSectionHeader(
                            platform = "Amazon AU",
                            count = state.amazonResults.size,
                            loading = state.amazonLoading,
                            badge = "New",
                            badgeColor = Color(0xFFFF9900)
                        )
                    }
                    if (state.amazonLoading) {
                        item { LoadingRow(label = "Searching Amazon…") }
                    } else {
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.amazonResults, key = { it.asin }) { item ->
                                    val ctx = context
                                    ShopItemCard(
                                        imageUrl = item.imageUrl, title = item.title, price = item.price,
                                        badge = null, width = 160.dp, platformTag = "Amazon",
                                        onClick = {
                                            if (item.itemWebUrl.isNotEmpty())
                                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.itemWebUrl)))
                                        }
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // Taobao results
                if (taobaoEnabled && (state.taobaoResults.isNotEmpty() || state.taobaoLoading)) {
                    item {
                        ResultSectionHeader(
                            platform = "淘宝 Taobao",
                            count = state.taobaoResults.size,
                            loading = state.taobaoLoading,
                            badge = "淘宝",
                            badgeColor = Color(0xFFFF4400)
                        )
                    }
                    if (state.taobaoLoading) {
                        item { LoadingRow(label = "Searching 淘宝…") }
                    } else {
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.taobaoResults, key = { it.itemId.ifEmpty { it.itemIdStr } }) { item ->
                                    val ctx = context
                                    TaobaoItemCard(item = item, width = 160.dp) {
                                        if (item.itemUrl.isNotEmpty())
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.itemUrl)))
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // SHEIN results
                if (sheinEnabled && (state.sheinResults.isNotEmpty() || state.sheinLoading)) {
                    item {
                        ResultSectionHeader(
                            platform = "SHEIN",
                            count = state.sheinResults.size,
                            loading = state.sheinLoading,
                            badge = "SHEIN",
                            badgeColor = Color(0xFFCC2233)
                        )
                    }
                    if (state.sheinLoading) {
                        item { LoadingRow(label = "Searching SHEIN…") }
                    } else {
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.sheinResults, key = { it.goodsId.ifEmpty { it.title } }) { item ->
                                    val ctx = context
                                    SheinItemCard(item = item, width = 160.dp) {
                                        if (item.itemUrl.isNotEmpty())
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.itemUrl)))
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // Vinted results
                if (vintedEnabled && (state.vintedResults.isNotEmpty() || state.vintedLoading)) {
                    item {
                        ResultSectionHeader(
                            platform = "Vinted",
                            count = state.vintedResults.size,
                            loading = state.vintedLoading,
                            badge = "Secondhand",
                            badgeColor = Color(0xFF09B7A0)
                        )
                    }
                    if (state.vintedLoading) {
                        item { LoadingRow(label = "Searching Vinted…") }
                    } else {
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.vintedResults, key = { it.id.ifEmpty { it.title } }) { item ->
                                    val ctx = context
                                    VintedItemCard(item = item, width = 160.dp) {
                                        if (item.itemUrl.isNotEmpty())
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.itemUrl)))
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // AliExpress results
                if (aliExpressEnabled && (state.aliExpressResults.isNotEmpty() || state.aliExpressLoading)) {
                    item {
                        ResultSectionHeader(
                            platform = "AliExpress",
                            count = state.aliExpressResults.size,
                            loading = state.aliExpressLoading,
                            badge = "AliExpress",
                            badgeColor = Color(0xFFE43226)
                        )
                    }
                    if (state.aliExpressLoading) {
                        item { LoadingRow(label = "Searching AliExpress…") }
                    } else {
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.aliExpressResults, key = { it.itemId.ifEmpty { it.title } }) { item ->
                                    val ctx = context
                                    AliExpressItemCard(item = item, width = 160.dp) {
                                        if (item.itemUrl.isNotEmpty())
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.itemUrl)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Common card & helpers ────────────────────────────────────────────────────

@Composable
fun ShopItemCard(
    imageUrl: String,
    title: String,
    price: String,
    badge: String?,
    width: Dp = Dp.Unspecified,
    platformTag: String? = null,
    onClick: () -> Unit
) {
    val mod = if (width != Dp.Unspecified) Modifier.width(width) else Modifier.fillMaxWidth()
    Column(modifier = mod.clip(RoundedCornerShape(12.dp)).background(Paper).clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White)) {
            if (imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            badge?.takeIf { it.isNotEmpty() }?.let {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Ash)
                }
            }
            platformTag?.let {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    .background(Color(0xFFFF9900).copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (price.isNotBlank()) {
                Text(price, style = MaterialTheme.typography.labelLarge, color = Warm, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// Keep old EbayItemCard as alias for backwards compatibility with other screens
@Composable
fun EbayItemCard(item: EbayItem, width: Dp = Dp.Unspecified, onClick: () -> Unit = {}) {
    ShopItemCard(imageUrl = item.imageUrl, title = item.title,
        price = "${item.currency} ${item.price}", badge = item.condition.take(10),
        width = width, onClick = onClick)
}

@Composable
fun TaobaoItemCard(item: TaobaoItem, width: Dp = Dp.Unspecified, onClick: () -> Unit = {}) {
    ShopItemCard(
        imageUrl = item.imageUrl,
        title = item.title,
        price = if (item.price.isNotEmpty()) "¥ ${item.price}" else "",
        badge = null,
        width = width,
        platformTag = "淘宝",
        onClick = onClick
    )
}

@Composable
fun SheinItemCard(item: SheinItem, width: Dp = Dp.Unspecified, onClick: () -> Unit = {}) {
    ShopItemCard(
        imageUrl = item.imageUrl,
        title = item.title,
        price = item.price,
        badge = null,
        width = width,
        platformTag = "SHEIN",
        onClick = onClick
    )
}

@Composable
fun VintedItemCard(item: VintedItem, width: Dp = Dp.Unspecified, onClick: () -> Unit = {}) {
    val priceStr = buildString {
        if (item.price.isNotEmpty()) {
            append(item.currency.ifEmpty { "" })
            if (item.currency.isNotEmpty()) append(" ")
            append(item.price)
        }
    }
    ShopItemCard(
        imageUrl = item.imageUrl,
        title = item.title,
        price = priceStr,
        badge = item.brand.ifEmpty { null },
        width = width,
        platformTag = "Vinted",
        onClick = onClick
    )
}

@Composable
fun AliExpressItemCard(item: AliExpressItem, width: Dp = Dp.Unspecified, onClick: () -> Unit = {}) {
    ShopItemCard(
        imageUrl = item.imageUrl,
        title = item.title,
        price = item.price,
        badge = null,
        width = width,
        platformTag = "AliExpress",
        onClick = onClick
    )
}

@Composable
private fun PlatformChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Ink else Paper)
            .border(1.dp, if (selected) Ink else Mist, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else InkLight)
    }
}

@Composable
private fun ExternalLinkChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Paper)
            .border(1.dp, Mist, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}

@Composable
private fun ResultSectionHeader(platform: String, count: Int, loading: Boolean, badge: String, badgeColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(badgeColor).padding(horizontal = 6.dp, vertical = 3.dp)) {
            Text(badge, style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        Text(platform, style = MaterialTheme.typography.titleSmall, color = Ink)
        if (!loading && count > 0) {
            Text("$count results", style = MaterialTheme.typography.labelSmall, color = Ash)
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Ink)
        }
    }
}

@Composable
private fun LoadingRow(label: String? = null) {
    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        if (label != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Ink)
                Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Ink)
        }
    }
}

@Composable
fun CategoryFilterRow(selected: ClothingCategory?, onSelect: (ClothingCategory?) -> Unit) {
    val cats = listOf(null) + ClothingCategory.entries
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(cats) { cat ->
            val sel = cat == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (sel) Ink else Paper)
                    .border(1.dp, if (sel) Ink else Mist, RoundedCornerShape(20.dp))
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text((cat?.label ?: "All").uppercase(), style = MaterialTheme.typography.labelSmall, color = if (sel) Color.White else InkLight)
            }
        }
    }
}

// ─── eBay Detail Sheet ────────────────────────────────────────────────────────

@Composable
private fun ItemDetailSheet(
    item: EbayItem,
    aiSuggestion: String?,
    aiLoading: Boolean,
    userImagePath: String?,
    tryOnResult: String?,
    tryOnLoading: Boolean,
    tryOnError: String,
    canTryOn: Boolean,
    onPickPhoto: () -> Unit,
    onTryOn: () -> Unit,
    affiliateCampaignId: String = ""
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        AsyncImage(model = item.imageUrl, contentDescription = item.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Mist), contentScale = ContentScale.Fit)

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (item.condition.isNotEmpty()) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFF5F5F5)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(item.condition, style = MaterialTheme.typography.labelSmall, color = Ash)
                }
            }
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (item.price.isNotEmpty()) {
                Text("${item.currency} ${item.price}", style = MaterialTheme.typography.titleMedium, color = Warm, fontWeight = FontWeight.Bold)
            }
        }

        HorizontalDivider(color = Mist, modifier = Modifier.padding(horizontal = 20.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AI Styling Advice", style = MaterialTheme.typography.labelMedium, color = Ash)
            when {
                aiLoading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Ink)
                    Text("Analyzing wardrobe compatibility…", style = MaterialTheme.typography.bodySmall, color = Ash)
                }
                aiSuggestion != null -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Paper).padding(14.dp)) {
                    Text(aiSuggestion, style = MaterialTheme.typography.bodySmall, color = Ink)
                }
            }
        }

        if (canTryOn) {
            HorizontalDivider(color = Mist, modifier = Modifier.padding(horizontal = 20.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Virtual Try-On", style = MaterialTheme.typography.labelMedium, color = Ash)
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Mist, RoundedCornerShape(10.dp)).clickable(onClick = onPickPhoto).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (userImagePath != null) {
                        AsyncImage(model = java.io.File(userImagePath), contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        Column {
                            Text("Photo selected", style = MaterialTheme.typography.labelMedium)
                            Text("Tap to change", style = MaterialTheme.typography.labelSmall, color = Ash)
                        }
                    } else {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Paper), contentAlignment = Alignment.Center) {
                            Text("📷", style = MaterialTheme.typography.titleLarge)
                        }
                        Column {
                            Text("Upload my photo", style = MaterialTheme.typography.labelMedium)
                            Text("Generate virtual try-on", style = MaterialTheme.typography.labelSmall, color = Ash)
                        }
                    }
                }
                if (userImagePath != null && item.imageUrl.isNotEmpty()) {
                    Button(onClick = onTryOn, modifier = Modifier.fillMaxWidth(), enabled = !tryOnLoading,
                        shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                        if (tryOnLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Generating…")
                        } else Text("Start Virtual Try-On")
                    }
                }
                if (tryOnResult != null) {
                    AsyncImage(model = tryOnResult, contentDescription = "Try-on result",
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)).background(Mist),
                        contentScale = ContentScale.Fit)
                }
                if (tryOnError.isNotEmpty()) {
                    Text(tryOnError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (item.itemWebUrl.isNotEmpty()) {
            HorizontalDivider(color = Mist, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    val url = ebayAffiliateUrl(item.itemWebUrl, affiliateCampaignId)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0064D2))
            ) {
                Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Buy on eBay", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
