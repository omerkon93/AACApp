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
     * Wraps the context with the selected locale.
     */
    fun wrap(context: Context, languageCode: String): Context {
        val normalized = normalize(languageCode)

        // FIX: Replaced the deprecated Locale(String) constructor with the modern
        // BCP 47 language tag builder, which is much safer and standard-compliant.
        val locale = Locale.forLanguageTag(normalized)

        // FIX: Since your minSdk is 26+, we completely removed the SDK version check.
        // We can confidently use the modern `.locales` list directly.
        val currentLocale = context.resources.configuration.locales[0]

        // Context check fast-return
        if (currentLocale.language == locale.language) {
            return context
        }

        Locale.setDefault(locale)

        // Deep-copy the configuration to prevent global Context corruption
        val config = Configuration(context.resources.configuration)

        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }
}