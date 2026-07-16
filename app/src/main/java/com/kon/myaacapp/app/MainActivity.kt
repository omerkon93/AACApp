package com.kon.myaacapp.app

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.play.core.splitcompat.SplitCompat
import com.kon.myaacapp.AACViewModel
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.ui.admin.AdminDashboardScreen
import com.kon.myaacapp.ui.communication.MainCommunicationScreen
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
            val viewModel: AACViewModel = viewModel()
            val langCode by viewModel.languageCode.collectAsState()

            val layoutDir = remember(langCode) {
                if (langCode == "he") LayoutDirection.Rtl else LayoutDirection.Ltr
            }

            MyAACAppTheme {
                // 👉 ADD THIS SURFACE WRAPPER
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
                                    val onNavigateToCategory: (String) -> Unit = remember(viewModel) {
                                        { id -> viewModel.setCategory(id) }
                                    }

                                    val onBackClick: () -> Unit = remember(navController) {
                                        { navController.popBackStack() }
                                    }

                                    val onNavigateToAdmin: () -> Unit = remember(navController) {
                                        { navController.navigate("admin") }
                                    }

                                    // 👉 1. Use resetToHome() which already exists in your ViewModel!
                                    val onHomeClick: () -> Unit = remember(viewModel) {
                                        { viewModel.resetToHome() }
                                    }

                                    MainCommunicationScreen(
                                        viewModel = viewModel,
                                        onNavigateToCategory = onNavigateToCategory,
                                        onBackClick = onBackClick,
                                        onNavigateToAdmin = onNavigateToAdmin,
                                        onHomeClick = onHomeClick // 👉 2. Pass it exactly like this
                                    )
                                }

                                composable("admin") {
                                    val onNavigateBack =
                                        remember(navController) { { navController.popBackStack(); Unit } }
                                    val onNavigateToProfiles =
                                        remember(navController) { { navController.navigate("profiles") } }

                                    AdminDashboardScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = onNavigateBack,
                                        onNavigateToProfiles = onNavigateToProfiles
                                    )
                                }

                                composable("profiles") {
                                    val onNavigateBack =
                                        remember(navController) { { navController.popBackStack(); Unit } }

                                    ProfileManagerScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = onNavigateBack
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