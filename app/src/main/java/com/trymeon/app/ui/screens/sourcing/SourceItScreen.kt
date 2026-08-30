package com.trymeon.app.ui.screens.sourcing

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.PhotoCamera
import com.trymeon.app.data.remote.GarmentSighting
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.trymeon.app.data.remote.TaobaoSource
import com.trymeon.app.data.sourcing.AuMarketPrices
import com.trymeon.app.data.sourcing.ClosetGap
import com.trymeon.app.data.sourcing.ClosetGapService
import com.trymeon.app.data.sourcing.FxPolicy
import com.trymeon.app.data.sourcing.PendingTryOn
import com.trymeon.app.data.sourcing.RouteQuote
import com.trymeon.app.data.sourcing.SourcedItem
import com.trymeon.app.data.sourcing.SourcingQuoter
import com.trymeon.app.data.sourcing.SourcingRepository
import com.trymeon.app.data.sourcing.SourcingResult
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.sourcing.DaigouAgent
import com.trymeon.app.domain.sourcing.MarketBenchmark
import com.trymeon.app.domain.sourcing.ShippingRoute
import com.trymeon.app.domain.sourcing.SourcingDefaults
import com.trymeon.app.ui.theme.Ash
import com.trymeon.app.ui.theme.Ink
import com.trymeon.app.ui.theme.Mist
import com.trymeon.app.ui.theme.Paper
import com.trymeon.app.ui.theme.Sage
import com.trymeon.app.ui.theme.Warm
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.trymeon.app.util.OpenLink

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
    /** With [fitLooks], shows how this kind of garment sat on similar bodies. */
    profile: com.trymeon.app.domain.model.UserProfile? = null,
    fitLooks: com.trymeon.app.data.repository.FitLookRepository? = null,
    initialQuery: String = "",
    categoryHint: ClothingCategory? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** Null hides the try-on handoff; sourcing still works on its own. */
    onTryOn: (() -> Unit)? = null,
    /** Null when shown as a bottom-nav tab, which has nowhere to go back to. */
    onBack: (() -> Unit)? = null,
    /**
     * Turns a photo into a search phrase. Null hides the camera entirely —
     * offering it without a way to read the photo would be a dead button.
     */
    identifyPhoto: (suspend (android.net.Uri) -> GarmentSighting?)? = null
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
    var reading by remember { mutableStateOf(false) }
    // What the photo was read as, kept so the card can say what it searched for
    // rather than leaving the user to guess why these results appeared.
    var readAs by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val sharedLooks by (fitLooks?.observeRecent() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsState(emptyList())

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

    /**
     * @param fromPhoto the phrase a photo was read as, or blank for a typed
     *   search. Owned by the search rather than kept beside it: left as its own
     *   state, the caption stayed above the results of the next, unrelated
     *   query the user typed.
     */
    fun run(text: String = query, fromPhoto: String = "") {
        if (text.isBlank() || loading) return
        query = text
        readAs = fromPhoto
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

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val read = identifyPhoto ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            reading = true; error = ""; readAs = ""
            val seen = read(uri)
            reading = false
            if (seen == null) {
                error = "Could not tell what that is — try a clearer photo, or describe it"
                return@launch
            }
            run(seen.query, fromPhoto = seen.query)
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
                    enabled = !loading && !reading,
                    onPhoto = identifyPhoto?.let { { photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    ) } }
                )
            }

            if (result == null && !loading && error.isEmpty()) {
                item { Intro(gaps = gaps, onPick = { run(it) }) }
            }

            if (reading) item { LoadingRow("Reading the photo…") }
            if (loading) item { LoadingRow() }

            // Saying what the photo was read as: these results follow from that
            // reading, and without it a wrong answer looks like a bad search.
            if (readAs.isNotEmpty() && !reading) {
                item {
                    Text(
                        "From your photo: $readAs",
                        style = MaterialTheme.typography.labelMedium,
                        color = Ash,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            if (error.isNotEmpty()) item { ErrorNote(error) }

            result?.let { res ->
                item { TranslationCard(res) }
                if (fitLooks != null) {
                    item {
                        com.trymeon.app.ui.components.FitAlikeStrip(
                            looks = sharedLooks,
                            profile = profile,
                            category = res.query.category,
                            keywords = com.trymeon.app.domain.model.FitLook.keywordsOf(
                                res.query.englishSummary.ifBlank { query }
                            ),
                            title = "ON PEOPLE YOUR SIZE"
                        )
                    }
                }
                // A forwarder fee is a choice only where a forwarder is involved.
                // When every listing is delivered by its seller these chips
                // change nothing, and offering them implies otherwise.
                if (items.any { it.quotes.any { q -> q.line.route != ShippingRoute.PLATFORM_QUOTED } }) {
                    item {
                        Settings(
                            agent = agent, onAgent = { agent = it },
                            cardPercent = cardPercent, onCardPercent = { cardPercent = it }
                        )
                    }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
            }
            Text("SOURCE IT", style = MaterialTheme.typography.titleLarge, color = Ink)
        }
        HorizontalDivider(color = Mist)
    }
}

@Composable
private fun SearchField(
    value: String,
    onValue: (String) -> Unit,
    onSearch: () -> Unit,
    enabled: Boolean,
    onPhoto: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        enabled = enabled,
        placeholder = { Text("Describe it, or use a photo", color = Ash) },
        trailingIcon = {
            Row {
                // A photo is a faster way to say "this kind of thing" than
                // finding the words for it, and it goes down exactly the same
                // path afterwards.
                if (onPhoto != null) {
                    IconButton(onClick = onPhoto, enabled = enabled) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "Find similar from a photo",
                            tint = if (enabled) Ink else Mist
                        )
                    }
                }
                IconButton(onClick = onSearch, enabled = enabled && value.isNotBlank()) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = if (value.isBlank()) Mist else Ink)
                }
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
            // Named no marketplace: which one answers depends on what is
            // configured, and the promise is the same either way.
            "Say it in English.\nWe search in Chinese,\nand price it to your door in dollars.",
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
private fun LoadingRow(text: String = "Searching…") {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Ink)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Ash)
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
                Text(
                    "${best.line.name} · ${best.cost.estimatedDays.first}–${best.cost.estimatedDays.last} days",
                    style = MaterialTheme.typography.labelSmall, color = Ash
                )
                Spacer(Modifier.height(4.dp))
                LocalComparison(item, benchmark)
            }
        }

        Nudge(best)

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(150)) + expandVertically(tween(200)),
            exit = fadeOut(tween(100)) + shrinkVertically(tween(150))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // With one route there is no spread to weigh up, and a
                // one-row "every route" table would imply a choice the buyer
                // does not actually have.
                if (item.quotes.size > 1) RouteLedger(item)
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
                            onClick = { OpenLink.open(context, url) },
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
        // Only show the "from" price where there was a conversion to show. A
        // source that already quotes in the buyer's currency has no yuan price,
        // and inventing one by dividing back through the rate would be a number
        // no seller ever charged.
        if (item.listing.currency != "AUD") {
            Text(
                "¥${"%.0f".format(item.priceCny)}",
                style = MaterialTheme.typography.bodyMedium, color = Ash
            )
            Text("→", style = MaterialTheme.typography.bodyMedium, color = Warm,
                modifier = Modifier.padding(bottom = 2.dp))
        }
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
    if (benchmark == null) return
    val saving = benchmark.savingPercentAgainst(item.bestTotalAud)

    // A sample that spans two markets — fast fashion beside designer — has no
    // "usual price" to be a percentage below. The range still tells the shopper
    // something true, and it is the honest half of what we know.
    if (saving == null) {
        if (!benchmark.isCoherent && item.bestTotalAud < benchmark.lowAud) {
            Text(
                "Similar here run A$${"%.0f".format(benchmark.lowAud)}–" +
                    "${"%.0f".format(benchmark.highAud)} · ${benchmark.sampleSize} listings",
                style = MaterialTheme.typography.labelMedium,
                color = Ash
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF1F5F2))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // The card's primary claim, set in the display face like the price it
        // is arguing against. It read as a caption while the price beside it
        // read as a headline.
        Text(
            "$saving%",
            style = MaterialTheme.typography.headlineSmall,
            color = Sage
        )
        Column {
            Text(
                "below the usual price here",
                style = MaterialTheme.typography.labelMedium,
                color = Sage
            )
            Text(
                // Named as a comparison against similar items, never the same
                // one: matching an exact product across two markets is not
                // possible, and implying it would be the larger claim.
                "similar sell for about A$${"%.0f".format(benchmark.typicalAud)} " +
                    "· ${benchmark.sampleSize} listings",
                style = MaterialTheme.typography.labelSmall,
                color = Ash
            )
        }
    }
}
