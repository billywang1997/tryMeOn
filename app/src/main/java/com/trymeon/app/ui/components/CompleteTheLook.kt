package com.trymeon.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trymeon.app.data.remote.ClaudeApiService
import com.trymeon.app.data.remote.EbayItem
import com.trymeon.app.data.remote.SerpApiService
import com.trymeon.app.data.repository.WishlistRepository
import com.trymeon.app.data.BasicWardrobeProvider
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.model.OutfitLog
import com.trymeon.app.ui.theme.Ash
import com.trymeon.app.ui.theme.Ink
import com.trymeon.app.ui.theme.Mist
import com.trymeon.app.ui.theme.Paper
import com.trymeon.app.ui.theme.Warm
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import com.trymeon.app.util.OpenLink

data class MissingPiece(
    val category: ClothingCategory?,
    val query: String,
    val reason: String,
    val wardrobeMatches: List<ClothingItem> = emptyList(),
    val essentialMatches: List<ClothingItem> = emptyList(),
    val products: List<EbayItem> = emptyList(),
    val loading: Boolean = true,
    val error: String = ""
)

// Loose keyword match of an AI search query against a closet item.
private fun MissingPiece.matches(item: ClothingItem): Boolean {
    val keywords = query.lowercase().split(" ", ",", "-", "_").filter { it.length > 2 }
    val text = "${item.name} ${item.color} ${item.brand} ${item.notes}".lowercase()
    return keywords.any { text.contains(it) }
}

