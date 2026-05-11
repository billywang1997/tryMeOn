package com.example.myapplication.ui.screens.dna

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.BasicWardrobeProvider
import com.example.myapplication.domain.model.ClothingCategory
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.ui.theme.*

data class StyleProfile(
    val type: String,
    val emoji: String,
    val description: String,
    val traits: List<String>,
    val gradientStart: Color,
    val gradientEnd: Color,
    val stats: List<Pair<String, String>>
)

private val NEUTRALS = setOf("white", "black", "grey", "gray", "beige", "khaki")
private val WARM_COLORS = setOf("red", "orange", "yellow", "brown", "gold", "pink")
private val COOL_COLORS = setOf("blue", "navy", "green", "purple")

fun computeStyleDna(items: List<ClothingItem>): StyleProfile {
    if (items.isEmpty()) return StyleProfile(
        "Style Explorer", "🌟", "Your wardrobe is just getting started — add more pieces to discover your style!",
        listOf("Potential", "Open to anything"),
        Color(0xFFE8E8E8), Color(0xFFF7F7F7), emptyList()
    )

    val colorWords = items.flatMap { it.color.split(",", " ", "/") }
    val neutralCount = colorWords.count { w -> NEUTRALS.any { w.contains(it, ignoreCase = true) } }
    val warmCount = colorWords.count { w -> WARM_COLORS.any { w.contains(it, ignoreCase = true) } }
    val coolCount = colorWords.count { w -> COOL_COLORS.any { w.contains(it, ignoreCase = true) } }

    val byCat = ClothingCategory.entries.associateWith { cat -> items.count { it.category == cat } }
    val total = items.size

    val dressPct = (byCat[ClothingCategory.DRESS] ?: 0) * 100 / total.coerceAtLeast(1)
    val accessPct = ((byCat[ClothingCategory.ACCESSORY] ?: 0) + (byCat[ClothingCategory.BAG] ?: 0)) * 100 / total.coerceAtLeast(1)
    val outerPct = (byCat[ClothingCategory.OUTERWEAR] ?: 0) * 100 / total.coerceAtLeast(1)
    val neutralPct = neutralCount * 100 / colorWords.size.coerceAtLeast(1)

    val stats = listOf(
        "Total Items" to "$total pieces",
        "Main Palette" to when {
            neutralPct > 55 -> "Neutrals"
            warmCount > coolCount -> "Warm tones"
            else -> "Cool tones"
        },
        "Top Category" to (byCat.maxByOrNull { it.value }?.key?.label ?: "-")
    )

    return when {
        neutralPct > 60 && total <= 20 -> StyleProfile(
            "Minimalist", "🖤",
            "You believe \"less is more\" — every piece is intentionally chosen. Black, white, and grey are your tools; simplicity is your signature.",
            listOf("Restrained", "Precise", "Elevated", "Individual"),
            Color(0xFF111111), Color(0xFF555555), stats
        )
        dressPct > 35 || (warmCount > neutralCount && warmCount > coolCount) -> StyleProfile(
            "Romantic", "🌸",
            "You love life's beautiful details — dresses and soft tones are your everyday. Dressing is poetry of self-expression.",
            listOf("Feminine", "Refined", "Tasteful", "Expressive"),
            Color(0xFFE91E8C), Color(0xFFFF8A65), stats
        )
        outerPct > 30 || (byCat[ClothingCategory.INNER] ?: 0) > 5 -> StyleProfile(
            "Power Dresser", "💼",
            "Your wardrobe is ready for any occasion — layering and professionalism are your standard. Shirts and outerwear are non-negotiable.",
            listOf("Professional", "Reliable", "Versatile", "Polished"),
            Color(0xFF1A237E), Color(0xFF42A5F5), stats
        )
        accessPct > 25 || total > 30 -> StyleProfile(
            "Free Bohemian", "🎨",
            "Your wardrobe is rich and eclectic — accessories are your finishing touch. You love mixing styles; no labels needed.",
            listOf("Free spirit", "Mixed", "Storytelling", "Limitless"),
            Color(0xFF6A1B9A), Color(0xFFFF7043), stats
        )
        coolCount > warmCount && coolCount > neutralCount -> StyleProfile(
            "Street Artist", "🎧",
            "Cool tones and athletic energy are your foundation — comfort and attitude coexist. Your looks have an effortless coolness.",
            listOf("Cool", "Comfortable", "Attitude", "Independent"),
            Color(0xFF0D47A1), Color(0xFF1B5E20), stats
        )
        else -> StyleProfile(
            "Versatile Pragmatist", "✨",
            "Your wardrobe is balanced and practical — ready for any situation. You're the \"rational thinker\" of fashion philosophy.",
            listOf("Practical", "Versatile", "Flexible", "Calm"),
            Color(0xFF37474F), Color(0xFF78909C), stats
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleDnaScreen(
    userItems: List<ClothingItem>,
    onBack: () -> Unit
) {
    val allItems = userItems + BasicWardrobeProvider.items
    val profile = remember(userItems) { computeStyleDna(userItems) }

    val alpha by rememberInfiniteTransition(label = "").animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = ""
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Style DNA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Style card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(profile.gradientStart, profile.gradientEnd)))
                    .alpha(alpha)
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(profile.emoji, fontSize = 52.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        profile.type,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Light,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        profile.traits.forEach { trait ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(trait, style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Description
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Paper)
                    .padding(18.dp)
            ) {
                Text(profile.description, style = MaterialTheme.typography.bodyMedium, color = Ink, lineHeight = 22.sp)
            }

            // Stats
            if (profile.stats.isNotEmpty()) {
                Text("Wardrobe Stats", style = MaterialTheme.typography.titleSmall, color = Ash,
                    modifier = Modifier.align(Alignment.Start))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    profile.stats.forEach { (label, value) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Paper)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(value, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Light, color = Ink)
                                Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
