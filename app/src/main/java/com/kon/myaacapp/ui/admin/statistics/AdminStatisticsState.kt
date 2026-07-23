package com.kon.myaacapp.ui.admin.statistics

import androidx.compose.runtime.Immutable
import com.kon.myaacapp.domain.model.AnalyticsTimeFilter
import com.kon.myaacapp.domain.model.CombinedTile

@Immutable
data class AdminStatisticsState(
    val allTiles: List<CombinedTile> = emptyList(),
    val allCategories: List<CombinedTile> = emptyList(),

    val selectedFilter: AnalyticsTimeFilter =
        AnalyticsTimeFilter.ALL_TIME,

    val totalClicks: Int = 0,

    val topWords: List<StatisticsTopWord> =
        emptyList(),

    val categoryBreakdown:
    List<StatisticsCategoryUsage> =
        emptyList(),

    val chartData: List<StatisticsChartPoint> =
        emptyList(),

    val activeDays: Int = 0,
    val estimatedActiveMinutes: Int = 0,

    val insight: StatisticsInsight =
        StatisticsInsight.GENERAL,

    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@Immutable
data class StatisticsTopWord(
    val tileId: String,
    val label: String,
    val clickCount: Int,
)

@Immutable
data class StatisticsCategoryUsage(
    val label: String,
    val clickCount: Int,
)

@Immutable
data class StatisticsChartPoint(
    val label: String,
    val clickCount: Int,
)

enum class StatisticsInsight {
    FOOD,
    REQUESTS,
    GENERAL,
}