package com.example.myapplication.ui.screens.styleexplorer

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.ui.theme.Ash
import com.example.myapplication.ui.theme.Ink
import com.example.myapplication.ui.theme.Mist
import com.example.myapplication.ui.theme.Paper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleExplorerScreen(
    wardrobeRepository: WardrobeRepository,
    claudeApiService: ClaudeApiService,
    apiKey: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val vm: StyleExplorerViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            StyleExplorerViewModel(wardrobeRepository, claudeApiService, apiKey) as T
    })
    val state by vm.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Ink)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("STYLE EXPLORER", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light)
                Text("Explore your fashion style", style = MaterialTheme.typography.labelSmall, color = Ash)
            }
        }
        HorizontalDivider(color = Mist)

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(ALL_STYLES, key = { it.id }) { style ->
                StyleCard(
                    style = style,
                    isExpanded = state.expandedStyle == style.id,
                    isLoading = style.id in state.loading,
                    result = state.results[style.id],
                    onToggle = { vm.expand(style.id) }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StyleCard(
    style: FashionStyle,
    isExpanded: Boolean,
    isLoading: Boolean,
    result: String?,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (isExpanded) style.colorA.copy(alpha = 0.4f) else Mist, RoundedCornerShape(12.dp))
            .background(if (isExpanded) style.colorA.copy(alpha = 0.04f) else Color.White)
    ) {
        // Card header - always visible
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // Gradient accent bar
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.verticalGradient(listOf(style.colorA, style.colorB)))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(style.emoji, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(6.dp))
                        Text(style.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    }
                    Text(style.tagline, style = MaterialTheme.typography.labelSmall, color = Ash)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Style tags (show 2)
                style.tags.take(2).forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(style.colorA.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(tag, style = MaterialTheme.typography.labelSmall, color = style.colorA)
                    }
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Ash,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Expanded content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                HorizontalDivider(color = Mist)
                Spacer(Modifier.height(12.dp))

                // Style keywords
                Text("Style Traits", style = MaterialTheme.typography.labelMedium, color = Ash)
                Spacer(Modifier.height(4.dp))
                Text(style.keywords, style = MaterialTheme.typography.bodySmall, color = Ink)
                Spacer(Modifier.height(12.dp))

                // All tags
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(style.tags) { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Brush.horizontalGradient(listOf(style.colorA.copy(alpha = 0.15f), style.colorB.copy(alpha = 0.15f))))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(tag, style = MaterialTheme.typography.labelSmall, color = style.colorA)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Mist)
                Spacer(Modifier.height(12.dp))

                // AI analysis result
                Text("Wardrobe Match Analysis", style = MaterialTheme.typography.labelMedium, color = Ash)
                Spacer(Modifier.height(8.dp))

                when {
                    isLoading -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = style.colorA
                            )
                            Text("Analyzing your wardrobe…", style = MaterialTheme.typography.bodySmall, color = Ash)
                        }
                    }
                    result != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Paper)
                                .padding(12.dp)
                        ) {
                            Text(result, style = MaterialTheme.typography.bodySmall, color = Ink, lineHeight = MaterialTheme.typography.bodySmall.lineHeight)
                        }
                    }
                    else -> {
                        Text("Expanding triggers auto-analysis…", style = MaterialTheme.typography.bodySmall, color = Ash)
                    }
                }
            }
        }
    }
}
