package com.kon.myaacapp.ui.admin.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kon.myaacapp.R
import com.kon.myaacapp.domain.model.AnalyticsTimeFilter
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileDefinition
import com.kon.myaacapp.domain.model.TileLayoutState
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.ui.theme.MyAACAppTheme

@Composable
fun AdminStatisticsScreen(
    state: AdminStatisticsState,
    onAction: (AdminStatisticsAction) -> Unit,
) {
    AdminStatisticsScreenContent(
        state = state,
        onAction = onAction,
    )
}

@Composable
fun AdminStatisticsScreenContent(
    state: AdminStatisticsState,
    onAction: (AdminStatisticsAction) -> Unit,
) {
    val insightResource = when (state.insight) {
        StatisticsInsight.FOOD -> {
            R.string.insight_food
        }

        StatisticsInsight.REQUESTS -> {
            R.string.insight_requests
        }

        StatisticsInsight.GENERAL -> {
            R.string.insight_general
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(
                top = 8.dp,
                bottom = 16.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(16.dp),
    ) {
        TimeFilterSelector(
            selectedFilter = state.selectedFilter,
            onFilterSelected = { filter ->
                onAction(
                    AdminStatisticsAction.SelectFilter(
                        filter = filter,
                    )
                )
            },
        )

        OverviewBentoGrid(
            totalClicks = state.totalClicks,
            activeDays = state.activeDays,
            estimatedActiveMinutes =
                state.estimatedActiveMinutes,
        )

        TopWordsChart(
            topWords = state.topWords,
        )

        CategoryPieChart(
            breakdown = state.categoryBreakdown,
        )

        DynamicTrendsChart(
            chartData = state.chartData,
            filter = state.selectedFilter,
        )

        InsightCard(
            insightResId = insightResource,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeFilterSelector(
    selectedFilter: AnalyticsTimeFilter,
    onFilterSelected: (AnalyticsTimeFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AnalyticsTimeFilter.entries.forEach { filter ->
            val selected = selectedFilter == filter
            val label = when (filter) {
                AnalyticsTimeFilter.DAILY -> stringResource(R.string.filter_daily)
                AnalyticsTimeFilter.WEEKLY -> stringResource(R.string.filter_weekly)
                AnalyticsTimeFilter.MONTHLY -> stringResource(R.string.filter_monthly)
                AnalyticsTimeFilter.YEARLY -> stringResource(R.string.filter_yearly)
                AnalyticsTimeFilter.ALL_TIME -> stringResource(R.string.filter_all_time)
            }

            FilterChip(
                selected = selected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
fun OverviewBentoGrid(
    totalClicks: Int,
    activeDays: Int,
    estimatedActiveMinutes: Int,
) {
    var infoDialogContent by remember {
        mutableStateOf<Pair<String, String>?>(null)
    }

    infoDialogContent?.let { dialogContent ->
        AlertDialog(
            onDismissRequest = {
                infoDialogContent = null
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        infoDialogContent = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.ok)
                    )
                }
            },
            title = {
                Text(text = dialogContent.first)
            },
            text = {
                Text(text = dialogContent.second)
            },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(12.dp),
    ) {
        MetricCard(
            label = stringResource(
                R.string.total_words_count
            ),
            value = totalClicks.toString(),
            color =
                MaterialTheme.colorScheme.primaryContainer,
            onInfoClick = {
                infoDialogContent =
                    "סה\"כ מילים" to
                            "סך כל הפעמים שהמשתמש לחץ על מילים להשמעה."
            },
            modifier = Modifier.weight(1f),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                label = stringResource(
                    R.string.avg_time_per_session
                ),
                value = "${estimatedActiveMinutes}m",
                color = MaterialTheme
                    .colorScheme
                    .secondaryContainer,
                onInfoClick = {
                    infoDialogContent =
                        "זמן ממוצע" to
                                "זמן משוער שהאפליקציה הייתה פתוחה ופעילה."
                },
            )

            MetricCard(
                label = stringResource(
                    R.string.active_sessions
                ),
                value = activeDays.toString(),
                color = MaterialTheme
                    .colorScheme
                    .tertiaryContainer,
                onInfoClick = {
                    infoDialogContent =
                        "סשנים פעילים" to
                                "מספר הימים שבהם תועד שימוש באפליקציה."
                },
            )
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    color: Color,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier
                        .offset(x = 8.dp, y = (-8).dp)
                        .size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TopWordsChart(
    topWords: List<StatisticsTopWord>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.top_words_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            val maxClicks = topWords.firstOrNull()?.clickCount ?: 1

            topWords.forEach { tile ->
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(tile.label, fontWeight = FontWeight.Medium)
                        Text(
                            tile.clickCount.toString(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (maxClicks > 0) tile.clickCount.toFloat() / maxClicks.toFloat() else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryPieChart(
    breakdown: List<StatisticsCategoryUsage>,
) {
    val total = breakdown
        .sumOf { item ->
            item.clickCount
        }
        .toFloat()
        .coerceAtLeast(1f)

    val colors = listOf(
        Color(0xFF6200EE),
        Color(0xFF03DAC6),
        Color(0xFFBB86FC),
        Color(0xFF018786),
        Color(0xFF3700B3),
        Color(0xFFFF0266),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.category_usage_title
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceEvenly,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(120.dp),
                ) {
                    Canvas(
                        modifier = Modifier.size(120.dp),
                    ) {
                        var startAngle = -90f

                        breakdown.forEachIndexed {
                                index,
                                item ->

                            val sweepAngle =
                                (
                                        item.clickCount
                                            .toFloat() /
                                                total
                                        ) * 360f

                            drawArc(
                                color =
                                    colors[index % colors.size],
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(
                                    width = 24.dp.toPx(),
                                    cap = StrokeCap.Round,
                                ),
                            )

                            startAngle += sweepAngle
                        }
                    }

                    Icon(
                        imageVector =
                            Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    )
                }

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {
                    breakdown.forEachIndexed {
                            index,
                            item ->

                        val percentage =
                            (
                                    (
                                            item.clickCount
                                                .toFloat() /
                                                    total
                                            ) * 100f
                                    ).toInt()

                        val label =
                            if (item.label == "Other") {
                                stringResource(
                                    R.string.other_category
                                )
                            } else {
                                item.label
                            }

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            2.dp
                                        )
                                    )
                                    .background(
                                        colors[
                                            index %
                                                    colors.size
                                        ]
                                    ),
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(8.dp)
                            )

                            Text(
                                text =
                                    "$label ($percentage%)",
                                style = MaterialTheme
                                    .typography
                                    .labelSmall,
                                maxLines = 1,
                                overflow =
                                    TextOverflow.Ellipsis,
                                modifier =
                                    Modifier.widthIn(
                                        max = 120.dp
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DynamicTrendsChart(
    chartData: List<StatisticsChartPoint>,
    filter: AnalyticsTimeFilter,
) {
    val maxValue = chartData
        .maxOfOrNull { point ->
            point.clickCount
        }
        ?.toFloat()
        ?.coerceAtLeast(1f)
        ?: 1f

    val title = when (filter) {
        AnalyticsTimeFilter.DAILY -> {
            stringResource(
                R.string.daily_usage_title
            )
        }

        AnalyticsTimeFilter.WEEKLY -> {
            stringResource(
                R.string.weekly_usage_title
            )
        }

        AnalyticsTimeFilter.MONTHLY -> {
            stringResource(
                R.string.monthly_usage_title
            )
        }

        AnalyticsTimeFilter.YEARLY -> {
            stringResource(
                R.string.yearly_usage_title
            )
        }

        AnalyticsTimeFilter.ALL_TIME -> {
            stringResource(
                R.string.all_time_usage_title
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Icon(
                    imageVector =
                        Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint =
                        MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            val isDaily =
                filter == AnalyticsTimeFilter.DAILY

            val barWidth =
                if (isDaily) {
                    40.dp
                } else {
                    0.dp
                }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .then(
                        if (isDaily) {
                            Modifier.horizontalScroll(
                                rememberScrollState()
                            )
                        } else {
                            Modifier
                        }
                    ),
                horizontalArrangement =
                    if (isDaily) {
                        Arrangement.spacedBy(8.dp)
                    } else {
                        Arrangement.SpaceBetween
                    },
                verticalAlignment = Alignment.Bottom,
            ) {
                chartData.forEachIndexed {
                        index,
                        point ->

                    val barHeightFraction =
                        (
                                point.clickCount.toFloat() /
                                        maxValue
                                ).coerceIn(
                                minimumValue = 0f,
                                maximumValue = 1f,
                            )

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally,
                        modifier =
                            if (isDaily) {
                                Modifier.width(barWidth)
                            } else {
                                Modifier.weight(1f)
                            },
                    ) {
                        Text(
                            text =
                                point.clickCount.toString(),
                            style = MaterialTheme
                                .typography
                                .labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme
                                .colorScheme
                                .primary,
                            fontSize = 10.sp,
                        )

                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )

                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .fillMaxHeight(
                                    fraction =
                                        barHeightFraction *
                                                0.8f
                                )
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 8.dp,
                                        topEnd = 8.dp,
                                    )
                                )
                                .background(
                                    if (
                                        index ==
                                        chartData.lastIndex
                                    ) {
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                    } else {
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                            .copy(alpha = 0.3f)
                                    }
                                ),
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Text(
                            text = point.label,
                            style = MaterialTheme
                                .typography
                                .labelSmall,
                            color = MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InsightCard(insightResId: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.ai_insight_title), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(insightResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AdminStatisticsScreenPreview() {
    val mockTiles = listOf(
        createStatisticsPreviewTile(
            id = "1",
            label = "שלום",
            clickCount = 10,
            cellIndex = 0,
        ),
        createStatisticsPreviewTile(
            id = "2",
            label = "אני רוצה",
            clickCount = 8,
            cellIndex = 1,
        ),
        createStatisticsPreviewTile(
            id = "3",
            label = "תפוח",
            clickCount = 5,
            parentId = "cat1",
            cellIndex = 0,
        ),
        createStatisticsPreviewTile(
            id = "4",
            label = "שתייה",
            clickCount = 3,
            parentId = "cat1",
            cellIndex = 1,
        ),
        createStatisticsPreviewTile(
            id = "5",
            label = "לשחק",
            clickCount = 2,
            parentId = "cat2",
            cellIndex = 0,
        ),
        createStatisticsPreviewTile(
            id = "cat1",
            label = "אוכל",
            tileType = TileType.FOLDER,
            cellIndex = 2,
        ),
        createStatisticsPreviewTile(
            id = "cat2",
            label = "משחקים",
            tileType = TileType.FOLDER,
            cellIndex = 3,
        ),
    )
    val mockCategories = listOf(
        createStatisticsPreviewTile(
            id = "cat1",
            label = "אוכל",
            tileType = TileType.FOLDER,
            cellIndex = 0,
        ),
        createStatisticsPreviewTile(
            id = "cat2",
            label = "משחקים",
            tileType = TileType.FOLDER,
            cellIndex = 1,
        ),
    )

    MyAACAppTheme {
        AdminStatisticsScreen(
            state = AdminStatisticsState(
                allTiles = mockTiles,
                allCategories = mockCategories,
                selectedFilter =
                    AnalyticsTimeFilter.WEEKLY,
                totalClicks = 3,
                topWords = listOf(
                    StatisticsTopWord(
                        tileId = "1",
                        label = "שלום",
                        clickCount = 2,
                    ),
                    StatisticsTopWord(
                        tileId = "2",
                        label = "אני רוצה",
                        clickCount = 1,
                    ),
                ),
                categoryBreakdown = listOf(
                    StatisticsCategoryUsage(
                        label = "Root",
                        clickCount = 3,
                    ),
                ),
                chartData = listOf(
                    StatisticsChartPoint(
                        label = "א'",
                        clickCount = 1,
                    ),
                    StatisticsChartPoint(
                        label = "ב'",
                        clickCount = 2,
                    ),
                ),
                activeDays = 1,
                estimatedActiveMinutes = 2,
                insight =
                    StatisticsInsight.REQUESTS,
            ),
            onAction = {},
        )
    }
}

private fun createStatisticsPreviewTile(
    id: String,
    label: String,
    clickCount: Int = 0,
    tileType: TileType = TileType.BASIC,
    parentId: String? = null,
    cellIndex: Int = 0,
): CombinedTile {
    return CombinedTile(
        definition = TileDefinition(
            id = id,
            label = label,
            ttsText = label,
            isCategory = tileType == TileType.FOLDER,
            type = tileType,
            languageCode = "he",
            defaultParentId = parentId,
            defaultCellIndex = cellIndex,
        ),
        layoutState = TileLayoutState(
            tileId = id,
            parentId = parentId,
            linkedCategoryId = null,
            cellIndex = cellIndex,
            isQuickFire = tileType == TileType.QUICK_FIRE,
            isHidden = false,
            clickCount = clickCount,
        ),
    )
}