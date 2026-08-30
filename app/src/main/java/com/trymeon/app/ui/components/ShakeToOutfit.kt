package com.trymeon.app.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.ClothingItem
import com.trymeon.app.domain.model.OutfitLog
import com.trymeon.app.ui.theme.Ash
import com.trymeon.app.ui.theme.Ink
import com.trymeon.app.ui.theme.Mist
import com.trymeon.app.ui.theme.Paper
import com.trymeon.app.ui.theme.Warm
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

/**
 * Registers a SensorManager-based shake detector while the host composable is in composition.
 * onShake fires when accelerometer magnitude crosses [threshold] g and a cooldown has elapsed.
 */
@Composable
fun rememberShakeDetector(
    threshold: Float = 2.5f,
    cooldownMs: Long = 1200L,
    onShake: () -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastShakeAt = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val gForce = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
                if (gForce > threshold) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeAt > cooldownMs) {
                        lastShakeAt = now
                        onShake()
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager?.unregisterListener(listener) }
    }
}

/**
 * Produces a random outfit by picking one item from each "essential" category
 * if available in the wardrobe. Categories considered (in order):
 * INNER/DRESS as top, OUTERWEAR (optional), PANTS (skipped if DRESS picked),
 * SHOES, BAG (optional), ACCESSORY (optional).
 */
fun rollRandomOutfit(items: List<ClothingItem>): List<ClothingItem> {
    if (items.isEmpty()) return emptyList()
    val byCat = items.groupBy { it.category }
    val result = mutableListOf<ClothingItem>()
    val dressed = byCat[ClothingCategory.DRESS]?.randomOrNull()
    if (dressed != null && (0..1).random() == 0) {
        result.add(dressed)
    } else {
        byCat[ClothingCategory.INNER]?.randomOrNull()?.let(result::add)
        byCat[ClothingCategory.PANTS]?.randomOrNull()?.let(result::add)
    }
    // 50% chance to add outerwear if available
    if ((0..1).random() == 0) byCat[ClothingCategory.OUTERWEAR]?.randomOrNull()?.let(result::add)
    byCat[ClothingCategory.SHOES]?.randomOrNull()?.let(result::add)
    if ((0..1).random() == 0) byCat[ClothingCategory.BAG]?.randomOrNull()?.let(result::add)
    if ((0..2).random() == 0) byCat[ClothingCategory.ACCESSORY]?.randomOrNull()?.let(result::add)
    return result.distinctBy { it.id }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShakeOutfitSheet(
    initial: List<ClothingItem>,
    wardrobe: List<ClothingItem>,
    onLogOutfit: suspend (OutfitLog) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var outfit by remember { mutableStateOf(initial) }
    var saved by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎲", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("SHAKE & STYLE", style = MaterialTheme.typography.labelSmall, color = Warm, fontWeight = FontWeight.SemiBold)
                    Text("Today's random look", style = MaterialTheme.typography.titleSmall, color = Ink, fontWeight = FontWeight.Medium)
                }
                Text(
                    "${outfit.size} PIECES",
                    style = MaterialTheme.typography.labelSmall, color = Ash
                )
            }
            HorizontalDivider(color = Mist)
            Spacer(Modifier.height(14.dp))

            if (outfit.isEmpty()) {
                Text(
                    "Your wardrobe is too small to roll an outfit. Add at least one top + one bottom.",
                    style = MaterialTheme.typography.bodySmall, color = Ash,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(outfit, key = { it.id }) { item -> RolledItemCard(item) }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = { outfit = rollRandomOutfit(wardrobe); saved = false },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🎲  ROLL AGAIN", color = Ink, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = {
                        if (outfit.isEmpty()) return@Button
                        scope.launch {
                            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                            onLogOutfit(OutfitLog(date = today, itemIds = outfit.map { it.id }, note = "Shake outfit"))
                            saved = true
                        }
                    },
                    enabled = outfit.isNotEmpty() && !saved,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) {
                    Text(if (saved) "✓ LOGGED" else "WEAR TODAY", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun RolledItemCard(item: ClothingItem) {
    Column(
        modifier = Modifier.width(112.dp).clip(RoundedCornerShape(10.dp)).background(Paper)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(0.85f).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            val img = item.imagePath.takeIf { it.isNotEmpty() } ?: item.cloudImageUrl
            if (img.isNotBlank()) {
                AsyncImage(
                    model = if (img.startsWith("http")) img else File(img),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(item.category.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Warm)
            Text(
                item.name.ifEmpty { item.category.label },
                style = MaterialTheme.typography.labelMedium, color = Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
