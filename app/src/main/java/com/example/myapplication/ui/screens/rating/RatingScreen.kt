package com.example.myapplication.ui.screens.rating

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.example.myapplication.data.remote.ClaudeApiService
import com.example.myapplication.data.repository.UserProfileRepository
import com.example.myapplication.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingScreen(
    claudeApiService: ClaudeApiService,
    profileRepository: UserProfileRepository,
    apiKey: String,
    onBack: () -> Unit
) {
    val vm: RatingViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            RatingViewModel(claudeApiService, profileRepository, apiKey) as T
    })
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.setImage(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = Warm, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Outfit Rating", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Light)
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Photo picker
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Paper)
                    .border(1.dp, Mist, RoundedCornerShape(16.dp))
                    .clickable { picker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (state.imageUri != null) {
                    SubcomposeAsyncImage(
                        model = state.imageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📸", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap to upload today's outfit", style = MaterialTheme.typography.bodyMedium, color = Ash)
                    }
                }
            }

            if (state.imageUri != null) {
                Button(
                    onClick = { vm.rate(context) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("AI rating…", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    } else {
                        Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Rate my outfit", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (state.error.isNotEmpty()) {
                Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (state.result.isNotEmpty()) {
                val score = remember(state.result) { parseScore(state.result) }
                ScoreCard(score)
            }
        }
    }
}

@Composable
private fun ScoreCard(score: OutfitScore) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Paper)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Rating Results", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Ink)

        listOf(
            "Color Harmony" to score.color,
            "Occasion Fit" to score.occasion,
            "Overall Look" to score.overall
        ).forEach { (label, s) ->
            ScoreRow(label, s)
        }

        if (score.highlight.isNotEmpty()) {
            HorizontalDivider(color = Mist)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("✨", fontSize = 14.sp)
                Text(score.highlight, style = MaterialTheme.typography.bodySmall, color = Ink)
            }
        }
        if (score.suggestion.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("💡", fontSize = 14.sp)
                Text(score.suggestion, style = MaterialTheme.typography.bodySmall, color = InkLight)
            }
        }
    }
}

@Composable
private fun ScoreRow(label: String, score: Int) {
    val color = when {
        score >= 8 -> Color(0xFF2E7D32)
        score >= 6 -> Warm
        else -> Color(0xFFB71C1C)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkLight, modifier = Modifier.width(64.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Mist)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(score / 10f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        Text("$score", style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Medium,
            modifier = Modifier.width(24.dp))
    }
}
