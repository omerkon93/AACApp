package com.kon.myaacapp.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.google.android.play.core.splitcompat.SplitCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) {
                    appContainer.updateCurrentActivity(activity)
                    applicationScope.launch {
                        runCatching {
                            appContainer.familyEntitlementProvider.refreshEntitlements()
                        }
                    }
                }

                override fun onActivityPaused(activity: Activity) {
                    appContainer.clearCurrentActivityIfMatching(activity)
                }

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) {
                    appContainer.clearCurrentActivityIfMatching(activity)
                }
            },
        )

        applicationScope.launch {
            runCatching {
                appContainer
                    .startupCoordinator
                    .initialize()
            }.onFailure { error ->
                error.printStackTrace()
            }
        }

        applicationScope.launch {
            runCatching {
                appContainer.familyEntitlementProvider.refreshEntitlements()
            }.onFailure { error ->
                error.printStackTrace()
            }
        }
    }
}
