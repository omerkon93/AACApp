package com.kon.myaacapp.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Calming Palette Elements
val SandBeigeBackground = Color(0xFFF4F1EA)
val PrimarySlateDark = Color(0xFF2D3748)
val MutedSlateGray = Color(0xFF718096)
val SoftInteractiveBlue = Color(0xFFE2E8F0)
val SmoothWhite = Color(0xFFFFFFFF)
val PrimaryBlue = Color(0xFF3182CE)

/**
 * Fitzgerald Key color mapping for AAC tiles.
 * Provides more vibrant colors for better category recognition while maintaining readability.
 */
fun resolveFitzgeraldColor(partOfSpeech: String?): Color {
    return when (partOfSpeech?.uppercase()) {
        "PRONOUN", "PEOPLE" -> Color(0xFFFFF59D) // Yellow
        "VERB", "ACTIONS" -> Color(0xFFC8E6C9)   // Green
        "ADJECTIVE" -> Color(0xFFBBDEFB)         // Blue
        "NOUN" -> Color(0xFFFFE0B2)              // Orange
        "SOCIAL" -> Color(0xFFF8BBD0)            // Pink
        else -> Color.White
    }
}