package com.kon.myaacapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kon.myaacapp.ui.theme.MyAACAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val settingsRepository = SettingsRepository(newBase)
        val languageCode = runBlocking {
            settingsRepository.languageCodeFlow.first()
        }
        super.attachBaseContext(LocaleHelper.wrap(newBase, languageCode))
        com.google.android.play.core.splitcompat.SplitCompat.installActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        setContent {
            val viewModel: AACViewModel = viewModel()
            val langCode by viewModel.languageCode.collectAsState()
            val layoutDir = if (langCode == "he") LayoutDirection.Rtl else LayoutDirection.Ltr

            MyAACAppTheme {
                val navController = rememberNavController()

                androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = "grid",
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("grid") {
                                MainCommunicationScreen(
                                    viewModel = viewModel,
                                    onNavigateToCategory = { id ->
                                        viewModel.setCategory(id)
                                    },
                                    onBackClick = {
                                        navController.popBackStack()
                                    },
                                    onNavigateToAdmin = {
                                        navController.navigate("admin")
                                    }
                                )
                            }

                            composable("admin") {
                                AdminDashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToProfiles = { navController.navigate("profiles") }
                                )
                            }

                            composable("profiles") {
                                ProfileManagerScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
