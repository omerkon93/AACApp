package com.kon.myaacapp.core.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    /**
     * Normalizes language codes for internal app use.
     * Android and Play Store often use "iw" for Hebrew, but "he" is more standard.
     */
    fun normalize(languageCode: String): String {
        // OPTIMIZATION: Zero-allocation fast paths.
        if (languageCode == "he" || languageCode == "en") return languageCode
        if (languageCode == "iw") return "he"

        return when (languageCode.lowercase()) {
            "in" -> "id" // Indonesian
            "ji" -> "yi" // Yiddish
            else -> languageCode
        }
    }

    /**
     * For Play Store Split Install Manager, we strictly need "iw".
     */
    fun forSplitInstall(languageCode: String): String {
        return if (languageCode == "he" || languageCode == "iw") "iw" else languageCode
    }

    /**
     * Wraps the context with the selected locale and updates process default locale.
     */
    fun wrap(context: Context, languageCode: String): Context {
        val normalized = normalize(languageCode)
        val locale = Locale.forLanguageTag(normalized)

        Locale.setDefault(locale)
        return createLocalizedContext(context, normalized)
    }

    /**
     * Creates a localized configuration context without mutating JVM default locale.
     */
    fun createLocalizedContext(context: Context, languageCode: String): Context {
        val normalized = normalize(languageCode)
        val locale = Locale.forLanguageTag(normalized)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }
}