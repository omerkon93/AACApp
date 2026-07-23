package com.kon.myaacapp.ui.admin.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AdminStatisticsRoute(
    languageCode: String,
    viewModelFactory: AdminStatisticsViewModelFactory,
    content: @Composable (
        state: AdminStatisticsState,
        onAction: (AdminStatisticsAction) -> Unit,
    ) -> Unit,
) {
    val statisticsViewModel:
            AdminStatisticsViewModel =
        viewModel(
            key = "admin-statistics",
            factory = viewModelFactory,
        )

    val state by statisticsViewModel.state
        .collectAsStateWithLifecycle()

    LaunchedEffect(
        statisticsViewModel,
        languageCode,
    ) {
        statisticsViewModel.updateLanguage(
            value = languageCode,
        )
    }

    content(
        state,
        statisticsViewModel::onAction,
    )
}