package com.example.myapplication.ui.screens.sourcing

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.data.remote.TaobaoSource
import com.example.myapplication.data.sourcing.AuMarketPrices
import com.example.myapplication.data.sourcing.ClosetGap
import com.example.myapplication.data.sourcing.ClosetGapService
import com.example.myapplication.data.sourcing.FxPolicy
import com.example.myapplication.data.sourcing.PendingTryOn
import com.example.myapplication.data.sourcing.RouteQuote
import com.example.myapplication.data.sourcing.SourcedItem
import com.example.myapplication.data.sourcing.SourcingQuoter
import com.example.myapplication.data.sourcing.SourcingRepository
import com.example.myapplication.data.sourcing.SourcingResult
import com.example.myapplication.domain.model.ClothingCategory
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.domain.sourcing.DaigouAgent
import com.example.myapplication.domain.sourcing.MarketBenchmark
import com.example.myapplication.domain.sourcing.SourcingDefaults
import com.example.myapplication.ui.theme.Ash
import com.example.myapplication.ui.theme.Ink
import com.example.myapplication.ui.theme.Mist
import com.example.myapplication.ui.theme.Paper
import com.example.myapplication.ui.theme.Sage
import com.example.myapplication.ui.theme.Warm
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Seeds for a first-time user, chosen to show the translation doing real work. */
private val EXAMPLES = listOf("cropped linen blazer", "wide leg trousers", "chunky leather loafers")

/**
 * "Buy it from the source."
 *
 * One sentence a Taobao listing will never say to an Australian: what this
 * costs at your door. The layout is built around that single transformation —
 * ¥ sticker to A$ landed — because it is the only thing here a shopper cannot
 * get anywhere else.
 */
@Composable
fun SourceItScreen(
    repository: SourcingRepository,
    /** Null hides closet-based suggestions; the search box still works. */
    closetGaps: ClosetGapService? = null,
    /** Null omits the "cheaper than here" comparison. */
    localPrices: AuMarketPrices? = null,
    wardrobe: List<ClothingItem> = emptyList(),
    gender: String = "",
    initialQuery: String = "",
    categoryHint: ClothingCategory? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** Null hides the try-on handoff; sourcing still works on its own. */
    onTryOn: (() -> Unit)? = null,
    /** Null when shown as a bottom-nav tab, which has nowhere to go back to. */
    onBack: (() -> Unit)? = null
) {
    var query by remember { mutableStateOf(initialQuery) }
    var result by remember { mutableStateOf<SourcingResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var agent by remember { mutableStateOf(SourcingDefaults.defaultAgent) }
    var cardPercent by remember { mutableStateOf(SourcingDefaults.DEFAULT_CARD_SETTLEMENT_PERCENT) }
    var gaps by remember { mutableStateOf<List<ClosetGap>>(emptyList()) }
    var benchmark by remember { mutableStateOf<MarketBenchmark?>(null) }

    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    // Quoting is pure arithmetic over listings we already have, so switching
    // agent re-prices instantly instead of re-running a paid search.
    val items = remember(result, agent, cardPercent) {
        result?.let { SourcingQuoter.quote(it, agent, cardSettlementPercent = cardPercent) }.orEmpty()
    }

    // The app already knows what they own, so a blank search box is a wasted question.
    LaunchedEffect(wardrobe.size) {
        if (closetGaps != null && wardrobe.isNotEmpty()) {
            gaps = closetGaps.gaps(wardrobe, gender)
        }
    }

    fun run(text: String = query) {
        if (text.isBlank() || loading) return
        query = text
        keyboard?.hide()
        scope.launch {
            loading = true; error = ""; result = null; expandedId = null; benchmark = null
            repository.source(text, gender = gender, categoryHint = categoryHint)
                .onSuccess { result = it }
                .onFailure { error = it.message ?: "Something went wrong" }
            loading = false
            // After the listings, not before: the comparison is worth waiting
            // for but never worth delaying the prices themselves.
            benchmark = localPrices?.benchmark(text)
        }
    }

    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank()) run(initialQuery) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding()
            )
    ) {
        Header(onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                SearchField(
                    value = query,
                    onValue = { query = it },
                    onSearch = { run() },
                    enabled = !loading
                )
            }

            if (result == null && !loading && error.isEmpty()) {
                item { Intro(gaps = gaps, onPick = { run(it) }) }
            }

            if (loading) item { LoadingRow() }

            if (error.isNotEmpty()) item { ErrorNote(error) }

            result?.let { res ->
                item { TranslationCard(res) }
                item {
                    Settings(
                        agent = agent, onAgent = { agent = it },
                        cardPercent = cardPercent, onCardPercent = { cardPercent = it }
                    )
                }
                items(items, key = { it.listing.itemId + it.listing.title.take(8) }) { item ->
                    val id = item.listing.itemId + item.listing.title.take(8)
                    ListingCard(
                        item = item,
                        benchmark = benchmark,
                        expanded = expandedId == id,
                        onToggle = { expandedId = if (expandedId == id) null else id },
                        onTryOn = onTryOn?.let {
                            {
                                PendingTryOn.offer(
                                    item = item.listing,
                                    category = res.query.category,
                                    priceCny = item.priceCny,
                                    landedAud = item.bestTotalAud
                                )
                                it()
                            }
                        }
                    )
                }
            }
        }
    }
}