// Append affiliate params to eBay URLs
private fun ebayAffiliate(url: String, campaignId: String): String {
    if (campaignId.isBlank() || url.isBlank()) return url
    val sep = if ('?' in url) "&" else "?"
    return "$url${sep}mkevt=1&mkcid=1&mkrid=705-53470-19255-0&campid=$campaignId&toolid=10001&customid="
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteTheLookSheet(
    anchorItem: ClothingItem,
    wardrobe: List<ClothingItem>,
    gender: String,
    apiKey: String,
    catalog: com.trymeon.app.data.sourcing.ShoppingCatalog,
    claudeService: ClaudeApiService,
    wishlistRepository: WishlistRepository? = null,
    outfitLogs: List<OutfitLog> = emptyList(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pieces by remember { mutableStateOf<List<MissingPiece>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(anchorItem.id) {
        loading = true
        error = ""
        val settings = com.trymeon.app.AppSettings(context)
        val raw = runCatching {
            claudeService.completeTheLook(
                apiKey, anchorItem, wardrobe, gender,
                priceHint = settings.priceExpectation.stylistHint,
                styleKeywords = settings.styleKeywords
            )
        }.getOrElse {
            error = "Couldn't analyze: ${it.message}"
            loading = false
            return@LaunchedEffect
        }
        val parsed = raw.lines()
            .mapNotNull { line ->
                if (!line.startsWith("QUERY|")) return@mapNotNull null
                val parts = line.removePrefix("QUERY|").split("|").map { it.trim() }
                if (parts.size < 2) return@mapNotNull null
                val maybeCat = runCatching { ClothingCategory.valueOf(parts[0].uppercase()) }.getOrNull()
                if (maybeCat != null && parts.size >= 3) {
                    MissingPiece(category = maybeCat, query = parts[1], reason = parts.getOrElse(2) { "" })
                } else {
                    MissingPiece(category = null, query = parts[0], reason = parts.getOrElse(1) { "" })
                }
            }
            .take(3)
        if (parsed.isEmpty()) {
            error = "No suggestions returned"
            loading = false
            return@LaunchedEffect
        }
        // Fill in matches from the user's own wardrobe and basic essentials —
        // no network needed, so these show instantly alongside the products.
        pieces = parsed.map { piece ->
            val cat = piece.category ?: return@map piece
            val ownPool = wardrobe.filter { it.id >= 0 && it.category == cat }
            val basicPool = BasicWardrobeProvider.byGenderAndCategory(gender, cat)
            piece.copy(
                wardrobeMatches = ownPool.filter { piece.matches(it) }.ifEmpty { ownPool }.take(6),
                essentialMatches = basicPool.filter { piece.matches(it) }.ifEmpty { basicPool }.take(6)
            )
        }
        loading = false

        // Fetch products for each piece in parallel
        parsed.forEachIndexed { index, piece ->
            scope.launch {
                val q = com.trymeon.app.util.ensureGenderInQuery(piece.query, gender)
                // Landed prices from one source, so the figure here means the
                // same thing as the figure everywhere else in the app.
                val withAffiliate = catalog.search(q, gender.orEmpty(), limit = 8)
                pieces = pieces.mapIndexed { i, p ->
                    if (i == index) p.copy(products = withAffiliate, loading = false,
                        error = if (withAffiliate.isEmpty()) "No products found" else "")
                    else p
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // Header: anchor item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Paper),
                    contentAlignment = Alignment.Center
                ) {
                    val img = anchorItem.imagePath.takeIf { it.isNotEmpty() } ?: anchorItem.cloudImageUrl
                    if (img.isNotBlank()) {
                        AsyncImage(
                            model = if (img.startsWith("http")) img else File(img),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("COMPLETE THE LOOK", style = MaterialTheme.typography.labelSmall, color = Warm, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        anchorItem.name.ifEmpty { anchorItem.category.label },
                        style = MaterialTheme.typography.titleMedium, color = Ink,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (anchorItem.color.isNotEmpty()) {
                        Text(anchorItem.color, style = MaterialTheme.typography.labelSmall, color = Ash)
                    }
                }
            }

            // Cost per wear (only if priced)
            if (anchorItem.price > 0.0) {
                CostPerWearBadge(
                    item = anchorItem,
                    logs = outfitLogs,
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
            }

            HorizontalDivider(color = Mist)

            when {
                loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Ink)
                    Spacer(Modifier.width(12.dp))
                    Text("Styling your look…", style = MaterialTheme.typography.bodySmall, color = Ash)
                }
                error.isNotEmpty() -> Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash,
                    modifier = Modifier.padding(24.dp)
                )
                else -> pieces.forEachIndexed { idx, piece ->
                    PieceSection(
                        index = idx + 1,
                        piece = piece,
                        wishlistRepository = wishlistRepository,
                        onProductClick = { item ->
                            if (item.itemWebUrl.isNotEmpty()) {
                                OpenLink.open(context, item.itemWebUrl)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PieceSection(
    index: Int,
    piece: MissingPiece,
    wishlistRepository: WishlistRepository?,
    onProductClick: (EbayItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(modifier = Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "0$index",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    piece.query.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Ink,
                    fontWeight = FontWeight.Medium
                )
                if (piece.reason.isNotEmpty()) {
                    Text(piece.reason, style = MaterialTheme.typography.labelSmall, color = Ash)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // From the user's own wardrobe
        if (piece.wardrobeMatches.isNotEmpty()) {
            ClosetRow(label = "FROM YOUR WARDROBE", closetItems = piece.wardrobeMatches)
            Spacer(Modifier.height(12.dp))
        }

        // Basic essentials (AI-curated staples)
        if (piece.essentialMatches.isNotEmpty()) {
            ClosetRow(label = "BASIC ESSENTIALS", closetItems = piece.essentialMatches)
            Spacer(Modifier.height(12.dp))
        }

        // Shop online
        Text(
            "SHOP ONLINE",
            style = MaterialTheme.typography.labelSmall, color = Warm,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(6.dp))
        when {
            piece.loading -> Row(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Ink)
                Spacer(Modifier.width(8.dp))
                Text("Finding pieces…", style = MaterialTheme.typography.labelSmall, color = Ash)
            }
            piece.products.isEmpty() -> Text(
                piece.error.ifEmpty { "No products found" },
                style = MaterialTheme.typography.labelSmall, color = Ash,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(piece.products, key = { it.itemId }) { product ->
                    ProductCard(
                        item = product,
                        query = piece.query,
                        wishlistRepository = wishlistRepository,
                        onClick = { onProductClick(product) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClosetRow(label: String, closetItems: List<ClothingItem>) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall, color = Ash,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(closetItems, key = { it.id }) { item -> ClosetThumb(item) }
        }
    }
}

@Composable
private fun ClosetThumb(item: ClothingItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.width(88.dp)
    ) {
        Box(
            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)).background(Paper)
        ) {
            FashionImage(
                model = item.imagePath.ifEmpty { item.cloudImageUrl },
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Text(
            item.name.ifEmpty { item.category.label },
            style = MaterialTheme.typography.labelSmall, color = Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProductCard(
    item: EbayItem,
    query: String = "",
    wishlistRepository: WishlistRepository? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Paper)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.White)
        ) {
            if (item.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            // Source pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(item.source.take(8), style = MaterialTheme.typography.labelSmall, color = Ash)
            }
            WishlistHeart(
                item = item,
                query = query,
                repository = wishlistRepository,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            )
        }
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                item.title, style = MaterialTheme.typography.labelSmall, color = Ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (item.price.isNotBlank()) {
                Text(
                    item.landedLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Warm, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
