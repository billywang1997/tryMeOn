package com.example.myapplication.ui.screens.audit

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.activity.ComponentActivity
import com.example.myapplication.AppSettings
import com.example.myapplication.data.billing.BillingManager
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.remote.EbayApiService
import com.example.myapplication.data.remote.EbayItem
import com.example.myapplication.data.remote.SerpApiService
import com.example.myapplication.data.repository.UserProfileRepository
import com.example.myapplication.data.repository.WishlistRepository
import com.example.myapplication.ui.components.WishlistHeart
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.domain.model.UserProfile
import com.example.myapplication.ui.theme.Ash
import com.example.myapplication.ui.theme.Ink
import com.example.myapplication.ui.theme.Mist
import com.example.myapplication.ui.theme.Paper
import com.example.myapplication.ui.theme.Warm
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.myapplication.util.OpenLink

data class AuditReport(
    val score: Int,
    val palette: List<String>,
    val strength: String,
    val gaps: List<String>,
    val recommendations: List<AuditRec>
)

data class AuditRec(
    val query: String,
    val reason: String,
    /** Written by the audit itself, so the strip does not pay to translate it again. */
    val chineseQuery: String = "",
    val products: List<EbayItem> = emptyList(),
    val loading: Boolean = true
)

private fun ebayAffiliate(url: String, campaignId: String): String {
    if (campaignId.isBlank() || url.isBlank()) return url
    val sep = if ('?' in url) "&" else "?"
    return "$url${sep}mkevt=1&mkcid=1&mkrid=705-53470-19255-0&campid=$campaignId&toolid=10001&customid="
}

fun parseAuditReport(raw: String): AuditReport? {
    var score = 0
    var palette: List<String> = emptyList()
    var strength = ""
    var gaps: List<String> = emptyList()
    val recs = mutableListOf<AuditRec>()
    for (line in raw.lines()) {
        when {
            line.startsWith("SCORE|") -> score = line.removePrefix("SCORE|").trim().filter { it.isDigit() }.toIntOrNull() ?: 0
            line.startsWith("PALETTE|") -> palette = line.removePrefix("PALETTE|").split(",").map { it.trim() }.filter { it.isNotEmpty() }
            line.startsWith("STRENGTH|") -> strength = line.removePrefix("STRENGTH|").trim()
            line.startsWith("GAPS|") -> gaps = line.removePrefix("GAPS|").split(";").map { it.trim() }.filter { it.isNotEmpty() }
            line.startsWith("BUY|") -> {
                val parts = line.removePrefix("BUY|").split("|")
                if (parts.size >= 2) recs.add(
                    AuditRec(
                        query = parts[0].trim(),
                        reason = parts[1].trim(),
                        chineseQuery = parts.getOrNull(2)?.trim().orEmpty()
                    )
                )
            }
        }
    }
    if (recs.isEmpty() && score == 0) return null
    return AuditReport(score, palette, strength, gaps, recs.take(6))
}

