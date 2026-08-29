package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.domain.model.OutfitLog
import com.example.myapplication.ui.theme.Ash
import com.example.myapplication.ui.theme.Ink
import com.example.myapplication.ui.theme.Mist
import com.example.myapplication.ui.theme.Paper
import com.example.myapplication.ui.theme.Warm
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.io.File

sealed class SurpriseCard {
    abstract val emoji: String
    abstract val headline: String
    abstract val subtitle: String

    data class ForgottenGem(
        val item: ClothingItem,
        val daysSinceWorn: Int
    ) : SurpriseCard() {
        override val emoji = "💎"
        override val headline = "${daysSinceWorn} days forgotten"
        override val subtitle = "Bring back the ${item.name.ifEmpty { item.category.label }.lowercase()}"
    }

    data class OnThisDay(
        val items: List<ClothingItem>,
        val yearsAgo: Int
    ) : SurpriseCard() {
        override val emoji = "📸"
        override val headline = "$yearsAgo year${if (yearsAgo > 1) "s" else ""} ago today"
        override val subtitle = "You wore ${items.size} pieces — remember this look?"
    }

    data class MostWornThisMonth(
        val item: ClothingItem,
        val count: Int
    ) : SurpriseCard() {
        override val emoji = "⭐"
        override val headline = "$count wears this month"
        override val subtitle = "${item.name.ifEmpty { item.category.label }} is your MVP"
    }

    data class Streak(
        val days: Int
    ) : SurpriseCard() {
        override val emoji = "🔥"
        override val headline = "$days-day streak"
        override val subtitle = "All unique outfits — no repeats"
    }

    data class WeatherPick(
        val item: ClothingItem,
        val tempC: Int,
        val condition: String
    ) : SurpriseCard() {
        override val emoji = when {
            tempC < 10 -> "🧥"
            tempC > 25 -> "☀️"
            condition.contains("rain", true) -> "🌧"
            else -> "🌤"
        }
        override val headline = "${tempC}° · $condition"
        override val subtitle = "Try your ${item.name.ifEmpty { item.category.label }.lowercase()}"
    }

    object Empty : SurpriseCard() {
        override val emoji = "✨"
        override val headline = "Build your daily moments"
        override val subtitle = "Log outfits to unlock daily surprises"
    }
}

/**
 * Pure logic that selects a daily surprise. Deterministic per (date, wardrobe), so opening the app
 * multiple times on the same day shows the same card.
 */
object DailySurpriseEngine {

