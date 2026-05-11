package com.example.myapplication.ui.screens.addclothing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.domain.model.ClothingCategory
import com.example.myapplication.ui.theme.Ash
import com.example.myapplication.ui.theme.Ink
import com.example.myapplication.ui.theme.InkLight
import kotlinx.coroutines.launch

@Composable
fun BatchAddFlow(
    viewModel: BatchAddViewModel,
    onDone: (savedCount: Int) -> Unit,
    onSkip: (() -> Unit)? = null,
    stepIndicator: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by viewModel.items.collectAsState()
    val savingAll by viewModel.savingAll.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) viewModel.addUris(context, uris)
    }

    val pagerState = rememberPagerState { items.size.coerceAtLeast(1) }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty() && pagerState.currentPage >= items.size) {
            pagerState.animateScrollToPage((items.size - 1).coerceAtLeast(0))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 160.dp)
        ) {
            Spacer(Modifier.height(72.dp))
            Column(modifier = Modifier.padding(horizontal = 28.dp)) {
                Text("Add your", style = MaterialTheme.typography.titleLarge, color = Ash, fontWeight = FontWeight.Light)
                Text("wardrobe.", style = MaterialTheme.typography.displaySmall, color = Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (items.isEmpty()) "Select multiple photos at once"
                    else "← Swipe to review each piece →",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash
                )
            }
            Spacer(Modifier.height(20.dp))

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .aspectRatio(0.8f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF7F7F7))
                        .border(1.5.dp, Color(0xFFE0E0E0), RoundedCornerShape(20.dp))
                        .clickable { picker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AddAPhoto, null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(32.dp))
                        Text("Tap to select photos", style = MaterialTheme.typography.labelMedium, color = Color(0xFFBBBBBB))
                        Text("Select multiple at once", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCCCCCC))
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 40.dp),
                        pageSpacing = 12.dp
                    ) { page ->
                        val item = items.getOrNull(page) ?: return@HorizontalPager
                        ItemCard(
                            item = item,
                            onDelete = { viewModel.removeItem(item.id) },
                            onCategorySelect = { viewModel.setCategory(item.id, it) },
                            onBgSelect = { viewModel.setBg(item.id, it) }
                        )
                    }

                    if (pagerState.currentPage > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 4.dp)
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.92f), CircleShape)
                                .border(1.dp, Color(0xFFEEEEEE), CircleShape)
                                .clickable { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ChevronLeft, null, tint = Ink, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (pagerState.currentPage < items.size - 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp)
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.92f), CircleShape)
                                .border(1.dp, Color(0xFFEEEEEE), CircleShape)
                                .clickable { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ChevronRight, null, tint = Ink, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${items.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ash
                    )
                    Spacer(Modifier.width(8.dp))
                    val maxDots = items.size.coerceAtMost(8)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(maxDots) { i ->
                            val active = i == pagerState.currentPage.coerceAtMost(maxDots - 1)
                            Box(
                                modifier = Modifier
                                    .size(if (active) 6.dp else 4.dp)
                                    .clip(CircleShape)
                                    .background(if (active) Ink else Color(0xFFDDDDDD))
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            stepIndicator?.invoke()
            if (stepIndicator != null) Spacer(Modifier.height(18.dp))

            if (items.isNotEmpty()) {
                val readyCount = items.count { !it.processingBg && it.foreground != null }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { picker.launch("image/*") },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add more", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val saved = viewModel.saveAll(context)
                                onDone(saved)
                            }
                        },
                        enabled = readyCount > 0 && !savingAll,
                        modifier = Modifier.weight(2f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink)
                    ) {
                        if (savingAll) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text(
                                "Save $readyCount ${if (readyCount == 1) "item" else "items"}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }
                if (onSkip != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onSkip) {
                        Text("Skip for now →", style = MaterialTheme.typography.labelMedium, color = Ash)
                    }
                }
            } else {
                Button(
                    onClick = { picker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) {
                    Text("SELECT PHOTOS", style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp, color = Color.White)
                }
                if (onSkip != null) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onSkip) {
                        Text("Skip for now →", style = MaterialTheme.typography.labelMedium, color = Ash)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(
    item: PendingItem,
    onDelete: () -> Unit,
    onCategorySelect: (ClothingCategory) -> Unit,
    onBgSelect: (Int) -> Unit
) {
    val bgColor = Color(onboardingBgOptions[item.selectedBgIndex.coerceIn(0, onboardingBgOptions.lastIndex)].second)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (item.foreground != null) bgColor else Color(0xFFF7F7F7))
                .then(
                    if (item.foreground == null) Modifier.border(1.5.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                item.processingBg -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Ink)
                    Text("Removing background…", style = MaterialTheme.typography.labelSmall, color = Ash)
                }

                item.foreground != null -> Image(
                    bitmap = item.foreground.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit
                )

                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AddAPhoto, null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(24.dp))
                    Text("Processing…", style = MaterialTheme.typography.labelSmall, color = Color(0xFFBBBBBB))
                }
            }

            // AI badge (top-left)
            if (!item.processingBg) {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    if (item.detectingAi) {
                        Row(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = Color.White)
                            Text("Detecting…", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    } else if (item.aiCategory != null) {
                        Row(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(10.dp))
                            Text(item.aiCategory.label, style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }
            }

            // Delete button (top-right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        Text("Background", style = MaterialTheme.typography.labelSmall, color = Ash)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onboardingBgOptions.forEachIndexed { index, (_, _, color) ->
                val isSel = index == item.selectedBgIndex
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(
                            width = if (isSel) 2.dp else 1.dp,
                            color = if (isSel) Ink else Color(0xFFDDDDDD),
                            shape = CircleShape
                        )
                        .clickable { onBgSelect(index) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text("Category", style = MaterialTheme.typography.labelSmall, color = Ash)
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            ClothingCategory.entries.forEach { cat ->
                val isSel = cat == item.selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSel) Ink else Color(0xFFF5F5F5))
                        .clickable { onCategorySelect(cat) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        cat.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSel) Color.White else InkLight
                    )
                }
            }
        }
    }
}
