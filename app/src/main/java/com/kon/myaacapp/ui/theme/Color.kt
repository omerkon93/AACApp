package com.kon.myaacapp.ui.theme

import androidx.compose.ui.graphics.Color

// Light theme
val ProfPrimary = Color(0xFF005DA7)
val ProfOnPrimary = Color(0xFFFFFFFF)
val ProfPrimaryContainer = Color(0xFF2976C7)
val ProfOnPrimaryContainer = Color(0xFFFFFFFF)

val ProfSecondary = Color(0xFF615E57)
val ProfOnSecondary = Color(0xFFFFFFFF)
val ProfSecondaryContainer = Color(0xFFE7E2D9)
val ProfOnSecondaryContainer = Color(0xFF292620)

val ProfBackground = Color(0xFFFBF9F8)
val ProfOnBackground = Color(0xFF1B1C1C)

val ProfSurface = Color(0xFFFBF9F8)
val ProfOnSurface = Color(0xFF1B1C1C)

val ProfSurfaceVariant = Color(0xFFE4E2E1)
val ProfOnSurfaceVariant = Color(0xFF414751)

val ProfOutline = Color(0xFF717783)
val ProfOutlineVariant = Color(0xFFC1C7D3)

val ProfError = Color(0xFFBA1A1A)
val ProfOnError = Color(0xFFFFFFFF)
val ProfErrorContainer = Color(0xFFFFDAD6)
val ProfOnErrorContainer = Color(0xFF410002)

// Dark theme
val ProfDarkPrimary = Color(0xFF9DCAFF)
val ProfDarkOnPrimary = Color(0xFF003258)
val ProfDarkPrimaryContainer = Color(0xFF00497F)
val ProfDarkOnPrimaryContainer = Color(0xFFD2E4FF)

val ProfDarkSecondary = Color(0xFFCBC6BD)
val ProfDarkOnSecondary = Color(0xFF33302A)
val ProfDarkSecondaryContainer = Color(0xFF4A4640)
val ProfDarkOnSecondaryContainer = Color(0xFFE7E2D9)

val ProfDarkBackground = Color(0xFF111416)
val ProfDarkOnBackground = Color(0xFFE2E2E5)

val ProfDarkSurface = Color(0xFF111416)
val ProfDarkOnSurface = Color(0xFFE2E2E5)

val ProfDarkSurfaceVariant = Color(0xFF41474D)
val ProfDarkOnSurfaceVariant = Color(0xFFC1C7CE)

val ProfDarkOutline = Color(0xFF8B9198)
val ProfDarkOutlineVariant = Color(0xFF41474D)

val ProfDarkError = Color(0xFFFFB4AB)
val ProfDarkOnError = Color(0xFF690005)
val ProfDarkErrorContainer = Color(0xFF93000A)
val ProfDarkOnErrorContainer = Color(0xFFFFDAD6)

/**
 * Dark text used on Fitzgerald Key tile backgrounds.
 *
 * Fitzgerald colors remain intentionally light in both themes,
 * so they require dark foreground content even during dark mode.
 */
val FitzgeraldTileContent = Color(0xFF41474D)

/**
 * Fitzgerald Key color mapping for AAC tiles.
 * These colors remain light to preserve category recognition.
 */
fun resolveFitzgeraldColor(partOfSpeech: String?): Color {
    return when (partOfSpeech?.uppercase()) {
        "PRONOUN", "PEOPLE" -> Color(0xFFFFF59D)
        "VERB", "ACTIONS" -> Color(0xFFA5D6A7)
        "ADJECTIVE", "DESCRIPTION" -> Color(0xFF90CAF9)
        "NOUN" -> Color(0xFFFFCC80)
        "SOCIAL" -> Color(0xFFF48FB1)
        else -> Color.White
    }
}