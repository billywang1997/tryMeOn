package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.myapplication.data.repository.MarketRepository
import com.example.myapplication.domain.model.ClothingItem
import com.example.myapplication.domain.model.MarketListing
import com.example.myapplication.ui.theme.Ash
import com.example.myapplication.ui.theme.Ink
import com.example.myapplication.ui.theme.Mist
import com.example.myapplication.ui.theme.Paper
import com.example.myapplication.ui.theme.Warm
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun SellItemDialog(
    item: ClothingItem,
    repository: MarketRepository,
    onDismiss: () -> Unit,
    onPosted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var askingPrice by remember { mutableStateOf(if (item.price > 0) "%.0f".format(item.price * 0.6) else "") }
    var size by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("Good") }
    var note by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val user = remember { FirebaseAuth.getInstance().currentUser }
    val canPost = user != null && !user.isAnonymous
    // Listing image must be a cloud URL so other shoppers can load it.
    // A local file path (cloudImageUrl not yet synced) would be unreadable to them.
    val hasCloudImage = item.cloudImageUrl.startsWith("http")
    val conditions = listOf("Like new", "Good", "Fair")

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(20.dp)
        ) {
            Text("LIST FOR SALE", style = MaterialTheme.typography.labelSmall, color = Warm, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                item.name.ifEmpty { item.category.label },
                style = MaterialTheme.typography.titleMedium, color = Ink,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
            Spacer(Modifier.height(16.dp))

            if (!canPost) {
                Text(
                    "Sign in with a real account on Profile to list items.",
                    style = MaterialTheme.typography.bodySmall, color = Ash
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                    Text("OK", color = Color.White)
                }
                return@Column
            }

            if (!hasCloudImage) {
                Text(
                    "This item's photo is still syncing to the cloud. Once it finishes, you'll be able to list it so other shoppers can see it.",
                    style = MaterialTheme.typography.bodySmall, color = Ash
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                    Text("OK", color = Color.White)
                }
                return@Column
            }

            OutlinedTextField(
                value = askingPrice, onValueChange = { askingPrice = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Asking price (AUD)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = size, onValueChange = { size = it },
                label = { Text("Size (e.g. M, 32, AU 10)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Text("CONDITION", style = MaterialTheme.typography.labelSmall, color = Warm, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(conditions) { c ->
                    val sel = c == condition
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (sel) Ink else Paper)
                            .border(1.dp, if (sel) Ink else Mist, RoundedCornerShape(20.dp))
                            .clickable { condition = c }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(c, style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else Ink)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City (e.g. Sydney)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 4
            )

            if (error.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel", color = Ash) }
                Button(
                    onClick = {
                        val price = askingPrice.toDoubleOrNull()
                        if (price == null || price <= 0) { error = "Enter a valid price"; return@Button }
                        scope.launch {
                            posting = true
                            error = ""
                            val listing = MarketListing(
                                sellerUid = user.uid,
                                sellerName = user.displayName.orEmpty().ifBlank { user.email?.substringBefore('@').orEmpty() },
                                sellerEmail = user.email.orEmpty(),
                                title = item.name.ifEmpty { item.category.label },
                                category = item.category.name,
                                color = item.color,
                                brand = item.brand,
                                condition = condition,
                                size = size,
                                askingPrice = price,
                                originalPrice = item.price,
                                imageUrl = item.cloudImageUrl,
                                note = note,
                                city = city
                            )
                            val result = repository.post(listing)
                            posting = false
                            if (result.isSuccess) onPosted()
                            else error = "Post failed: ${result.exceptionOrNull()?.message}"
                        }
                    },
                    enabled = !posting,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) {
                    if (posting) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("LIST IT", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            }
        }
    }
}
