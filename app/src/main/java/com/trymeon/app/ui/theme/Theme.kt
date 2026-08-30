package com.trymeon.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FashionColorScheme = lightColorScheme(
    primary              = Ink,
    onPrimary            = White,
    primaryContainer     = Mist,
    onPrimaryContainer   = Ink,
    secondary            = InkLight,
    onSecondary          = White,
    secondaryContainer   = Paper,
    onSecondaryContainer = Ink,
    tertiary             = Warm,
    onTertiary           = White,
    background           = White,
    onBackground         = Ink,
    surface              = White,
    onSurface            = Ink,
    surfaceVariant       = Paper,
    onSurfaceVariant     = InkLight,
    outline              = Mist,
    error                = ErrorRed,
    onError              = White,
    errorContainer       = Color(0xFFFFDAD6),
    onErrorContainer     = Color(0xFF410002),
)

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FashionColorScheme,
        typography  = Typography,
        content     = content
    )
}