    fun pick(
        items: List<ClothingItem>,
        logs: List<OutfitLog>,
        today: LocalDate = LocalDate.now(),
        weatherTempC: Int? = null,
        weatherCondition: String? = null
    ): SurpriseCard {
        if (items.isEmpty()) return SurpriseCard.Empty

        val candidates = mutableListOf<SurpriseCard>()

        // 1. On This Day (from logs)
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val md = todayStr.substring(5) // "MM-dd"
        logs.firstOrNull { it.date.endsWith(md) && it.date != todayStr }?.let { log ->
            val yearsAgo = today.year - log.date.substring(0, 4).toInt()
            val worn = items.filter { it.id in log.itemIds }
            if (worn.isNotEmpty() && yearsAgo >= 1) {
                candidates.add(SurpriseCard.OnThisDay(worn, yearsAgo))
            }
        }

        // 2. Streak: consecutive days at the end of logs where no item repeats.
        //    Only counts as a live streak if the most recent log is today or yesterday.
        val recentLogs = logs.sortedByDescending { it.date }.take(14)
        val streak = computeStreak(recentLogs)
        val lastLogDate = recentLogs.firstOrNull()?.let { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        val streakIsLive = lastLogDate != null && ChronoUnit.DAYS.between(lastLogDate, today) <= 1L
        if (streak >= 3 && streakIsLive) candidates.add(SurpriseCard.Streak(streak))

        // 3. Most worn this month
        val monthPrefix = todayStr.substring(0, 7)
        val monthLogs = logs.filter { it.date.startsWith(monthPrefix) }
        if (monthLogs.size >= 3) {
            val freq = monthLogs.flatMap { it.itemIds }.groupingBy { it }.eachCount()
            freq.maxByOrNull { it.value }?.let { (id, count) ->
                if (count >= 3) {
                    items.firstOrNull { it.id == id }?.let {
                        candidates.add(SurpriseCard.MostWornThisMonth(it, count))
                    }
                }
            }
        }

        // 4. Forgotten Gem
        val lastWornByItem = mutableMapOf<Long, LocalDate>()
        for (log in logs) {
            val date = runCatching { LocalDate.parse(log.date) }.getOrNull() ?: continue
            for (id in log.itemIds) {
                val prev = lastWornByItem[id]
                if (prev == null || date.isAfter(prev)) lastWornByItem[id] = date
            }
        }
        val forgotten = items
            .filter { lastWornByItem[it.id] != null }
            .map { it to ChronoUnit.DAYS.between(lastWornByItem[it.id], today).toInt() }
            .filter { it.second >= 60 }
            .maxByOrNull { it.second }
        forgotten?.let { (item, days) -> candidates.add(SurpriseCard.ForgottenGem(item, days)) }

        // 5. Weather Pick (if weather provided)
        if (weatherTempC != null && weatherCondition != null) {
            val matchingItem = pickWeatherItem(items, weatherTempC)
            if (matchingItem != null) {
                candidates.add(SurpriseCard.WeatherPick(matchingItem, weatherTempC, weatherCondition))
            }
        }

        if (candidates.isEmpty()) return SurpriseCard.Empty

        // Deterministic rotation by day-of-year
        val idx = today.toEpochDay().toInt() % candidates.size
        return candidates[Math.floorMod(idx, candidates.size)]
    }

    private fun computeStreak(recent: List<OutfitLog>): Int {
        if (recent.isEmpty()) return 0
        val seenItems = mutableSetOf<Long>()
        var streak = 0
        var prevDate: LocalDate? = null
        for (log in recent) {
            val d = runCatching { LocalDate.parse(log.date) }.getOrNull() ?: break
            if (prevDate != null && ChronoUnit.DAYS.between(d, prevDate) != 1L) break
            if (log.itemIds.any { it in seenItems }) break
            seenItems.addAll(log.itemIds)
            streak++
            prevDate = d
        }
        return streak
    }

    private fun pickWeatherItem(items: List<ClothingItem>, tempC: Int): ClothingItem? {
        val cold = listOf("coat", "jacket", "sweater", "knit", "wool")
        val hot  = listOf("tee", "t-shirt", "tank", "linen", "shorts", "dress")
        val keywords = if (tempC < 12) cold else if (tempC > 25) hot else emptyList()
        if (keywords.isEmpty()) return null
        return items.firstOrNull { item ->
            val text = "${item.name} ${item.notes} ${item.category.label}".lowercase()
            keywords.any { it in text }
        }
    }
}

@Composable
fun DailySurpriseCard(
    card: SurpriseCard,
    onClick: () -> Unit = {}
) {
    val previewItem: ClothingItem? = when (card) {
        is SurpriseCard.ForgottenGem -> card.item
        is SurpriseCard.MostWornThisMonth -> card.item
        is SurpriseCard.WeatherPick -> card.item
        is SurpriseCard.OnThisDay -> card.items.firstOrNull()
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFAF9F6)) // warm off-white, distinguishes from grid
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Image or emoji thumbnail
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Paper),
            contentAlignment = Alignment.Center
        ) {
            val imgPath = previewItem?.let {
                it.imagePath.takeIf { p -> p.isNotEmpty() } ?: it.cloudImageUrl
            }
            if (!imgPath.isNullOrBlank()) {
                AsyncImage(
                    model = if (imgPath.startsWith("http")) imgPath else File(imgPath),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(card.emoji, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.emoji, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(6.dp))
                Text(
                    "TODAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = Warm,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                card.headline,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                card.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Ink)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                when (card) {
                    is SurpriseCard.ForgottenGem -> "STYLE"
                    is SurpriseCard.OnThisDay -> "VIEW"
                    is SurpriseCard.WeatherPick -> "STYLE"
                    is SurpriseCard.MostWornThisMonth -> "STYLE"
                    is SurpriseCard.Streak -> "VIEW"
                    SurpriseCard.Empty -> "OPEN"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
