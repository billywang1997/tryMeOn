package com.example.myapplication.ui.screens.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.BasicWardrobeProvider
import com.example.myapplication.domain.model.ClothingCategory
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.ui.theme.*

private val RECOMMENDED_MIN = mapOf(
    ClothingCategory.INNER     to 3,
    ClothingCategory.OUTERWEAR to 2,
    ClothingCategory.PANTS     to 2,
    ClothingCategory.DRESS     to 1,
    ClothingCategory.SHOES     to 2,
    ClothingCategory.ACCESSORY to 1,
    ClothingCategory.BAG       to 1
)

private val COLOR_MAP = mapOf(
    "white" to Color(0xFFF5F5F5), "black" to Color(0xFF222222),
    "grey" to Color(0xFF9E9E9E), "gray" to Color(0xFF9E9E9E),
    "blue" to Color(0xFF1976D2), "navy" to Color(0xFF1A237E),
    "red" to Color(0xFFD32F2F), "pink" to Color(0xFFF48FB1),
    "green" to Color(0xFF388E3C), "yellow" to Color(0xFFFBC02D),
    "orange" to Color(0xFFF57C00), "purple" to Color(0xFF7B1FA2),
    "brown" to Color(0xFF5D4037), "beige" to Color(0xFFF5E6C8),
    "khaki" to Color(0xFFBDB76B), "multicolor" to Color(0xFFE91E63),
    "blue and white stripes" to Color(0xFF42A5F5), "gold" to Color(0xFFFFD700)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    userItems: List<ClothingItem>,
    onBack: () -> Unit
) {
    val allItems = userItems + BasicWardrobeProvider.items
    val byCategory = ClothingCategory.entries.associateWith { cat ->
        userItems.count { it.category == cat }
    }
    val maxCount = byCategory.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val totalUser = userItems.size

    val colorCounts = userItems
        .flatMap { item -> COLOR_MAP.keys.filter { item.color.contains(it) } }
        .groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.take(8)

    val gaps = RECOMMENDED_MIN.entries
        .filter { (cat, min) -> (byCategory[cat] ?: 0) < min }
        .map { (cat, min) -> cat to min - (byCategory[cat] ?: 0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WARDROBE ANALYSIS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = White)
            )
        },
        containerColor = White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Stats row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("My Items", "$totalUser", Modifier.weight(1f))
                StatCard("Basics", "${BasicWardrobeProvider.items.size}", Modifier.weight(1f))
                StatCard("Total", "${allItems.size}", Modifier.weight(1f))
            }

            // Category breakdown
            SectionTitle("Category Breakdown")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ClothingCategory.entries.forEach { cat ->
                    val count = byCategory[cat] ?: 0
                    val recommended = RECOMMENDED_MIN[cat] ?: 1
                    CategoryBar(
                        label = cat.label,
                        count = count,
                        recommended = recommended,
                        fraction = count.toFloat() / maxCount
                    )
                }
            }

            // Color palette
            if (colorCounts.isNotEmpty()) {
                SectionTitle("Color Palette")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    colorCounts.forEach { (colorName, count) ->
                        val bg = COLOR_MAP[colorName] ?: Mist
                        val textColor = if (bg.luminance() < 0.4f) White else Ink
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(bg)
                                    .then(if (bg.luminance() > 0.8f) Modifier.then(
                                        Modifier.background(bg, CircleShape)
                                    ) else Modifier)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(colorName, style = MaterialTheme.typography.labelSmall, color = Ash)
                            Text("$count", style = MaterialTheme.typography.labelSmall, color = InkLight)
                        }
                    }
                }
            }

            // Gap analysis
            if (gaps.isNotEmpty()) {
                SectionTitle("Wardrobe Gaps")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Paper)
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        gaps.forEach { (cat, missing) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Not enough ${cat.label}", style = MaterialTheme.typography.bodySmall, color = Ink)
                                Text(
                                    "Add $missing more",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Warm
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Paper)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Great wardrobe! All categories are well balanced ✓", style = MaterialTheme.typography.bodySmall, color = Ash)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Paper)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light, color = Ink)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Ink)
}

@Composable
private fun CategoryBar(label: String, count: Int, recommended: Int, fraction: Float) {
    val barColor = if (count >= recommended) Ink else Warm
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkLight,
            modifier = Modifier.width(48.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Mist)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
        Text("$count", style = MaterialTheme.typography.labelSmall, color = Ash,
            modifier = Modifier.width(20.dp))
    }
}

private fun Color.luminance(): Float {
    val r = red; val g = green; val b = blue
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