// ── Chrome ──────────────────────────────────────────────────────────────────

@Composable
private fun Header(onBack: (() -> Unit)?) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (onBack != null) 4.dp else 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Ink)
                }
            }
            Text("SOURCE IT", style = MaterialTheme.typography.titleLarge, color = Ink)
        }
        HorizontalDivider(color = Mist)
    }
}

@Composable
private fun SearchField(value: String, onValue: (String) -> Unit, onSearch: () -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        enabled = enabled,
        placeholder = { Text("Describe it in English", color = Ash) },
        trailingIcon = {
            IconButton(onClick = onSearch, enabled = enabled && value.isNotBlank()) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = if (value.isBlank()) Mist else Ink)
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ink, unfocusedBorderColor = Mist, cursorColor = Ink
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * First-run state. The examples are not decoration: they are the fastest way to
 * show that the app searches in a language the user cannot type.
 */
@Composable
private fun Intro(gaps: List<ClosetGap>, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(top = 12.dp)) {
        Text(
            "Say it in English.\nWe search Taobao in Chinese,\nand price it to your door.",
            style = MaterialTheme.typography.headlineSmall,
            color = Ink,
            lineHeight = 34.sp
        )

        // Gaps read off the actual wardrobe outrank generic examples: they are
        // about this person, and they need no typing.
        if (gaps.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("YOUR CLOSET IS MISSING")
                gaps.forEach { gap ->
                    Suggestion(title = gap.query, caption = gap.reason) { onPick(gap.query) }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("TRY")
                EXAMPLES.forEach { example ->
                    Suggestion(title = example, caption = "") { onPick(example) }
                }
            }
        }
    }
}

@Composable
private fun Suggestion(title: String, caption: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Mist, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Ink)
            if (caption.isNotBlank()) {
                Text(caption, style = MaterialTheme.typography.labelSmall, color = Ash)
            }
        }
        Text("→", style = MaterialTheme.typography.bodyMedium, color = Warm)
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Ink)
        Spacer(Modifier.width(12.dp))
        Text("Translating, then pricing…", style = MaterialTheme.typography.bodySmall, color = Ash)
    }
}

@Composable
private fun ErrorNote(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)).background(Paper).padding(16.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Warm,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp
    )
}

// ── The translation ─────────────────────────────────────────────────────────

/**
 * The Chinese phrase gets top billing. It is the proof that the app did the one
 * thing the user could not, it is the most distinctive thing on the screen, and
 * it is reusable — they can paste it into Taobao themselves.
 */
@Composable
private fun TranslationCard(result: SourcingResult) {
    val now = remember(result) { System.currentTimeMillis() }
    val official = result.listings.firstOrNull()?.listing?.source == TaobaoSource.AFFILIATE

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("WE SEARCHED")
        Text(
            result.usedQuery,
            style = MaterialTheme.typography.headlineSmall,
            color = Ink
        )
        if (result.query.englishSummary.isNotBlank()) {
            Text(result.query.englishSummary, style = MaterialTheme.typography.bodySmall, color = Ash)
        }

        if (result.query.buyerNote.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)).background(Paper).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.width(2.dp).height(32.dp).background(Warm))
                Text(result.query.buyerNote, style = MaterialTheme.typography.bodySmall, color = Ink)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                FxPolicy.label(result.fxRate, now),
                style = MaterialTheme.typography.labelSmall,
                color = if (FxPolicy.isStale(result.fxRate, now)) Color(0xFFB07A2B) else Ash
            )
            Text("·", style = MaterialTheme.typography.labelSmall, color = Mist)
            // Honest about provenance: affiliate data is official, the scraper is not.
            Text(
                if (official) "official listings" else "unofficial listings",
                style = MaterialTheme.typography.labelSmall,
                color = if (official) Ash else Color(0xFFB07A2B)
            )
        }
        HorizontalDivider(color = Mist)
    }
}

