package com.kon.myaacapp.app

import kotlinx.coroutines.flow.map
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.play.core.splitcompat.SplitCompat
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.ui.admin.AdminDashboardRoute
import com.kon.myaacapp.ui.admin.AdminDashboardScreen
import com.kon.myaacapp.ui.communication.CommunicationRoute
import com.kon.myaacapp.ui.communication.MainCommunicationScreen
import com.kon.myaacapp.ui.profile.ProfileManagerRoute
import com.kon.myaacapp.ui.profile.ProfileManagerScreen
import com.kon.myaacapp.ui.theme.MyAACAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val settingsRepository = SettingsRepository(newBase)

        val languageCode = runBlocking(Dispatchers.IO) {
            settingsRepository.languageCodeFlow.first()
        }

        super.attachBaseContext(LocaleHelper.wrap(newBase, languageCode))
        SplitCompat.installActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val application =
                applicationContext as MyApplication

            val langCode by remember(application) {
                application
                    .appContainer
                    .settingsRepository
                    .languageCodeFlow
                    .map { languageCode ->
                        LocaleHelper.normalize(languageCode)
                    }
            }.collectAsState(
                initial = "he",
            )

            val layoutDir = remember(langCode) {
                if (langCode == "he") LayoutDirection.Rtl else LayoutDirection.Ltr
            }

            MyAACAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = "grid",
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                composable("grid") {
                                    val onNavigateBackFromRoot: () -> Unit =
                                        remember(navController) {
                                            {
                                                navController.popBackStack()
                                                Unit
                                            }
                                        }

                                    val onNavigateToAdmin: () -> Unit =
                                        remember(navController) {
                                            {
                                                navController.navigate("admin")
                                            }
                                        }

                                    CommunicationRoute(
                                        viewModelFactory =
                                            application
                                                .appContainer
                                                .communicationViewModelFactory,
                                        onNavigateBackFromRoot =
                                            onNavigateBackFromRoot,
                                        onNavigateToAdmin =
                                            onNavigateToAdmin,
                                    ) { state, onAction ->
                                        MainCommunicationScreen(
                                            state = state,
                                            onAction = onAction,
                                        )
                                    }
                                }

                                composable("admin") {
                                    val onNavigateBack =
                                        remember(navController) { { navController.popBackStack(); Unit } }
                                    val onNavigateToProfiles =
                                        remember(navController) { { navController.navigate("profiles") } }

                                    val onCommunicationReset =
                                        remember(application) {
                                            {
                                                application
                                                    .appContainer
                                                    .communicationSessionController
                                                    .requestReset()
                                            }
                                        }

                                    AdminDashboardRoute(
                                        viewModelFactory =
                                            application
                                                .appContainer
                                                .adminDashboardViewModelFactory,
                                    ) { state, onAction ->

                                        AdminDashboardScreen(
                                            state = state,
                                            onAction = onAction,
                                            tileEditorViewModelFactory =
                                                application
                                                    .appContainer
                                                    .tileEditorViewModelFactory,
                                            adminGridViewModelFactory =
                                                application
                                                    .appContainer
                                                    .adminGridViewModelFactory,
                                            layoutSettingsViewModelFactory =
                                                application
                                                    .appContainer
                                                    .layoutSettingsViewModelFactory,
                                            adminListViewModelFactory =
                                                application
                                                    .appContainer
                                                    .adminListViewModelFactory,
                                            adminStatisticsViewModelFactory =
                                                application
                                                    .appContainer
                                                    .adminStatisticsViewModelFactory,
                                            systemSettingsViewModelFactory =
                                                application
                                                    .appContainer
                                                    .systemSettingsViewModelFactory,
                                            audioRecordingService =
                                                application
                                                    .appContainer
                                                    .audioRecordingService,
                                            audioPreviewManager =
                                                application
                                                    .appContainer
                                                    .audioPreviewManager,
                                            onCommunicationReset =
                                                onCommunicationReset,
                                            onNavigateBack =
                                                onNavigateBack,
                                            onNavigateToProfiles =
                                                onNavigateToProfiles,
                                        )
                                    }
                                }

                                composable("profiles") {
                                    val onNavigateBack =
                                        remember(navController) {
                                            {
                                                navController.popBackStack()
                                                Unit
                                            }
                                        }

                                    val onProfileChanged =
                                        remember(application) {
                                            {
                                                application
                                                    .appContainer
                                                    .communicationSessionController
                                                    .requestReset()
                                            }
                                        }

                                    ProfileManagerRoute(
                                        viewModelFactory =
                                            application
                                                .appContainer
                                                .profileManagerViewModelFactory,
                                        onProfileChanged = onProfileChanged,
                                    ) { state, onAction ->
                                        ProfileManagerScreen(
                                            state = state,
                                            onAction = onAction,
                                            onNavigateBack = onNavigateBack,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}