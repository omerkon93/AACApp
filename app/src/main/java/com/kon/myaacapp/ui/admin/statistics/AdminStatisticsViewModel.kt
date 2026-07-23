package com.kon.myaacapp.ui.admin.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kon.myaacapp.domain.model.AnalyticsTimeFilter
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileUsageEvent
import com.kon.myaacapp.domain.usecase.analytics.ObserveUsageEventsUseCase
import com.kon.myaacapp.domain.usecase.tile.ObserveAllTilesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class AdminStatisticsViewModel(
    private val observeAllTilesUseCase:
    ObserveAllTilesUseCase,
    private val observeUsageEventsUseCase:
    ObserveUsageEventsUseCase,
) : ViewModel() {

    private val languageCode =
        MutableStateFlow("he")

    private val selectedFilter =
        MutableStateFlow(
            AnalyticsTimeFilter.ALL_TIME
        )

    val state: StateFlow<AdminStatisticsState> =
        combine(
            languageCode,
            selectedFilter,
        ) { language, filter ->
            language to filter
        }.flatMapLatest { (language, filter) ->
            val now =
                System.currentTimeMillis()

            val startTime =
                calculateStartTime(filter)

            combine(
                observeAllTilesUseCase(
                    languageCode = language,
                ),
                observeUsageEventsUseCase(
                    startTime = startTime,
                    endTime = now,
                ),
            ) { allTiles, usageEvents ->
                createState(
                    allTiles = allTiles,
                    usageEvents = usageEvents,
                    selectedFilter = filter,
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started =
                    SharingStarted.WhileSubscribed(
                        stopTimeoutMillis = 5_000,
                    ),
                initialValue =
                    AdminStatisticsState(
                        isLoading = true,
                    ),
            )

    fun updateLanguage(
        value: String,
    ) {
        languageCode.value =
            value.trim().ifBlank { "he" }
    }

    fun onAction(
        action: AdminStatisticsAction,
    ) {
        when (action) {
            is AdminStatisticsAction.SelectFilter -> {
                selectedFilter.value =
                    action.filter
            }
        }
    }

    private fun createState(
        allTiles: List<CombinedTile>,
        usageEvents: List<TileUsageEvent>,
        selectedFilter: AnalyticsTimeFilter,
    ): AdminStatisticsState {
        val allCategories =
            allTiles.filter { tile ->
                tile.isCategory
            }

        if (
            allTiles.isEmpty() ||
            usageEvents.isEmpty()
        ) {
            return AdminStatisticsState(
                allTiles = allTiles,
                allCategories = allCategories,
                selectedFilter = selectedFilter,
                chartData = createChartData(
                    events = usageEvents,
                    filter = selectedFilter,
                ),
                isLoading = false,
            )
        }

        val tileMap =
            allTiles.associateBy { tile ->
                tile.id
            }

        val categoryMap =
            allCategories.associateBy { tile ->
                tile.id
            }

        val clickCounts =
            usageEvents
                .groupingBy { event ->
                    event.tileId
                }
                .eachCount()

        val totalClicks =
            clickCounts.values.sum()

        val topWords =
            clickCounts
                .mapNotNull { (tileId, count) ->
                    val tile = tileMap[tileId]

                    if (
                        tile != null &&
                        !tile.isCategory
                    ) {
                        StatisticsTopWord(
                            tileId = tile.id,
                            label = tile.label,
                            clickCount = count,
                        )
                    } else {
                        null
                    }
                }
                .sortedByDescending { item ->
                    item.clickCount
                }
                .take(4)

        val categoryCounts =
            clickCounts
                .mapNotNull { (tileId, count) ->
                    val tile = tileMap[tileId]

                    if (
                        tile != null &&
                        !tile.isCategory
                    ) {
                        val categoryLabel =
                            tile.parentId
                                ?.let { parentId ->
                                    categoryMap[parentId]
                                        ?.label
                                }
                                ?: "Root"

                        categoryLabel to count
                    } else {
                        null
                    }
                }
                .groupBy(
                    keySelector = { item ->
                        item.first
                    },
                    valueTransform = { item ->
                        item.second
                    },
                )
                .map { (label, counts) ->
                    StatisticsCategoryUsage(
                        label = label,
                        clickCount = counts.sum(),
                    )
                }
                .sortedByDescending { item ->
                    item.clickCount
                }

        val categoryBreakdown =
            if (categoryCounts.size > 4) {
                val topCategories =
                    categoryCounts.take(4)

                val otherCount =
                    categoryCounts
                        .drop(4)
                        .sumOf { item ->
                            item.clickCount
                        }

                topCategories +
                        StatisticsCategoryUsage(
                            label = "Other",
                            clickCount = otherCount,
                        )
            } else {
                categoryCounts
            }

        val activeDays =
            countActiveDays(usageEvents)

        val insight =
            calculateInsight(
                topWords = topWords,
                categoryBreakdown =
                    categoryBreakdown,
            )

        return AdminStatisticsState(
            allTiles = allTiles,
            allCategories = allCategories,
            selectedFilter = selectedFilter,
            totalClicks = totalClicks,
            topWords = topWords,
            categoryBreakdown =
                categoryBreakdown,
            chartData = createChartData(
                events = usageEvents,
                filter = selectedFilter,
            ),
            activeDays = activeDays,
            estimatedActiveMinutes =
                activeDays * 2,
            insight = insight,
            isLoading = false,
            errorMessage = null,
        )
    }

    private fun calculateInsight(
        topWords: List<StatisticsTopWord>,
        categoryBreakdown:
        List<StatisticsCategoryUsage>,
    ): StatisticsInsight {
        val topCategory =
            categoryBreakdown.firstOrNull()

        val topWord =
            topWords.firstOrNull()

        return when {
            topCategory?.label == "אוכל" ||
                    topCategory?.label
                        ?.equals(
                            other = "Food",
                            ignoreCase = true,
                        ) == true -> {
                StatisticsInsight.FOOD
            }

            topWord?.label
                ?.contains(
                    other = "רוצה",
                    ignoreCase = true,
                ) == true ||
                    topWord?.label
                        ?.contains(
                            other = "want",
                            ignoreCase = true,
                        ) == true -> {
                StatisticsInsight.REQUESTS
            }

            else -> {
                StatisticsInsight.GENERAL
            }
        }
    }

    private fun countActiveDays(
        events: List<TileUsageEvent>,
    ): Int {
        return events
            .distinctBy { event ->
                val calendar =
                    Calendar.getInstance().apply {
                        timeInMillis =
                            event.timestamp
                    }

                calendar.get(Calendar.YEAR) to
                        calendar.get(
                            Calendar.DAY_OF_YEAR
                        )
            }
            .size
    }

    private fun createChartData(
        events: List<TileUsageEvent>,
        filter: AnalyticsTimeFilter,
    ): List<StatisticsChartPoint> {
        return when (filter) {
            AnalyticsTimeFilter.DAILY -> {
                val groups =
                    events
                        .groupingBy { event ->
                            calendarValue(
                                timestamp =
                                    event.timestamp,
                                field =
                                    Calendar.HOUR_OF_DAY,
                            )
                        }
                        .eachCount()

                (0..23).map { hour ->
                    StatisticsChartPoint(
                        label = "$hour:00",
                        clickCount =
                            groups[hour] ?: 0,
                    )
                }
            }

            AnalyticsTimeFilter.WEEKLY,
            AnalyticsTimeFilter.ALL_TIME -> {
                val dayLabels =
                    listOf(
                        "א'",
                        "ב'",
                        "ג'",
                        "ד'",
                        "ה'",
                        "ו'",
                        "ש'",
                    )

                val groups =
                    events
                        .groupingBy { event ->
                            calendarValue(
                                timestamp =
                                    event.timestamp,
                                field =
                                    Calendar.DAY_OF_WEEK,
                            )
                        }
                        .eachCount()

                (1..7).map { dayOfWeek ->
                    StatisticsChartPoint(
                        label =
                            dayLabels[
                                dayOfWeek - 1
                            ],
                        clickCount =
                            groups[dayOfWeek] ?: 0,
                    )
                }
            }

            AnalyticsTimeFilter.MONTHLY -> {
                val calendar =
                    Calendar.getInstance()

                val maximumDay =
                    calendar.getActualMaximum(
                        Calendar.DAY_OF_MONTH
                    )

                val groups =
                    events
                        .groupingBy { event ->
                            calendarValue(
                                timestamp =
                                    event.timestamp,
                                field =
                                    Calendar.DAY_OF_MONTH,
                            )
                        }
                        .eachCount()

                (1..maximumDay).map { day ->
                    StatisticsChartPoint(
                        label = day.toString(),
                        clickCount =
                            groups[day] ?: 0,
                    )
                }
            }

            AnalyticsTimeFilter.YEARLY -> {
                val groups =
                    events
                        .groupingBy { event ->
                            calendarValue(
                                timestamp =
                                    event.timestamp,
                                field =
                                    Calendar.MONTH,
                            )
                        }
                        .eachCount()

                (0..11).map { month ->
                    StatisticsChartPoint(
                        label =
                            (month + 1).toString(),
                        clickCount =
                            groups[month] ?: 0,
                    )
                }
            }
        }
    }

    private fun calendarValue(
        timestamp: Long,
        field: Int,
    ): Int {
        return Calendar
            .getInstance()
            .apply {
                timeInMillis = timestamp
            }
            .get(field)
    }

    private fun calculateStartTime(
        filter: AnalyticsTimeFilter,
    ): Long {
        return when (filter) {
            AnalyticsTimeFilter.DAILY -> {
                startOfDay()
            }

            AnalyticsTimeFilter.WEEKLY -> {
                startOfWeek()
            }

            AnalyticsTimeFilter.MONTHLY -> {
                startOfMonth()
            }

            AnalyticsTimeFilter.YEARLY -> {
                startOfYear()
            }

            AnalyticsTimeFilter.ALL_TIME -> {
                0L
            }
        }
    }

    private fun startOfDay(): Long {
        return Calendar
            .getInstance()
            .apply {
                setStartOfDay()
            }
            .timeInMillis
    }

    private fun startOfWeek(): Long {
        return Calendar
            .getInstance()
            .apply {
                set(
                    Calendar.DAY_OF_WEEK,
                    firstDayOfWeek,
                )

                setStartOfDay()
            }
            .timeInMillis
    }

    private fun startOfMonth(): Long {
        return Calendar
            .getInstance()
            .apply {
                set(
                    Calendar.DAY_OF_MONTH,
                    1,
                )

                setStartOfDay()
            }
            .timeInMillis
    }

    private fun startOfYear(): Long {
        return Calendar
            .getInstance()
            .apply {
                set(
                    Calendar.DAY_OF_YEAR,
                    1,
                )

                setStartOfDay()
            }
            .timeInMillis
    }

    private fun Calendar.setStartOfDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}