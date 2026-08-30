package com.trymeon.app.ui.screens.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trymeon.app.data.repository.OutfitLogRepository
import com.trymeon.app.data.repository.WardrobeRepository
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.model.OutfitLog
import com.trymeon.app.ui.theme.Ash
import com.trymeon.app.ui.theme.Ink
import com.trymeon.app.ui.theme.Mist
import com.trymeon.app.ui.theme.Paper
import com.trymeon.app.ui.theme.Warm
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class StreakStats(
    val current: Int,
    val best: Int,
    val totalDays: Int,
    val uniqueOutfitsAllTime: Int,
    val lastWornDate: LocalDate?,
    val recent: List<OutfitLog>
)

object StreakEngine {
    fun compute(logs: List<OutfitLog>, today: LocalDate = LocalDate.now()): StreakStats {
        if (logs.isEmpty()) return StreakStats(0, 0, 0, 0, null, emptyList())

        val sorted = logs.mapNotNull {
            runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> d to it }
        }.sortedByDescending { it.first }

        val lastDate = sorted.firstOrNull()?.first

        // Current streak = consecutive days ending today or yesterday with non-repeating items
        val seen = mutableSetOf<Long>()
        var current = 0
        var prev: LocalDate? = null
        val daysFromToday = lastDate?.let { ChronoUnit.DAYS.between(it, today) } ?: 999L
        if (daysFromToday <= 1) {
            for ((d, log) in sorted) {
                if (prev != null && ChronoUnit.DAYS.between(d, prev) != 1L) break
                if (log.itemIds.any { it in seen }) break
                seen.addAll(log.itemIds)
                current++
                prev = d
            }
        }

        // Best streak ever — sliding window over entire history
        var best = 0
        val asc = sorted.reversed()
        val windowSeen = mutableSetOf<Long>()
        var run = 0
        var prevAsc: LocalDate? = null
        for ((d, log) in asc) {
            val consecutive = prevAsc?.let { ChronoUnit.DAYS.between(it, d) == 1L } ?: true
            val unique = log.itemIds.none { it in windowSeen }
            if (consecutive && unique) {
                windowSeen.addAll(log.itemIds)
                run++
            } else {
                windowSeen.clear()
                windowSeen.addAll(log.itemIds)
                run = 1
            }
            if (run > best) best = run
            prevAsc = d
        }

        val uniqueOutfits = sorted.map { it.second.itemIds.sorted() }.distinct().size

        return StreakStats(
            current = current,
            best = best,
            totalDays = sorted.size,
            uniqueOutfitsAllTime = uniqueOutfits,
            lastWornDate = lastDate,
            recent = sorted.take(7).map { it.second }
        )
    }

    fun milestones(currentStreak: Int): List<Pair<Int, String>> = listOf(
        3 to "🔥 Warming up",
        7 to "✨ One week strong",
        14 to "💎 Fortnight icon",
        30 to "👑 Monthly legend",
        60 to "🌟 Two-month all-star"
    ).map { (n, label) -> n to label }

    fun nextMilestone(currentStreak: Int): Pair<Int, String>? =
        milestones(currentStreak).firstOrNull { it.first > currentStreak }
}

