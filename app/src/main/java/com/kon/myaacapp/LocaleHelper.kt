package com.kon.myaacapp

import android.content.Context
import java.util.Locale

object LocaleHelper {
    /**
     * Normalizes language codes for internal app use.
     * Android and Play Store often use "iw" for Hebrew, but "he" is more standard.
     * We use "he" for database queries and file paths consistently.
     */
    fun normalize(languageCode: String): String {
        return when (languageCode.lowercase()) {
            "iw" -> "he"
            "in" -> "id" // Indonesian
            "ji" -> "yi" // Yiddish
            else -> languageCode
        }
    }

    /**
     * For Play Store Split Install Manager, we might need "iw" specifically.
     */
    fun forSplitInstall(languageCode: String): String {
        return if (languageCode == "he") "iw" else languageCode
    }

    /**
     * Wraps the context with the selected locale.
     */
    fun wrap(context: Context, languageCode: String): Context {
        val normalized = normalize(languageCode)
        val locale = Locale(normalized)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
