package com.kon.myaacapp.ui.admin.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kon.myaacapp.domain.usecase.analytics.ObserveUsageEventsUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveAllTilesUseCase

class AdminStatisticsViewModelFactory(
    private val observeAllTilesUseCase:
    ObserveAllTilesUseCase,
    private val observeUsageEventsUseCase:
    ObserveUsageEventsUseCase,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                AdminStatisticsViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return AdminStatisticsViewModel(
                observeAllTilesUseCase =
                    observeAllTilesUseCase,
                observeUsageEventsUseCase =
                    observeUsageEventsUseCase,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}