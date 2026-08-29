package com.example.myapplication.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.domain.model.OutfitLog
import com.example.myapplication.ui.theme.Ash
import com.example.myapplication.ui.theme.Ink
import com.example.myapplication.ui.theme.Warm

object CostPerWearEngine {
    /** Wears = how many OutfitLogs contain this item's id. */
    fun wearCount(item: ClothingItem, logs: List<OutfitLog>): Int =
        logs.count { item.id in it.itemIds }

    /** $/wear; returns null if price or wear count unknown. */
    fun perWear(item: ClothingItem, logs: List<OutfitLog>): Double? {
        if (item.price <= 0.0) return null
        val wears = wearCount(item, logs)
        return if (wears == 0) item.price else item.price / wears
    }

    data class Totals(
        val totalSpend: Double,
        val totalWears: Int,
        val avgCpw: Double,
        val mostValuableItem: ClothingItem?,
        val mostValuableCpw: Double
    )

    fun totals(items: List<ClothingItem>, logs: List<OutfitLog>): Totals {
        val priced = items.filter { it.price > 0 }
        val totalSpend = priced.sumOf { it.price }
        val totalWears = priced.sumOf { wearCount(it, logs) }
        val avgCpw = if (totalWears > 0) totalSpend / totalWears else 0.0
        val byCpw = priced.mapNotNull { item ->
            val cpw = perWear(item, logs) ?: return@mapNotNull null
            val wears = wearCount(item, logs)
            if (wears >= 3) item to cpw else null
        }
        val best = byCpw.minByOrNull { it.second }
        return Totals(totalSpend, totalWears, avgCpw, best?.first, best?.second ?: 0.0)
    }
}

@Composable
fun CostPerWearBadge(
    item: ClothingItem,
    logs: List<OutfitLog>,
    modifier: Modifier = Modifier
) {
    val cpw = CostPerWearEngine.perWear(item, logs)
    val wears = CostPerWearEngine.wearCount(item, logs)
    if (cpw == null) return

    // Animate the number on entrance
    var target by remember { mutableStateOf(0f) }
    LaunchedEffect(cpw) { target = cpw.toFloat() }
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "cpw-animation"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFAF9F6))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "COST PER WEAR",
                style = MaterialTheme.typography.labelSmall,
                color = Warm, fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$%.2f".format(animated),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${wears} wear${if (wears != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ash,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
fun WardrobeWorthCard(
    items: List<ClothingItem>,
    logs: List<OutfitLog>
) {
    val totals = remember(items, logs) { CostPerWearEngine.totals(items, logs) }
    if (totals.totalSpend <= 0.0) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFAF9F6))
            .padding(20.dp)
    ) {
        Text("💸  WARDROBE WORTH", style = MaterialTheme.typography.labelSmall, color = Warm, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$%,.0f".format(totals.totalSpend),
                style = MaterialTheme.typography.displaySmall,
                color = Ink, fontWeight = FontWeight.Light
            )
            Spacer(Modifier.width(8.dp))
            Text("invested", style = MaterialTheme.typography.bodyMedium, color = Ash, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Stat(
                label = "TOTAL WEARS",
                value = totals.totalWears.toString(),
                modifier = Modifier.weight(1f)
            )
            Stat(
                label = "AVG / WEAR",
                value = if (totals.avgCpw > 0) "$%.2f".format(totals.avgCpw) else "—",
                modifier = Modifier.weight(1f)
            )
        }
        if (totals.mostValuableItem != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "🏆 Best value:  ${totals.mostValuableItem.name.ifEmpty { totals.mostValuableItem.category.label }}  ·  $%.2f/wear".format(totals.mostValuableCpw),
                style = MaterialTheme.typography.labelMedium, color = Ink
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.Medium)
    }
}
