package com.kon.myaacapp.app

import android.app.Application
import android.content.Context
import com.google.android.play.core.splitcompat.SplitCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MyApplication : Application() {

    // A globally available, lifecycle-aware CoroutineScope.

    val applicationScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default
        )

    val appContainer: AppContainer by lazy {
        AppContainer(
            application = this,
            applicationScope = applicationScope,
        )
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        SplitCompat.install(this)
    }

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            try {
                appContainer
                    .settingsRepository
                    .languageCodeFlow
                    .first()
            } catch (error: Exception) {
                error.printStackTrace()
            }
        }
    }
}