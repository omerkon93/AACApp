package com.kon.myaacapp.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

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
val ProfSurfaceContainerLowest = Color(0xFFFFFFFF)
val ProfSurfaceContainerLow = Color(0xFFF5F3F3)
val ProfSurfaceContainer = Color(0xFFF0EDED)
val ProfSurfaceContainerHigh = Color(0xFFEAE8E7)
val ProfSurfaceContainerHighest = Color(0xFFE4E2E1)
val ProfError = Color(0xFFBA1A1A)
val ProfErrorContainer = Color(0xFFFFDAD6)
val ProfSecondaryContainer = Color(0xFFE7E2D9)
val ProfOnSecondaryContainer = Color(0xFF67645D)

// Calming Palette Elements (Existing)
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