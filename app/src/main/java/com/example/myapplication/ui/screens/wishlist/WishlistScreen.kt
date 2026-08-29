package com.example.myapplication.ui.screens.wishlist

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import com.example.myapplication.data.remote.SerpApiService
import com.example.myapplication.data.repository.WishlistRepository
import com.example.myapplication.domain.model.WishlistItem
import com.example.myapplication.ui.theme.Ash
import com.example.myapplication.ui.theme.Ink
import com.example.myapplication.ui.theme.Mist
import com.example.myapplication.ui.theme.Paper
import com.example.myapplication.ui.theme.Warm
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Composable
fun WishlistScreen(
    repository: WishlistRepository,
    serpApiKey: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serpService = remember { SerpApiService() }
    val items by repository.observe().collectAsState(emptyList())
    var refreshing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = Ink)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("WISHLIST", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light, color = Ink)
                Text(
                    "${items.size} item${if (items.size != 1) "s" else ""} · ${items.count { hasDropped(it) }} dropped",
                    style = MaterialTheme.typography.labelSmall, color = Ash
                )
            }
            if (items.isNotEmpty() && serpApiKey.isNotBlank()) {
                IconButton(
                    onClick = {
                        if (refreshing) return@IconButton
                        scope.launch {
                            refreshing = true
                            val updates = items.map { item ->
                                async {
                                    val q = item.query.ifBlank { item.title }
                                    val newPrice = serpService.search(serpApiKey, q, limit = 5)
                                        .getOrNull()
                                        ?.firstOrNull { it.title.equals(item.title, ignoreCase = true) || it.itemWebUrl == item.itemWebUrl }
                                        ?.price
                                        ?: item.lastSeenPrice
                                    item.copy(lastSeenPrice = newPrice, lastCheckedAt = System.currentTimeMillis())
                                }
                            }.awaitAll()
                            updates.forEach { repository.update(it) }
                            refreshing = false
                        }
                    }
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Ink)
                    } else {
                        Icon(Icons.Default.Refresh, null, tint = Ink)
                    }
                }
            }
        }
        HorizontalDivider(color = Mist)

        if (items.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { wishItem ->
                    WishlistRow(
                        item = wishItem,
                        onClick = {
                            if (wishItem.itemWebUrl.isNotEmpty()) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(wishItem.itemWebUrl)))
                            }
                        },
                        onRemove = { scope.launch { repository.remove(wishItem.id) } }
                    )
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🤍", style = MaterialTheme.typography.displayMedium)
            Text(
                "Tap the heart on any product",
                style = MaterialTheme.typography.bodyMedium, color = Ink
            )
            Text(
                "We'll track price changes for you.",
                style = MaterialTheme.typography.labelSmall, color = Ash
            )
        }
    }
}

@Composable
private fun WishlistRow(item: WishlistItem, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Paper)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(10.dp)).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (item.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.source.uppercase(), style = MaterialTheme.typography.labelSmall, color = Warm, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                item.title, style = MaterialTheme.typography.bodyMedium, color = Ink,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            PriceLine(item)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, "Remove", tint = Ash, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PriceLine(item: WishlistItem) {
    val saved = item.savedPrice.toDoubleOrNull() ?: 0.0
    val current = item.lastSeenPrice.toDoubleOrNull() ?: saved
    val diff = current - saved
    val fmt = NumberFormat.getCurrencyInstance(Locale.US).apply {
        maximumFractionDigits = if (current % 1.0 == 0.0) 0 else 2
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${item.currency} ${item.lastSeenPrice.ifBlank { item.savedPrice }}",
            style = MaterialTheme.typography.titleSmall, color = Ink, fontWeight = FontWeight.SemiBold
        )
        if (saved > 0 && current > 0 && kotlin.math.abs(diff) >= 0.5) {
            Spacer(Modifier.width(8.dp))
            val down = diff < 0
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (down) Color(0xFFD7EFD8) else Color(0xFFF5E1E1))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "${if (down) "▼" else "▲"} ${fmt.format(kotlin.math.abs(diff)).removePrefix("$")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (down) Color(0xFF1F7A2A) else Color(0xFFB02A2A),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun hasDropped(item: WishlistItem): Boolean {
    val s = item.savedPrice.toDoubleOrNull() ?: return false
    val c = item.lastSeenPrice.toDoubleOrNull() ?: return false
    return c < s - 0.5
}
