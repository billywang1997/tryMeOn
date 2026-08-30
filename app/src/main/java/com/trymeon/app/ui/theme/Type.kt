package com.trymeon.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Editorial type scale.
 *
 * Two families, which is what the scale was always shaped for and never had: a
 * serif carries the display sizes and the screen headers, and the sans carries
 * everything a reader has to work through. One family throughout is why a
 * carefully tracked scale still read as a system app.
 *
 * Both are platform families — no font files, no download, no dependency, and
 * nothing to fail on a device that has neither. On Android the serif is Noto
 * Serif, which is a proper text face rather than a display novelty, so it holds
 * up at 52sp and at 20sp alike.
 *
 * Serif is deliberately kept off small text and off anything set in caps with
 * wide tracking below ~20sp, where its detail turns to noise.
 *
 * Every style states its own line height. Compose defaults to
 * `includeFontPadding = false`, which makes the line box exactly the font's own
 * metrics and crops the tops off capitals — visible as a half-drawn "1 ITEM" in
 * the closet header, and on every wide-tracked caps label in the app, which is
 * most of the labels it has.
 */
private val Display = FontFamily.Serif
private val Text = FontFamily.Default
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Light,
        fontSize = 52.sp,
        lineHeight = 54.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Light,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.3).sp
    ),
    displaySmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 4.sp        // wide editorial tracking for screen headers
    ),
    titleMedium = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 1.8.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.6.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.4.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Text,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        letterSpacing = 1.3.sp      // wide tracking for the small caps tags
    )
)
