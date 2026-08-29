package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.myapplication.data.remote.GoogleImageSearchService
import com.example.myapplication.data.remote.UnsplashService
import com.example.myapplication.ui.theme.Ash
import com.example.myapplication.ui.theme.Mist
import java.io.File

// Strip photography jargon so Unsplash can actually find the item
private val JARGON = Regex(
    "(?i)\\b(ghost mannequin|flat lay|product photo(graphy)?|isolated|white background|no model|studio shot|women|men|folded)\\b"
)
private fun cleanForUnsplash(query: String) =
    query.replace(JARGON, "").replace(Regex("\\s{2,}"), " ").trim()

@Composable
fun FashionImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f
) {
    val context = LocalContext.current

    // Use LaunchedEffect + mutableStateOf — safe to use unconditionally unlike produceState
    // inside when-branches which violates Compose's rules-of-composables.
    var resolvedUrl by remember(model) { mutableStateOf<String?>(null) }

    LaunchedEffect(model) {
        resolvedUrl = null  // reset on model change
        when {
            // amazon: and google: both resolve to face-free packshots.
            // Google ghost-mannequin search is tried first; Amazon product
            // photos (which usually feature a model's face) are intentionally
            // not used for wardrobe essentials.
            model is String && (model.startsWith("amazon:") || model.startsWith("google:")) -> {
                val q = model.substringAfter(':')
                resolvedUrl = GoogleImageSearchService.resolveUrl(q)
                    ?: UnsplashService.resolveUrl(cleanForUnsplash(q))
            }
            model is String && model.startsWith("unsplash:") -> {
                val q = model.removePrefix("unsplash:")
                resolvedUrl = UnsplashService.resolveUrl(q)
            }
        }
    }

    val displayModel: Any? = when {
        model is String && (model.startsWith("amazon:") || model.startsWith("google:") || model.startsWith("unsplash:")) ->
            resolvedUrl  // null while loading → shows loading shimmer
        model is String && model.startsWith("/") -> File(model)
        else -> model
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(displayModel)
            .crossfade(300)
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        alpha = alpha,
        modifier = modifier,
        loading = {
            Box(Modifier.fillMaxSize().background(Mist))
        },
        error = {
            Box(
                Modifier.fillMaxSize().background(Mist),
                contentAlignment = Alignment.Center
            ) {
                Text("·", style = MaterialTheme.typography.bodySmall, color = Ash)
            }
        }
    )
}
