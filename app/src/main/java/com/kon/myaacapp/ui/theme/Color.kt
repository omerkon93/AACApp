package com.kon.myaacapp.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

// Professional Palette from Design
val ProfPrimary = Color(0xFF005DA7)
val ProfOnPrimary = Color(0xFFFFFFFF)
val ProfPrimaryContainer = Color(0xFF2976C7)
val ProfOnPrimaryContainer = Color(0xFFFDFCFF)
val ProfSecondary = Color(0xFF615E57)
val ProfOnSecondary = Color(0xFFFFFFFF)
val ProfBackground = Color(0xFFFBF9F8)
val ProfOnBackground = Color(0xFF1B1C1C)
val ProfSurface = Color(0xFFFBF9F8)
val ProfOnSurface = Color(0xFF1B1C1C)
val ProfSurfaceVariant = Color(0xFFE4E2E1)
val ProfOnSurfaceVariant = Color(0xFF414751)
val ProfOutline = Color(0xFF717783)
val ProfOutlineVariant = Color(0xFFC1C7D3)
val ProfError = Color(0xFFBA1A1A)
val ProfErrorContainer = Color(0xFFFFDAD6)
val ProfSecondaryContainer = Color(0xFFE7E2D9)
val ProfOnSecondaryContainer = Color(0xFF67645D)

val PrimaryBlue = Color(0xFF3182CE)

/**
 * Fitzgerald Key color mapping for AAC tiles.
 * Provides more vibrant colors for better category recognition while maintaining readability.
 */
fun resolveFitzgeraldColor(partOfSpeech: String?): Color {
    return when (partOfSpeech?.uppercase()) {
        "PRONOUN", "PEOPLE" -> Color(0xFFFFF59D) // Yellow
        "VERB", "ACTIONS" -> Color(0xFFA5D6A7)   // Green
        "ADJECTIVE" -> Color(0xFF90CAF9)         // Blue
        "NOUN" -> Color(0xFFFFCC80)              // Orange
        "SOCIAL" -> Color(0xFFF48FB1)            // Pink
        else -> Color.White
    }
}