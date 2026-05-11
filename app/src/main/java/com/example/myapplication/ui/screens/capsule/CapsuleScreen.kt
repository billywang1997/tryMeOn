package com.example.myapplication.ui.screens.capsule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.repository.WardrobeRepository
import com.example.myapplication.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapsuleScreen(
    wardrobeRepository: WardrobeRepository,
    claudeApiService: ClaudeApiService,
    apiKey: String,
    onBack: () -> Unit
) {
    val vm: CapsuleViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            CapsuleViewModel(wardrobeRepository, claudeApiService, apiKey) as T
    })
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capsule Wardrobe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!state.generated) {
                Spacer(Modifier.height(32.dp))
                Text("💊", fontSize = 56.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Capsule Wardrobe",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Light,
                    color = Ink
                )
                Text(
                    "AI picks the 10 core pieces from your wardrobe\nand shows how many outfits you can build",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${state.wardrobe.size} items available",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ash
                )
            }

            Button(
                onClick = { vm.generate() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !state.isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("AI curating…", color = Color.White, style = MaterialTheme.typography.labelLarge)
                } else {
                    Text(
                        if (state.generated) "Regenerate" else "Build My Capsule Wardrobe",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (state.error.isNotEmpty()) {
                Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (state.result.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Paper)
                        .padding(18.dp)
                ) {
                    Text(state.result, style = MaterialTheme.typography.bodyMedium, color = Ink, lineHeight = 22.sp)
                }
            }
        }
    }
}
