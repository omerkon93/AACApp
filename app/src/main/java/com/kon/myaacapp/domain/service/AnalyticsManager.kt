package com.kon.myaacapp.domain.service

import com.kon.myaacapp.data.local.entity.TileClickEvent
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.domain.model.AnalyticsTimeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsManager(
    private val repository: AACRepository,
    scope: CoroutineScope,
) {
    private val _selectedTimeFilter =
        MutableStateFlow(AnalyticsTimeFilter.ALL_TIME)

    val selectedTimeFilter:
            StateFlow<AnalyticsTimeFilter> =
        _selectedTimeFilter.asStateFlow()

    val filteredClickEvents:
            StateFlow<List<TileClickEvent>> =
        _selectedTimeFilter
            .flatMapLatest { filter ->
                val now = System.currentTimeMillis()

                val startTime = when (filter) {
                    AnalyticsTimeFilter.DAILY -> {
                        getStartOfDay()
                    }

                    AnalyticsTimeFilter.WEEKLY -> {
                        getStartOfWeek()
                    }

                    AnalyticsTimeFilter.MONTHLY -> {
                        getStartOfMonth()
                    }

                    AnalyticsTimeFilter.YEARLY -> {
                        getStartOfYear()
                    }

                    AnalyticsTimeFilter.ALL_TIME -> {
                        0L
                    }
                }

                repository.getClickEventsBetween(
                    startTime = startTime,
                    endTime = now,
                )
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun setTimeFilter(
        filter: AnalyticsTimeFilter,
    ) {
        _selectedTimeFilter.value = filter
    }

    private fun getStartOfDay(): Long =
        Calendar.getInstance().apply {
            setStartOfDay()
        }.timeInMillis

    private fun getStartOfWeek(): Long =
        Calendar.getInstance().apply {
            set(
                Calendar.DAY_OF_WEEK,
                firstDayOfWeek,
            )
            setStartOfDay()
        }.timeInMillis

    private fun getStartOfMonth(): Long =
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            setStartOfDay()
        }.timeInMillis

    private fun getStartOfYear(): Long =
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, 1)
            setStartOfDay()
        }.timeInMillis

    private fun Calendar.setStartOfDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}