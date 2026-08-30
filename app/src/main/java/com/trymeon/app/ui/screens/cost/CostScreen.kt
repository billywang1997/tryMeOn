package com.trymeon.app.ui.screens.cost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.model.OutfitLog
import com.trymeon.app.ui.components.FashionImage
import com.trymeon.app.ui.theme.*

data class CostItem(
    val item: ClothingItem,
    val wearCount: Int,
    val costPerWear: Double?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostScreen(
    userItems: List<ClothingItem>,
    logs: List<OutfitLog>,
    onBack: () -> Unit
) {
    val costItems = remember(userItems, logs) {
        userItems.map { item ->
            val wearCount = logs.count { log -> item.id in log.itemIds }
            CostItem(
                item = item,
                wearCount = wearCount,
                costPerWear = if (item.price > 0 && wearCount > 0) item.price / wearCount else null
            )
        }.sortedWith(compareBy(nullsLast()) { it.costPerWear })
    }

    val totalValue = userItems.sumOf { it.price }
    val itemsWithPrice = userItems.count { it.price > 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cost Tracker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = White)
            )
        },
        containerColor = White
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Summary
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard("Total Wardrobe Value", if (totalValue > 0) "$${totalValue.toInt()}" else "Not recorded",
                        Modifier.weight(1f))
                    SummaryCard("Items with Price", "$itemsWithPrice items", Modifier.weight(1f))
                    SummaryCard("Outfit Logs", "${logs.size} days", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }

            if (costItems.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Record prices when adding items — we'll calculate your cost per wear here",
                            style = MaterialTheme.typography.bodyMedium, color = Ash,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            } else {
                items(costItems.withIndex().toList(), key = { it.value.item.id }) { (index, ci) ->
                    CostItemRow(ci, rank = index)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Paper)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Ink)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
        }
    }
}

@Composable
private fun CostItemRow(ci: CostItem, rank: Int) {
    val isBest = rank == 0 && ci.costPerWear != null
    val isWorst = ci.costPerWear == null && ci.item.price > 0 && ci.wearCount == 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isBest) Color(0xFFF0FFF4) else Paper)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            FashionImage(
                model = ci.item.imagePath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(ci.item.name.ifEmpty { ci.item.category.label },
                    style = MaterialTheme.typography.labelLarge, color = Ink)
                if (isBest) Badge(containerColor = Color(0xFF2E7D32)) { Text("Best Value", style = MaterialTheme.typography.labelSmall) }
                if (isWorst) Badge(containerColor = Warm) { Text("Never worn", style = MaterialTheme.typography.labelSmall) }
            }
            Text(
                buildString {
                    if (ci.item.price > 0) append("$${ci.item.price.toInt()} · ")
                    append("Worn ${ci.wearCount} times")
                },
                style = MaterialTheme.typography.labelSmall, color = Ash
            )
        }

        if (ci.costPerWear != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$${"%.1f".format(ci.costPerWear)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isBest) Color(0xFF2E7D32) else Ink
                )
                Text("per wear", style = MaterialTheme.typography.labelSmall, color = Ash)
            }
        } else if (ci.item.price > 0) {
            Text("$${ci.item.price.toInt()}", style = MaterialTheme.typography.labelMedium, color = Ash)
        }
    }
}
