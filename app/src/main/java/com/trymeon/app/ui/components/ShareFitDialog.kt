package com.trymeon.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.trymeon.app.data.repository.FitLookRepository
import com.trymeon.app.domain.model.ClothingCategory
import com.trymeon.app.domain.model.FitLook
import com.trymeon.app.domain.model.UserProfile
import com.trymeon.app.ui.theme.Ash
import com.trymeon.app.ui.theme.Ink
import com.trymeon.app.ui.theme.Mist
import com.trymeon.app.ui.theme.Paper
import com.trymeon.app.ui.theme.Warm
import kotlinx.coroutines.launch

/**
 * Publishes one look, with the body it was worn on, to people of that build.
 *
 * Everything the reader will see is on this screen before it is sent — the
 * image, the numbers, the size and the verdict — because a body is the one
 * thing an app must never publish on someone's behalf. Height is required:
 * without it the look matches nobody and the share is a photo into a void.
 */
@Composable
fun ShareFitDialog(
    imagePath: String,
    uid: String,
    profile: UserProfile?,
    repository: FitLookRepository,
    /** Best guess at what was worn; the wearer can rewrite it. */
    garmentSuggestion: String = "",
    categorySuggestion: ClothingCategory? = null,
    source: String = FitLook.SOURCE_TRYON,
    onPosted: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var garment by remember { mutableStateOf(garmentSuggestion) }
    var category by remember { mutableStateOf(categorySuggestion ?: ClothingCategory.INNER) }
    var size by remember { mutableStateOf("") }
    var fit by remember { mutableStateOf(FitLook.FIT_TRUE) }
    var note by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val bodyReady = profile != null && profile.height > 0 && profile.gender.isNotBlank()

    Dialog(onDismissRequest = { if (!posting) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("SHARE YOUR FIT", style = MaterialTheme.typography.labelSmall, color = Warm, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("So people your size can see it on you", style = MaterialTheme.typography.bodySmall, color = Ash)
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.width(72.dp).aspectRatio(0.75f)
                        .clip(RoundedCornerShape(8.dp)).background(Paper)
                ) {
                    FashionImage(
                        model = imagePath, contentDescription = "Your look",
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Shown with", style = MaterialTheme.typography.labelSmall, color = Ash)
                    if (bodyReady) {
                        val p = profile!!
                        Text(
                            listOfNotNull(
                                p.gender, "${p.height}cm",
                                p.weight.takeIf { it > 0 }?.let { "${it}kg" }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.titleSmall, color = Ink
                        )
                        Text(
                            if (source == FitLook.SOURCE_TRYON) "AI render, labelled as one" else "Your photo",
                            style = MaterialTheme.typography.labelSmall, color = Ash
                        )
                    } else {
                        Text("Add your height in Profile first", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = garment, onValueChange = { garment = it },
                label = { Text("What you're wearing") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))
            Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = Ash)
            Spacer(Modifier.height(6.dp))
            FlowChips(
                options = ClothingCategory.entries.map { it.label to it },
                selected = category,
                onSelect = { category = it }
            )

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = size, onValueChange = { size = it },
                label = { Text("Size worn (e.g. M, 38)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))
            Text("HOW IT FIT", style = MaterialTheme.typography.labelSmall, color = Ash)
            Spacer(Modifier.height(6.dp))
            FlowChips(
                options = listOf(
                    "Runs small" to FitLook.FIT_SMALL,
                    "True to size" to FitLook.FIT_TRUE,
                    "Runs large" to FitLook.FIT_LARGE
                ),
                selected = fit,
                onSelect = { fit = it }
            )

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "Your image, height and weight will be visible to other users of a similar build. You can remove it any time from Profile.",
                style = MaterialTheme.typography.labelSmall, color = Ash
            )

            if (error.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, enabled = !posting, modifier = Modifier.weight(1f)) { Text("Cancel", color = Ash) }
                Button(
                    onClick = {
                        val p = profile ?: return@Button
                        if (garment.isBlank()) { error = "Say what you're wearing"; return@Button }
                        scope.launch {
                            posting = true; error = ""
                            val look = FitLook(
                                uid = uid,
                                gender = p.gender,
                                // Exactly what the "Shown with" line above says,
                                // and nothing else. Bust, waist and hips used to
                                // travel with it — never displayed, never used to
                                // match, and readable by every signed-in user.
                                heightCm = p.height, weightKg = p.weight,
                                garment = garment.trim(),
                                category = category.name,
                                sizeWorn = size.trim(),
                                fit = fit,
                                note = note.trim(),
                                source = source
                            )
                            val result = repository.post(look, imagePath)
                            posting = false
                            if (result.isSuccess) onPosted()
                            else error = "Couldn't share: ${result.exceptionOrNull()?.message}"
                        }
                    },
                    enabled = !posting && bodyReady && uid.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) {
                    if (posting) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("SHARE", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FlowChips(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (label, value) ->
            val sel = value == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sel) Ink else Paper)
                    .border(1.dp, if (sel) Ink else Mist, RoundedCornerShape(8.dp))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else Ink)
            }
        }
    }
}
