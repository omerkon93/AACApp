package com.kon.myaacapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kon.myaacapp.ui.theme.MyAACAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyAACAppTheme {
                val navController = rememberNavController()
                val viewModel: AACViewModel = viewModel()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "grid",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(
                            route = "grid?parentId={parentId}",
                            arguments = listOf(
                                navArgument("parentId") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { backStackEntry ->
                            val parentId = backStackEntry.arguments?.getString("parentId")
                            
                            // Update ViewModel's category based on the current route
                            LaunchedEffect(parentId) {
                                viewModel.setCategory(parentId)
                            }

                            MainCommunicationScreen(
                                viewModel = viewModel,
                                onNavigateToCategory = { id ->
                                    navController.navigate("grid?parentId=$id")
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
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}