/**
 * The two costs that depend on the buyer rather than the item.
 *
 * The card row exists because every other figure converts at the ECB rate,
 * which nobody is actually offered — Alipay adds a service fee and a spread,
 * and most Australian cards add 2-3% on top. Quoting mid-market alone
 * understates the bill.
 */
@Composable
private fun Settings(
    agent: DaigouAgent,
    onAgent: (DaigouAgent) -> Unit,
    cardPercent: Double,
    onCardPercent: (Double) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("FORWARDER FEE, IF YOU USE ONE")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourcingDefaults.agents.filter { it.id != "none" }.forEach { option ->
                    Chip("${option.serviceFeePercent.toInt()}%", option.id == agent.id) { onAgent(option) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("CARD FX & FEES")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourcingDefaults.cardSettlementOptions.forEach { pct ->
                    Chip(if (pct == 0.0) "None" else "${trim(pct)}%", pct == cardPercent) { onCardPercent(pct) }
                }
            }
            Text(
                when (cardPercent) {
                    0.0 -> "A card with no foreign transaction fee."
                    2.5 -> "Alipay's overseas-card fee."
                    5.0 -> "Card paid direct on Taobao — issuer fee plus spread."
                    else -> "Typical Australian card."
                },
                style = MaterialTheme.typography.labelSmall, color = Ash
            )
        }
    }
}

@Composable
private fun Chip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Ink else Color.White)
            .border(1.dp, if (active) Ink else Mist, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) Color.White else Ash
        )
    }
}

private fun trim(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

// ── Listings ────────────────────────────────────────────────────────────────

@Composable
internal fun ListingCard(
    item: SourcedItem,
    benchmark: MarketBenchmark? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTryOn: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val best = item.best

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (expanded) Ink else Mist, RoundedCornerShape(14.dp))
            .clickable { onToggle() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AsyncImage(
                model = item.listing.imageUrl,
                contentDescription = item.listing.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(96.dp).aspectRatio(0.75f)
                    .clip(RoundedCornerShape(10.dp)).background(Paper)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.listing.title,
                    style = MaterialTheme.typography.bodySmall, color = Ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    item.listing.shop.takeIf { it.isNotBlank() },
                    item.listing.sold.takeIf { it > 0 }?.let { "${compact(it)} sold" }
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = Ash, maxLines = 1)
                }
                Spacer(Modifier.height(2.dp))
                PriceReveal(item)
                LocalComparison(item, benchmark)
                Text(
                    "${best.line.name} · ${best.cost.estimatedDays.first}–${best.cost.estimatedDays.last} days",
                    style = MaterialTheme.typography.labelSmall, color = Ash
                )
            }
        }

        Nudge(best)

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(150)) + expandVertically(tween(200)),
            exit = fadeOut(tween(100)) + shrinkVertically(tween(150))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RouteLedger(item)
                Breakdown(best)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onTryOn != null) {
                        OutlinedButton(
                            onClick = onTryOn,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Ink),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
                        ) {
                            Text("Try it on", style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
                        }
                    }
                    item.orderUrl?.let { url ->
                        Button(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Ink)
                        ) {
                            Text("Order this", style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The whole product in three glyphs: what it is listed at, an arrow, what it
 * actually costs. The multiplier is the line people repeat to other people.
 */
@Composable
private fun PriceReveal(item: SourcedItem) {
    val multiplier = item.best.cost.multiplier
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "¥${"%.0f".format(item.priceCny)}",
            style = MaterialTheme.typography.bodyMedium, color = Ash
        )
        Text("→", style = MaterialTheme.typography.bodyMedium, color = Warm,
            modifier = Modifier.padding(bottom = 2.dp))
        Text(
            "A$${"%.2f".format(item.bestTotalAud)}",
            style = MaterialTheme.typography.headlineSmall, color = Ink
        )
        if (multiplier > 1.05) {
            Text(
                "${"%.1f".format(multiplier)}×",
                style = MaterialTheme.typography.labelSmall, color = Ash,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

/** The one line of advice that can actually change what the user pays. */
@Composable
private fun Nudge(quote: RouteQuote) {
    val escape = quote.volumetricEscape
    val text = when {
        quote.addToFreeShippingCny != null ->
            "Add ¥${quote.addToFreeShippingCny.roundToInt()} to this order and shipping is free"
        // Counterintuitive and real: just over the 3:1 ratio, a few more grams
        // moves the parcel off volumetric billing and cuts the freight.
        escape != null ->
            "${escape.extraGrams} g heavier and freight drops A$${"%.2f".format(escape.savingAud)}"
        else -> return
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFBF7F0))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(Warm))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color(0xFF7A6647))
    }
}