@Composable
fun StreakScreen(
    wardrobeRepository: WardrobeRepository,
    logRepository: OutfitLogRepository,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val logs by logRepository.getLogs().collectAsState(emptyList())
    val items by wardrobeRepository.getAllClothing().collectAsState(emptyList())
    val stats = remember(logs) { StreakEngine.compute(logs) }
    val next = remember(stats) { StreakEngine.nextMilestone(stats.current) }

    Column(modifier = Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Ink) }
            Column(modifier = Modifier.weight(1f)) {
                Text("STREAKS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light, color = Ink)
                Text("Wear different, every day", style = MaterialTheme.typography.labelSmall, color = Ash)
            }
            if (stats.current > 0) {
                IconButton(onClick = {
                    val milestone = StreakEngine.milestones(stats.current).firstOrNull { stats.current >= it.first }?.second ?: ""
                    val bmp = com.trymeon.app.share.ShareCardRenderer.render(
                        com.trymeon.app.share.ShareCardRenderer.CardSpec.Streak(days = stats.current, milestone = milestone)
                    )
                    com.trymeon.app.share.Sharer.share(context, bmp, "${stats.current}-day outfit streak 🔥")
                }) {
                    Icon(Icons.Default.Share, "Share", tint = Ink)
                }
            }
        }
        HorizontalDivider(color = Mist)

        // Hero number
        Column(
            modifier = Modifier.fillMaxWidth().padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🔥", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stats.current.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = Ink, fontWeight = FontWeight.Light
                )
                Text(
                    " day${if (stats.current != 1) "s" else ""}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ash, modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Text(
                if (stats.current == 0) "Log today's outfit to start"
                else "Current streak — no repeats",
                style = MaterialTheme.typography.bodySmall, color = Ash
            )
            if (next != null) {
                Spacer(Modifier.height(20.dp))
                StreakProgressBar(current = stats.current, target = next.first, label = next.second)
            }
        }

        HorizontalDivider(color = Mist)

        Row(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            BigStat(label = "BEST EVER", value = stats.best.toString(), modifier = Modifier.weight(1f))
            BigStat(label = "TOTAL LOGGED", value = stats.totalDays.toString(), modifier = Modifier.weight(1f))
            BigStat(label = "UNIQUE LOOKS", value = stats.uniqueOutfitsAllTime.toString(), modifier = Modifier.weight(1f))
        }

        HorizontalDivider(color = Mist)
        Spacer(Modifier.height(20.dp))

        // Milestone badges
        Text("MILESTONES", style = MaterialTheme.typography.labelSmall, color = Warm, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))
        StreakEngine.milestones(stats.current).forEach { (n, label) ->
            MilestoneRow(target = n, label = label, achieved = stats.best >= n)
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Mist)
        Spacer(Modifier.height(20.dp))

        // Recent history
        if (stats.recent.isNotEmpty()) {
            Text("LAST 7 LOGS", style = MaterialTheme.typography.labelSmall, color = Warm, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            stats.recent.forEach { log ->
                RecentLogRow(log = log, items = items)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StreakProgressBar(current: Int, target: Int, label: String) {
    val pct = (current.toFloat() / target).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth(0.7f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Next: $label", style = MaterialTheme.typography.labelMedium, color = Ink, modifier = Modifier.weight(1f))
            Text("$current / $target", style = MaterialTheme.typography.labelSmall, color = Ash)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Mist)
        ) {
            Box(modifier = Modifier.fillMaxWidth(pct).fillMaxHeight().background(Warm))
        }
    }
}

@Composable
private fun BigStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}

@Composable
private fun MilestoneRow(target: Int, label: String, achieved: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (achieved) Ink else Paper),
            contentAlignment = Alignment.Center
        ) {
            Text(
                target.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (achieved) Color.White else Ash,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (achieved) Ink else Ash,
            modifier = Modifier.weight(1f)
        )
        if (achieved) Text("✓", color = Warm, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecentLogRow(log: OutfitLog, items: List<ClothingItem>) {
    val parts = log.itemIds.mapNotNull { id -> items.firstOrNull { it.id == id }?.name?.takeIf { it.isNotEmpty() } ?: items.firstOrNull { it.id == id }?.category?.label }
    val pretty = parts.take(3).joinToString(" · ") + if (parts.size > 3) " · +${parts.size - 3}" else ""
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(log.date.substring(5).replace("-", "/"), style = MaterialTheme.typography.labelMedium, color = Ash, modifier = Modifier.width(52.dp))
        Text(pretty.ifBlank { "${log.itemIds.size} items" }, style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}
