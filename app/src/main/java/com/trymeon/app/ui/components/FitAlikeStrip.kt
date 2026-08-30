package com.trymeon.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trymeon.app.domain.fit.BodyMatch
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.FitLook
import com.trymeon.app.domain.model.UserProfile
import com.trymeon.app.ui.theme.Ash
import com.trymeon.app.ui.theme.Ink
import com.trymeon.app.ui.theme.Paper
import com.trymeon.app.ui.theme.Warm

/**
 * "People your size": shared looks from bodies within a few centimetres and
 * kilos of this one, nearest first.
 *
 * Renders nothing when there is no match rather than a sad empty state — on
 * a shopping surface, silence is better than a strip that says the community
 * is empty. The one exception is the wearer's own tab, where [emptyHint]
 * invites the first share.
 */
@Composable
fun FitAlikeStrip(
    looks: List<FitLook>,
    profile: UserProfile?,
    category: ClothingCategory? = null,
    keywords: List<String> = emptyList(),
    title: String = "PEOPLE YOUR SIZE",
    emptyHint: String? = null,
    modifier: Modifier = Modifier
) {
    val matches = remember(looks, profile, category, keywords) {
        BodyMatch.forProfile(profile, looks, category, keywords)
    }
    var open by remember { mutableStateOf<FitLook?>(null) }

    if (matches.isEmpty()) {
        if (emptyHint != null && profile != null && profile.height > 0) {
            Text(emptyHint, style = MaterialTheme.typography.bodySmall, color = Ash, modifier = modifier)
        }
        return
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Warm, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                "${matches.size} within ±${BodyMatch.HEIGHT_TOLERANCE_CM}cm",
                style = MaterialTheme.typography.labelSmall, color = Ash
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(matches, key = { it.look.id }) { m -> FitCard(m.look) { open = m.look } }
        }
    }

    open?.let { look -> FitLookSheet(look) { open = null } }
}

@Composable
private fun FitCard(look: FitLook, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(124.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Paper)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).background(Color.White)) {
            FashionImage(
                model = look.imageUrl, contentDescription = look.garment,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
            if (look.isRender) {
                Box(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        .clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.92f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) { Text("AI", style = MaterialTheme.typography.labelSmall, color = Ash) }
            }
        }
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(look.bodyLabel, style = MaterialTheme.typography.labelMedium, color = Ink, fontWeight = FontWeight.Medium)
            Text(
                listOf(look.sizeWorn.takeIf { it.isNotBlank() }, look.fitLabel).filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = Warm
            )
            Text(look.garment, style = MaterialTheme.typography.labelSmall, color = Ash, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FitLookSheet(look: FitLook, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)).clickable(onClick = onDismiss)
        ) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                FashionImage(
                    model = look.imageUrl, contentDescription = look.garment,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                    contentScale = ContentScale.Fit
                )
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(look.garment, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        listOf(look.gender, look.bodyLabel, look.sizeWorn.takeIf { it.isNotBlank() }, look.fitLabel)
                            .filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f)
                    )
                    if (look.note.isNotBlank()) {
                        Text(look.note, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                    }
                    if (look.isRender) {
                        Text("AI try-on render, shared by the wearer", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.45f))
                    }
                }
            }
        }
    }
}
