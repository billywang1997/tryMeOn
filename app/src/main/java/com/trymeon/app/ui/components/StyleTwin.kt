package com.trymeon.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.ui.theme.Ash
import com.trymeon.app.ui.theme.Ink
import com.trymeon.app.ui.theme.Mist
import com.trymeon.app.ui.theme.Paper
import com.trymeon.app.ui.theme.Warm
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import com.trymeon.app.util.OpenLink

data class StyleTwinResult(
    val twins: List<TwinMatch>,
    val shopQueries: List<String>
)

data class TwinMatch(
    val name: String,
    val percentage: Int,
    val reason: String
)

private fun ebayAffiliate(url: String, campaignId: String): String {
    if (campaignId.isBlank() || url.isBlank()) return url
    val sep = if ('?' in url) "&" else "?"
    return "$url${sep}mkevt=1&mkcid=1&mkrid=705-53470-19255-0&campid=$campaignId&toolid=10001&customid="
}

fun parseStyleTwin(raw: String): StyleTwinResult {
    val twins = mutableListOf<TwinMatch>()
    var shopQueries: List<String> = emptyList()
    for (line in raw.lines()) {
        when {
            line.startsWith("TWIN|") -> {
                val parts = line.removePrefix("TWIN|").split("|")
                if (parts.size >= 3) {
                    twins.add(
                        TwinMatch(
                            name = parts[0].trim(),
                            percentage = parts[1].trim().filter { it.isDigit() }.toIntOrNull() ?: 0,
                            reason = parts.getOrNull(2)?.trim().orEmpty()
                        )
                    )
                }
            }
            line.startsWith("SHOP|") -> {
                shopQueries = line.removePrefix("SHOP|").split("|").map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
    }
    return StyleTwinResult(twins.take(3), shopQueries.take(3))
}

@Composable
fun StyleTwinCard(
    wardrobe: List<ClothingItem>,
    styleKeywords: Set<String>,
    gender: String,
    apiKey: String,
    catalog: com.trymeon.app.data.sourcing.ShoppingCatalog,
    claudeService: ClaudeApiService,
    wishlistRepository: WishlistRepository? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<StyleTwinResult?>(null) }
    var products by remember { mutableStateOf<List<EbayItem>>(emptyList()) }
    var productsLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(wardrobe.size, gender) {
        if (wardrobe.size < 3 || apiKey.isBlank()) return@LaunchedEffect
        loading = true
        error = ""
        try {
            val raw = claudeService.styleTwin(apiKey, wardrobe, styleKeywords, gender)
            val parsed = parseStyleTwin(raw)
            if (parsed.twins.isEmpty()) {
                error = "Couldn't pin your style — try logging more outfits"
            } else {
                result = parsed
                if (parsed.shopQueries.isNotEmpty()) {
                    productsLoading = true
                    val items = parsed.shopQueries.map { rawQ ->
                        val q = com.trymeon.app.util.ensureGenderInQuery(rawQ, gender)
                        scope.async {
                            catalog.search(q, gender.orEmpty(), limit = 4)
                        }
                    }.awaitAll().flatten()
                    products = items
                    productsLoading = false
                }
            }
        } catch (e: Exception) {
            error = "Analysis failed: ${e.message}"
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFAF9F6))
            .padding(vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🪞", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("STYLE TWIN", style = MaterialTheme.typography.labelSmall, color = Warm, fontWeight = FontWeight.SemiBold)
                Text("Who you dress like", style = MaterialTheme.typography.titleSmall, color = Ink, fontWeight = FontWeight.Medium)
            }
            result?.twins?.firstOrNull()?.let { top ->
                androidx.compose.material3.TextButton(onClick = {
                    val bmp = com.trymeon.app.share.ShareCardRenderer.render(
                        com.trymeon.app.share.ShareCardRenderer.CardSpec.StyleTwin(
                            celebrity = top.name, percent = top.percentage, reason = top.reason
                        )
                    )
                    com.trymeon.app.share.Sharer.share(context, bmp, "My style twin is ${top.name}")
                }) {
                    Text("📤 SHARE", style = MaterialTheme.typography.labelSmall, color = Ink, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        when {
            loading -> Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Ink)
                Spacer(Modifier.width(10.dp))
                Text("Reading your style…", style = MaterialTheme.typography.bodySmall, color = Ash)
            }
            error.isNotEmpty() -> Text(error, style = MaterialTheme.typography.bodySmall, color = Ash, modifier = Modifier.padding(horizontal = 18.dp))
            result != null -> {
                // Twin bars
                Column(modifier = Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    result!!.twins.forEachIndexed { i, twin ->
                        TwinRow(twin = twin, isTop = i == 0)
                    }
                }
                if (products.isNotEmpty() || productsLoading) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Mist, modifier = Modifier.padding(horizontal = 18.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "SHOP THEIR LOOK",
                        style = MaterialTheme.typography.labelSmall,
                        color = Warm, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    if (productsLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Ink)
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(products, key = { it.itemId }) { item ->
                                MiniProductCard(
                                    item = item,
                                    wishlistRepository = wishlistRepository,
                                    onClick = {
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
            else -> Text(
                "Add ${(3 - wardrobe.size).coerceAtLeast(0)} more items to unlock",
                style = MaterialTheme.typography.bodySmall, color = Ash,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun TwinRow(twin: TwinMatch, isTop: Boolean) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                twin.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                fontWeight = if (isTop) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${twin.percentage}%",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isTop) Ink else Ash,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        // Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Mist)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(twin.percentage.coerceIn(0, 100) / 100f)
                    .background(if (isTop) Ink else Ash)
            )
        }
        if (twin.reason.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(twin.reason, style = MaterialTheme.typography.labelSmall, color = Ash, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MiniProductCard(
    item: EbayItem,
    wishlistRepository: WishlistRepository?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(108.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(1.dp, Mist, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Paper),
            contentAlignment = Alignment.Center
        ) {
            if (item.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            WishlistHeart(
                item = item,
                repository = wishlistRepository,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
            )
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.labelSmall,
                color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (item.price.isNotBlank()) {
                Text(
                    item.landedLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Warm, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
