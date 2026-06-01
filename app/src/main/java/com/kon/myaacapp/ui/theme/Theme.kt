package com.kon.myaacapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = ProfPrimary,
    onPrimary = ProfOnPrimary,
    primaryContainer = ProfPrimaryContainer,
    onPrimaryContainer = ProfOnPrimaryContainer,
    secondary = ProfSecondary,
    onSecondary = ProfOnSecondary,
    secondaryContainer = ProfSecondaryContainer,
    onSecondaryContainer = ProfOnSecondaryContainer,
    background = ProfBackground,
    onBackground = ProfOnBackground,
    surface = ProfSurface,
    onSurface = ProfOnSurface,
    surfaceVariant = ProfSurfaceVariant,
    onSurfaceVariant = ProfOnSurfaceVariant,
    outline = ProfOutline,
    outlineVariant = ProfOutlineVariant,
    error = ProfError,
    errorContainer = ProfErrorContainer
)

@Composable
fun MyAACAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}