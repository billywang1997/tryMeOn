package com.trymeon.app.ui.screens.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trymeon.app.data.remote.ClaudeApiService
import com.trymeon.app.data.repository.WardrobeRepository
import com.trymeon.app.ui.theme.*

private val QUICK_PRESETS = listOf(
    "Job interview in 30 minutes",
    "Surprise date night",
    "Last-minute wedding invite",
    "Important client meeting",
    "Friend's birthday party",
    "Dinner straight after work"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    wardrobeRepository: WardrobeRepository,
    claudeApiService: ClaudeApiService,
    apiKey: String,
    onBack: () -> Unit
) {
    val vm: EmergencyViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            EmergencyViewModel(wardrobeRepository, claudeApiService, apiKey) as T
    })
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, null, tint = Warm, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Emergency Outfit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = White)
            )
        },
        containerColor = White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("What's the occasion?", style = MaterialTheme.typography.titleSmall, color = Ash)

            // Quick presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_PRESETS.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            val selected = state.situation == preset
                            FilterChip(
                                selected = selected,
                                onClick = { vm.setSituation(preset) },
                                label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Ink,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        if (row.size < 2) Spacer(Modifier.weight(1f))
                    }
                }
            }

            OutlinedTextField(
                value = state.situation,
                onValueChange = { vm.setSituation(it) },
                label = { Text("Or describe your situation…") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                minLines = 2
            )

            Button(
                onClick = { vm.go() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.isLoading && state.situation.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Styling in a flash…", color = Color.White, style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.Default.Bolt, null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Style me now!", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }

            if (state.error.isNotEmpty()) {
                Text(state.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (state.result.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Paper)
                        .padding(18.dp)
                ) {
                    Text(state.result, style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
            }
        }
    }
}
