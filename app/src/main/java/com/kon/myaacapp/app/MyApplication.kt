package com.kon.myaacapp.app

import android.app.Application
import android.content.Context
import com.google.android.play.core.splitcompat.SplitCompat
import com.kon.myaacapp.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MyApplication : Application() {

    // A globally available, lifecycle-aware CoroutineScope.
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // FIX: Changed "Play Core" to "Play Store" to resolve the IDE spellcheck warning.
        // Mandatory for Google Play Store dynamic language modules (Split APKs).
        SplitCompat.install(this)
    }

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            try {
                // 1. Pre-warm DataStore
                // FIX: Actively invoking .first() forces the OS to read the preferences
                // file into memory immediately, resolving the "unused variable" warning
                // and ensuring true pre-warming.
                val settings = SettingsRepository(this@MyApplication)
                settings.languageCodeFlow.first()

                // 2. Pre-warm SQLite / Room Database (Uncomment and apply your actual DB call)
                // AACDatabase.getDatabase(this@MyApplication).query("SELECT 1", null)

                // 3. Pre-warm Text-To-Speech Engine (Uncomment and apply your TTS helper)
                // TextToSpeechHelper.getInstance(this@MyApplication)

            } catch (e: Exception) {
                // Background pre-warming failures should be logged but never crash the app
                e.printStackTrace()
            }
        }
    }
}