@Composable
fun ClosetAuditScreen(
    wardrobeRepository: WardrobeRepository,
    profileRepository: UserProfileRepository,
    catalog: com.example.myapplication.data.sourcing.ShoppingCatalog,
    claudeService: ClaudeApiService,
    apiKey: String,
    ebayClientId: String,
    ebayClientSecret: String,
    serpApiKey: String,
    ebayAffiliateCampaignId: String,
    styleKeywords: Set<String>,
    wishlistRepository: WishlistRepository? = null,
    onSourceIt: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val ebayService = remember { EbayApiService() }
    val serpService = remember { SerpApiService() }
    val scope = rememberCoroutineScope()

    val billing = remember { BillingManager(context.applicationContext) }
    DisposableEffect(Unit) {
        billing.start()
        onDispose { billing.stop() }
    }
    val billingUnlocked by billing.unlocked.collectAsState()
    val playPrice by billing.productDetails.collectAsState()

    var unlocked by remember { mutableStateOf(settings.auditUnlocked) }
    LaunchedEffect(billingUnlocked) { if (billingUnlocked) unlocked = true }
    var report by remember { mutableStateOf<AuditReport?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var clothing by remember { mutableStateOf<List<ClothingItem>>(emptyList()) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var clothingLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        clothing = wardrobeRepository.getAllClothing().first()
        profile = profileRepository.getProfile().first()
        clothingLoaded = true
    }

    LaunchedEffect(unlocked, clothing.size) {
        if (!unlocked || clothing.isEmpty() || apiKey.isBlank() || report != null) return@LaunchedEffect
        loading = true; error = ""
        try {
            val raw = claudeService.closetAudit(apiKey, clothing, profile, styleKeywords)
            val parsed = parseAuditReport(raw)
            if (parsed == null) {
                error = "Couldn't generate report"
            } else {
                report = parsed
                // Fetch products for each rec
                parsed.recommendations.forEachIndexed { idx, rec ->
                    scope.launch {
                        val q = com.example.myapplication.util.ensureGenderInQuery(rec.query, profile?.gender)
                        val results = catalog.search(
                            englishQuery = q,
                            gender = profile?.gender.orEmpty(),
                            limit = 6,
                            chineseQuery = rec.chineseQuery
                        )
                        report = report?.copy(
                            recommendations = report!!.recommendations.mapIndexed { i, r ->
                                if (i == idx) r.copy(products = results, loading = false) else r
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            error = "Failed: ${e.message}"
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = Ink)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("AI CLOSET AUDIT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light, color = Ink)
                Text("Personal stylist report", style = MaterialTheme.typography.labelSmall, color = Ash)
            }
            report?.let { r ->
                IconButton(onClick = {
                    val bmp = com.example.myapplication.share.ShareCardRenderer.render(
                        com.example.myapplication.share.ShareCardRenderer.CardSpec.AuditScore(
                            score = r.score,
                            palette = r.palette,
                            verdict = r.strength
                        )
                    )
                    com.example.myapplication.share.Sharer.share(context, bmp, "My closet scored ${r.score}/100 ✨")
                }) {
                    Icon(Icons.Default.Share, "Share", tint = Ink)
                }
            }
        }
        HorizontalDivider(color = Mist)

        if (!unlocked) {
            Paywall(
                itemCount = clothing.size,
                price = playPrice?.oneTimePurchaseOfferDetails?.formattedPrice ?: "\$2.99",
                onUnlock = {
                    if (playPrice != null) {
                        val activity = context as? ComponentActivity
                        if (activity != null) billing.launchPurchase(activity)
                    } else {
                        // Fallback: dev / Play unavailable — manual unlock
                        settings.auditUnlocked = true
                        unlocked = true
                    }
                }
            )
        } else {
            when {
                clothingLoaded && clothing.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("👗", style = MaterialTheme.typography.displaySmall)
                        Text("Add items to your closet first", style = MaterialTheme.typography.bodyMedium, color = Ink)
                        Text("The audit needs a wardrobe to analyze.", style = MaterialTheme.typography.labelSmall, color = Ash)
                    }
                }
                loading || (report == null && error.isEmpty()) -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator(color = Ink, strokeWidth = 1.5.dp)
                        Text("Your stylist is reading your closet…", style = MaterialTheme.typography.labelMedium, color = Ash)
                    }
                }
                error.isNotEmpty() -> Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = Ash)
                }
                report != null -> ReportBody(
                    report = report!!,
                    wishlistRepository = wishlistRepository,
                    onSourceIt = onSourceIt,
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

@Composable
private fun Paywall(itemCount: Int, price: String, onUnlock: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFFFAF9F6))
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Bolt, null, tint = Warm, modifier = Modifier.size(32.dp))
        }
        Text(
            "Premium Closet Audit",
            style = MaterialTheme.typography.headlineSmall,
            color = Ink,
            fontWeight = FontWeight.Light,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            "Deep AI analysis of your $itemCount-piece wardrobe — gaps, palette, and 6 personalized buys.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ash,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(4.dp))
        // Feature bullets
        listOf(
            "📊  Wardrobe versatility score",
            "🎨  Your dominant color palette",
            "💎  3 critical wardrobe gaps",
            "🛍  6 high-impact buys with shop links",
            "✨  Generated fresh each time"
        ).forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onUnlock,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink)
        ) {
            Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp), tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Unlock for $price", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "One-time purchase · Reusable forever",
            style = MaterialTheme.typography.labelSmall,
            color = Ash,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun ReportBody(
    report: AuditReport,
    wishlistRepository: WishlistRepository?,
    onSourceIt: (String) -> Unit,
    onProductClick: (EbayItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)
    ) {
        // Score
        Column(modifier = Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("VERSATILITY", style = MaterialTheme.typography.labelSmall, color = Warm, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    report.score.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = Ink, fontWeight = FontWeight.Light
                )
                Text(
                    "/ 100",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ash, modifier = Modifier.padding(bottom = 12.dp, start = 6.dp)
                )
            }
        }

        // Palette
        if (report.palette.isNotEmpty()) {
            SectionLabel("PALETTE")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(report.palette) { color ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Paper)
                            .border(1.dp, Mist, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(color, style = MaterialTheme.typography.labelMedium, color = Ink)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // Strength
        if (report.strength.isNotEmpty()) {
            SectionLabel("YOUR STRENGTH")
            Text(
                report.strength,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(20.dp))
        }

        // Gaps
        if (report.gaps.isNotEmpty()) {
            SectionLabel("WARDROBE GAPS")
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                report.gaps.forEachIndexed { i, gap ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "0${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Warm, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 12.dp, top = 2.dp)
                        )
                        Text(gap, style = MaterialTheme.typography.bodyMedium, color = Ink)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // Recommendations
        SectionLabel("RECOMMENDED BUYS")
        report.recommendations.forEachIndexed { idx, rec ->
            Column(modifier = Modifier.padding(top = if (idx == 0) 4.dp else 18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rec.query.uppercase(), style = MaterialTheme.typography.labelMedium, color = Ink, fontWeight = FontWeight.Medium)
                        Text(rec.reason, style = MaterialTheme.typography.labelSmall, color = Ash)
                    }
                    // A gap the user has just accepted is the strongest possible
                    // seed for a sourcing search — no typing, no translation.
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ink)
                            .clickable { onSourceIt(rec.query) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Source it",
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    rec.loading -> Row(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Ink)
                    }
                    rec.products.isEmpty() -> Text(
                        "No matches found",
                        style = MaterialTheme.typography.labelSmall, color = Ash,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(rec.products, key = { it.itemId }) { p ->
                            AuditProductCard(
                                item = p,
                                query = rec.query,
                                wishlistRepository = wishlistRepository,
                                onClick = { onProductClick(p) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Warm,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun AuditProductCard(
    item: EbayItem,
    query: String = "",
    wishlistRepository: WishlistRepository? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Paper)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White)) {
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
                query = query,
                repository = wishlistRepository,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            )
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(item.title, style = MaterialTheme.typography.labelSmall, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.price.isNotBlank()) {
                Text("${item.currency} ${item.price}", style = MaterialTheme.typography.labelSmall, color = Warm, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
