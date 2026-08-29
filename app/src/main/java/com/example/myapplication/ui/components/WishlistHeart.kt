package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.remote.EbayItem
import com.example.myapplication.data.repository.WishlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Heart-shaped overlay button. Drop this inside a product card's image Box,
 * aligned to TopEnd. Reads/writes the wishlist via WishlistRepository.
 */
@Composable
fun WishlistHeart(
    item: EbayItem,
    query: String = "",
    repository: WishlistRepository?,
    modifier: Modifier = Modifier
) {
    if (repository == null) return
    val scope = rememberCoroutineScope()
    val wishlistFlow: Flow<List<com.example.myapplication.domain.model.WishlistItem>> =
        remember(repository) { repository.observe() }
    val list by wishlistFlow.collectAsState(emptyList())
    val saved = list.any { it.itemWebUrl == item.itemWebUrl || it.id == item.itemId }

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f))
            .clickable {
                scope.launch {
                    if (saved) {
                        val existing = list.firstOrNull { it.itemWebUrl == item.itemWebUrl || it.id == item.itemId }
                        existing?.let { repository.remove(it.id) }
                    } else {
                        repository.add(item, query)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (saved) "Remove from wishlist" else "Add to wishlist",
            tint = if (saved) Color(0xFFE91E63) else Color(0xFF888888),
            modifier = Modifier.size(14.dp)
        )
    }
}
