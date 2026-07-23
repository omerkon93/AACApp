package com.kon.myaacapp.ui.admin

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AdminDashboardRoute(
    viewModelFactory: AdminDashboardViewModelFactory,
    content: @Composable (
        state: AdminDashboardState,
        onAction: (AdminDashboardAction) -> Unit,
    ) -> Unit,
) {
    val context = LocalContext.current

    val adminDashboardViewModel:
            AdminDashboardViewModel =
        viewModel(
            key = "admin-dashboard",
            factory = viewModelFactory,
        )

    val state by adminDashboardViewModel.state
        .collectAsStateWithLifecycle()

    LaunchedEffect(adminDashboardViewModel) {
        adminDashboardViewModel.effects.collect { effect ->
            when (effect) {
                is AdminDashboardEffect.ShowError -> {
                    Toast.makeText(
                        context,
                        effect.message,
                        Toast.LENGTH_LONG,
                    ).show()

                    adminDashboardViewModel.onAction(
                        AdminDashboardAction.ErrorConsumed
                    )
                }
            }
        }
    }

    content(
        state,
        adminDashboardViewModel::onAction,
    )
}