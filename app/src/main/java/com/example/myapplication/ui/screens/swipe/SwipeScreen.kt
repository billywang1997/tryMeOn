package com.example.myapplication.ui.screens.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.ui.components.FashionImage
import com.example.myapplication.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(
    wardrobeRepository: WardrobeRepository,
    onBack: () -> Unit
) {
    val vm: SwipeViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            SwipeViewModel(wardrobeRepository) as T
    })
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("STYLE BUILDER", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { vm.reset() }) { Icon(Icons.Default.Refresh, null, tint = Ash) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = White)
            )
        },
        containerColor = White
    ) { padding ->
        if (state.done) {
            OutfitSummary(
                selected = state.selected,
                onReset = { vm.reset() },
                modifier = Modifier.padding(padding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Category progress
                Text(
                    "${state.currentCategory.label}  ·  ${state.deck.size} available",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ash,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(Modifier.height(4.dp))

                // Skip category
                TextButton(onClick = { vm.skipCategory() }) {
                    Text("Skip category →", style = MaterialTheme.typography.labelSmall, color = Ash)
                }

                Spacer(Modifier.height(8.dp))

                if (state.deck.isEmpty()) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("No more items in this category", style = MaterialTheme.typography.bodyMedium, color = Ash)
                    }
                } else {
                    // Card stack
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Background cards (next 2)
                        state.deck.getOrNull(2)?.let { item ->
                            ClothingCardBack(item, scale = 0.88f, offsetY = -24.dp)
                        }
                        state.deck.getOrNull(1)?.let { item ->
                            ClothingCardBack(item, scale = 0.94f, offsetY = -12.dp)
                        }
                        // Top card (swipeable)
                        SwipeableCard(
                            item = state.deck.first(),
                            onSwipeLeft = { vm.swipeLeft() },
                            onSwipeRight = { vm.swipeRight() }
                        )
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 60.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FloatingActionButton(
                            onClick = { vm.swipeLeft() },
                            containerColor = Paper,
                            contentColor = Ash,
                            modifier = Modifier.size(56.dp)
                        ) { Icon(Icons.Default.Close, null) }

                        Text(
                            "${state.selected.size} selected",
                            style = MaterialTheme.typography.labelMedium,
                            color = Ash,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )

                        FloatingActionButton(
                            onClick = { vm.swipeRight() },
                            containerColor = Ink,
                            contentColor = White,
                            modifier = Modifier.size(56.dp)
                        ) { Icon(Icons.Default.Check, null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeableCard(
    item: ClothingItem,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .rotate(offsetX.value / 25f)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(item.id) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                offsetX.value > 120 -> {
                                    offsetX.animateTo(900f, tween(200))
                                    onSwipeRight()
                                    offsetX.snapTo(0f)
                                }
                                offsetX.value < -120 -> {
                                    offsetX.animateTo(-900f, tween(200))
                                    onSwipeLeft()
                                    offsetX.snapTo(0f)
                                }
                                else -> offsetX.animateTo(0f, tween(300))
                            }
                        }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + drag.x) }
                    }
                )
            }
    ) {
        FashionImage(
            model = item.imagePath,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Labels on drag
        if (offsetX.value > 30) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC2E7D32))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("Keep", style = MaterialTheme.typography.labelLarge, color = White) }
        }
        if (offsetX.value < -30) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCCB71C1C))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("Skip", style = MaterialTheme.typography.labelLarge, color = White) }
        }
        // Item name bottom
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(12.dp)
        ) {
            Text(
                item.name.ifEmpty { item.category.label },
                style = MaterialTheme.typography.labelLarge,
                color = White
            )
        }
    }
}

@Composable
private fun ClothingCardBack(item: ClothingItem, scale: Float, offsetY: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(scale)
            .aspectRatio(0.72f)
            .offset(y = offsetY)
            .clip(RoundedCornerShape(16.dp))
    ) {
        FashionImage(
            model = item.imagePath,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )
    }
}

@Composable
private fun OutfitSummary(
    selected: List<ClothingItem>,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Today's OOTD", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light)
        Text("${selected.size} pieces", style = MaterialTheme.typography.labelMedium, color = Ash)
        Spacer(Modifier.height(24.dp))

        if (selected.isEmpty()) {
            Text("No items selected", style = MaterialTheme.typography.bodyMedium, color = Ash)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selected) { item ->
                    Column {
                        Box(
                            Modifier
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(10.dp))
                        ) {
                            FashionImage(
                                model = item.imagePath,
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Text(
                            item.name.ifEmpty { item.category.label },
                            style = MaterialTheme.typography.labelSmall,
                            color = Ash,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink)
        ) {
            Text("Start Over", style = MaterialTheme.typography.labelLarge)
        }
    }
}