/** Every route as a ledger — the comparison a listing page will never show. */
@Composable
internal fun RouteLedger(item: SourcedItem) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label("EVERY ROUTE")
        Spacer(Modifier.height(6.dp))
        item.quotes.forEachIndexed { i, quote ->
            val lead = i == 0
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        quote.line.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lead) Ink else Ash,
                        fontWeight = if (lead) FontWeight.Medium else FontWeight.Normal
                    )
                    Text(
                        "${quote.cost.estimatedDays.first}–${quote.cost.estimatedDays.last} days · billed ${grams(quote.cost.chargeableGrams)}",
                        style = MaterialTheme.typography.labelSmall, color = Ash
                    )
                }
                Text(
                    "A$${"%.2f".format(quote.cost.totalAud)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (lead) Ink else Ash,
                    fontWeight = if (lead) FontWeight.Medium else FontWeight.Normal
                )
            }
            if (i < item.quotes.lastIndex) HorizontalDivider(color = Mist)
        }
        if (item.spreadAud > 0.5) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Choosing the wrong one costs A$${"%.2f".format(item.spreadAud)}.",
                style = MaterialTheme.typography.bodySmall, color = Ink
            )
        }
    }
}

@Composable
private fun Breakdown(quote: RouteQuote) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)).background(Paper).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Label(quote.line.name.uppercase())
        Spacer(Modifier.height(2.dp))
        quote.cost.lines.forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(line.label, style = MaterialTheme.typography.labelSmall, color = Ash)
                Text("A$${"%.2f".format(line.amountAud)}", style = MaterialTheme.typography.labelSmall, color = Ink)
            }
        }
        Spacer(Modifier.height(2.dp))
        HorizontalDivider(color = Mist)
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Landed at your door", style = MaterialTheme.typography.bodySmall, color = Ink)
            Text(
                "A$${"%.2f".format(quote.cost.totalAud)}",
                style = MaterialTheme.typography.bodyMedium, color = Ink, fontWeight = FontWeight.Medium
            )
        }
        if (quote.cost.billedOnVolume) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Billed on ${grams(quote.cost.chargeableGrams)} of volume, not the ${grams(quote.cost.actualGrams)} it weighs.",
                style = MaterialTheme.typography.labelSmall, color = Ash
            )
        }
    }
}

private fun grams(g: Int) = if (g >= 1000) "${"%.1f".format(g / 1000.0)} kg" else "$g g"

private fun compact(n: Int) = when {
    n >= 10_000 -> "${"%.1f".format(n / 10_000.0)}w"
    n >= 1_000 -> "${"%.1f".format(n / 1_000.0)}k"
    else -> "$n"
}

/**
 * How far below the local market this lands.
 *
 * A landed price is a number; a landed price against what the same kind of
 * thing costs here is an argument, and it is the argument the whole feature
 * exists to make. Shown only when the item is genuinely cheaper and the
 * comparison rests on enough listings to be a market rather than an anecdote —
 * an invented saving would cost more trust than a missing one.
 *
 * The wording says "similar", because matching an exact product across two
 * markets is not possible and claiming otherwise would be the larger lie.
 */
@Composable
private fun LocalComparison(item: SourcedItem, benchmark: MarketBenchmark?) {
    val saving = benchmark?.savingPercentAgainst(item.bestTotalAud) ?: return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.padding(top = 3.dp)
    ) {
        // A hairline rather than a badge: the number is the point, and a loud
        // chip here would read like a discount sticker rather than a finding.
        Box(Modifier.width(2.dp).height(13.dp).background(Sage))
        Text(
            "$saving% under",
            style = MaterialTheme.typography.labelMedium,
            color = Sage,
            fontWeight = FontWeight.Medium
        )
        Text(
            "similar here · A$${"%.0f".format(benchmark.typicalAud)}",
            style = MaterialTheme.typography.labelSmall,
            color = Ash
        )
    }
}
