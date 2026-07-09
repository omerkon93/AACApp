package com.kon.myaacapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.kon.myaacapp.ui.theme.MyAACAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
fun AdminStatisticsScreen(viewModel: AACViewModel) {
    val allTiles by viewModel.allTiles.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val clickEvents by viewModel.filteredClickEvents.collectAsState(initial = emptyList())
    val selectedFilter by viewModel.selectedTimeFilter.collectAsState()

    AdminStatisticsScreenContent(
        allTiles = allTiles,
        allCategories = allCategories,
        clickEvents = clickEvents,
        selectedFilter = selectedFilter,
        onFilterSelected = { viewModel.setTimeFilter(it) }
    )
}

// Internal data class to hold atomically processed UI state
private data class StatsPayload(
    val totalClicks: Int = 0,
    val topWords: List<CombinedTile> = emptyList(),
    val categoryBreakdown: List<Pair<String, Int>> = emptyList(),
    val chartData: List<Pair<String, Int>> = emptyList(),
    val dynamicInsight: Int = R.string.insight_general
)

@Composable
fun AdminStatisticsScreenContent(
    allTiles: List<CombinedTile>,
    allCategories: List<CombinedTile>,
    clickEvents: List<TileClickEvent>,
    selectedFilter: AnalyticsTimeFilter,
    onFilterSelected: (AnalyticsTimeFilter) -> Unit
) {
    // OPTIMIZATION: produceState pushes all O(N) array processing off the Main Thread.
    // This prevents the screen from freezing when loading thousands of analytics events.
    val payload by produceState(
        initialValue = StatsPayload(),
        allTiles, allCategories, clickEvents, selectedFilter
    ) {
        withContext(Dispatchers.Default) {
            if (clickEvents.isEmpty() || allTiles.isEmpty()) {
                value = StatsPayload()
                return@withContext
            }

            // OPTIMIZATION: Convert lists to Maps to change lookups from O(N) to O(1)
            val tileMap = allTiles.associateBy { it.id }
            val categoryMap = allCategories.associateBy { it.id }

            val clickCounts = clickEvents.groupingBy { it.tileId }.eachCount()
            val totalClicks = clickCounts.values.sum()

            // Calculate Top Words in O(N) using the tileMap
            val topWords = clickCounts.mapNotNull { (tileId, count) ->
                val tile = tileMap[tileId]
                if (tile != null && !tile.isCategory) {
                    tile.copy(layoutState = tile.layoutState.copy(clickCount = count))
                } else null
            }.sortedByDescending { it.clickCount }.take(4)

            // Calculate Category Breakdown
            val catCounts = clickCounts.mapNotNull { (tileId, count) ->
                val tile = tileMap[tileId]
                if (tile != null && !tile.isCategory) {
                    val parentId = tile.parentId
                    val label = if (parentId == null) "Root" else categoryMap[parentId]?.label ?: "Unknown"
                    label to count
                } else null
            }.groupBy({ it.first }, { it.second })
                .map { (label, counts) -> label to counts.sum() }
                .sortedByDescending { it.second }

            val categoryBreakdown = if (catCounts.size > 4) {
                val top4 = catCounts.take(4)
                val othersCount = catCounts.drop(4).sumOf { it.second }
                top4 + ("Other" to othersCount)
            } else {
                catCounts
            }

            // Chart Data Logic
            val chartData = processChartData(clickEvents, selectedFilter)

            // AI Insight Logic
            val topWord = topWords.firstOrNull()
            val topCategory = categoryBreakdown.firstOrNull()
            val dynamicInsight = when {
                topCategory?.first == "אוכל" || topCategory?.first == "Food" -> R.string.insight_food
                topWord?.label?.contains("רוצה") == true || topWord?.label?.contains("want") == true -> R.string.insight_requests
                else -> R.string.insight_general
            }

            value = StatsPayload(totalClicks, topWords, categoryBreakdown, chartData, dynamicInsight)
        }
    }

    // 2. UI Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TimeFilterSelector(selectedFilter = selectedFilter, onFilterSelected = onFilterSelected)

        OverviewBentoGrid(totalClicks = payload.totalClicks, clickEvents = clickEvents)

        TopWordsChart(topWords = payload.topWords)

        CategoryPieChart(breakdown = payload.categoryBreakdown)

        DynamicTrendsChart(chartData = payload.chartData, filter = selectedFilter)

        InsightCard(insightResId = payload.dynamicInsight)
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
fun OverviewBentoGrid(totalClicks: Int, clickEvents: List<TileClickEvent>) {
    var infoDialogContent by remember { mutableStateOf<Pair<String, String>?>(null) }

    // OPTIMIZATION: Offload grouping to background thread.
    val sessionStats by produceState(initialValue = Pair("0m", "0"), clickEvents) {
        withContext(Dispatchers.Default) {
            if (clickEvents.isEmpty()) {
                value = Pair("0m", "0")
            } else {
                val cal = Calendar.getInstance()
                // OPTIMIZATION: distinctBy drops elements instantly, using vastly less heap memory
                // than groupBy (which creates huge duplicate arrays for every day).
                val activeDays = clickEvents.distinctBy {
                    cal.timeInMillis = it.timestamp
                    cal.get(Calendar.DAY_OF_YEAR)
                }.size

                value = Pair("${activeDays * 2}m", activeDays.toString())
            }
        }
    }

    if (infoDialogContent != null) {
        AlertDialog(
            onDismissRequest = { infoDialogContent = null },
            confirmButton = {
                TextButton(onClick = { infoDialogContent = null }) {
                    Text(stringResource(R.string.ok))
                }
            },
            title = { Text(infoDialogContent!!.first) },
            text = { Text(infoDialogContent!!.second) }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricCard(
            label = stringResource(R.string.total_words_count),
            value = totalClicks.toString(),
            color = MaterialTheme.colorScheme.primaryContainer,
            onInfoClick = {
                infoDialogContent = "סה\"כ מילים" to "סך כל הפעמים שהמשתמש לחץ על מילים להשמעה."
            },
            modifier = Modifier.weight(1f)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                label = stringResource(R.string.avg_time_per_session),
                value = sessionStats.first,
                color = MaterialTheme.colorScheme.secondaryContainer,
                onInfoClick = {
                    infoDialogContent = "זמן ממוצע" to "זמן משוער שהאפליקציה הייתה פתוחה ופעילה."
                }
            )
            MetricCard(
                label = stringResource(R.string.active_sessions),
                value = sessionStats.second,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                onInfoClick = {
                    infoDialogContent = "סשנים פעילים" to "מספר הפעמים שהאפליקציה נפתחה לשימוש השבוע."
                }
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
        shape = RoundedCornerShape(16.dp)
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
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TopWordsChart(topWords: List<CombinedTile>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp)
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
                        Text(tile.clickCount.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (maxClicks > 0) tile.clickCount.toFloat() / maxClicks.toFloat() else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryPieChart(breakdown: List<Pair<String, Int>>) {
    val total = breakdown.sumOf { it.second }.toFloat().coerceAtLeast(1f)
    val colors = listOf(
        Color(0xFF6200EE), Color(0xFF03DAC6), Color(0xFFBB86FC),
        Color(0xFF018786), Color(0xFF3700B3), Color(0xFFFF0266)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.category_usage_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                    Canvas(modifier = Modifier.size(120.dp)) {
                        var startAngle = -90f
                        breakdown.forEachIndexed { index, pair ->
                            val sweepAngle = (pair.second / total) * 360f
                            drawArc(
                                color = colors[index % colors.size],
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
                            )
                            startAngle += sweepAngle
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    breakdown.forEachIndexed { index, pair ->
                        val percentage = ((pair.second / total) * 100).toInt()
                        val label = if (pair.first == "Other") stringResource(R.string.other_category) else pair.first
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(colors[index % colors.size])
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$label ($percentage%)",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DynamicTrendsChart(chartData: List<Pair<String, Int>>, filter: AnalyticsTimeFilter) {
    val maxVal = chartData.maxOfOrNull { it.second }?.toFloat()?.coerceAtLeast(1f) ?: 1f
    val title = when (filter) {
        AnalyticsTimeFilter.DAILY -> stringResource(R.string.daily_usage_title)
        AnalyticsTimeFilter.WEEKLY -> stringResource(R.string.weekly_usage_title)
        AnalyticsTimeFilter.MONTHLY -> stringResource(R.string.monthly_usage_title)
        AnalyticsTimeFilter.YEARLY -> stringResource(R.string.yearly_usage_title)
        AnalyticsTimeFilter.ALL_TIME -> stringResource(R.string.all_time_usage_title)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(24.dp))

            val barWidth = if (filter == AnalyticsTimeFilter.DAILY) 40.dp else 0.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .then(if (filter == AnalyticsTimeFilter.DAILY) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                horizontalArrangement = if (filter == AnalyticsTimeFilter.DAILY) Arrangement.spacedBy(8.dp) else Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                chartData.forEachIndexed { index, pair ->
                    val barHeightFraction = pair.second / maxVal
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = if (filter == AnalyticsTimeFilter.DAILY) Modifier.width(barWidth) else Modifier.weight(1f)
                    ) {
                        Text(
                            pair.second.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .fillMaxHeight(barHeightFraction * 0.8f)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    if (index == chartData.size - 1) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            pair.first,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            fontSize = 9.sp
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
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
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

private fun processChartData(events: List<TileClickEvent>, filter: AnalyticsTimeFilter): List<Pair<String, Int>> {
    val cal = Calendar.getInstance()
    return when (filter) {
        AnalyticsTimeFilter.DAILY -> {
            // OPTIMIZATION: groupingBy.eachCount() avoids allocating massive arrays of duplicate
            // objects in memory, returning a simple Map<Int, Int> integer frequency.
            val groups = events.groupingBy {
                cal.timeInMillis = it.timestamp
                cal.get(Calendar.HOUR_OF_DAY)
            }.eachCount()
            (0..23).map { hour -> "$hour:00" to (groups[hour] ?: 0) }
        }
        AnalyticsTimeFilter.WEEKLY -> {
            val days = listOf("א'", "ב'", "ג'", "ד'", "ה'", "ו'", "ש'")
            val groups = events.groupingBy {
                cal.timeInMillis = it.timestamp
                cal.get(Calendar.DAY_OF_WEEK)
            }.eachCount()
            (1..7).map { dow -> days[dow - 1] to (groups[dow] ?: 0) }
        }
        AnalyticsTimeFilter.MONTHLY -> {
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val groups = events.groupingBy {
                cal.timeInMillis = it.timestamp
                cal.get(Calendar.DAY_OF_MONTH)
            }.eachCount()
            (1..maxDay).map { dom -> dom.toString() to (groups[dom] ?: 0) }
        }
        AnalyticsTimeFilter.YEARLY -> {
            val months = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
            val groups = events.groupingBy {
                cal.timeInMillis = it.timestamp
                cal.get(Calendar.MONTH)
            }.eachCount()
            (0..11).map { month -> months[month] to (groups[month] ?: 0) }
        }
        AnalyticsTimeFilter.ALL_TIME -> {
            processChartData(events, AnalyticsTimeFilter.WEEKLY)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AdminStatisticsScreenPreview() {
    val mockTiles = listOf(
        AACTile("1", "שלום", "שלום", clickCount = 10, isCategory = false).toCombinedTile(),
        AACTile("2", "אני רוצה", "אני רוצה", clickCount = 8, isCategory = false).toCombinedTile(),
        AACTile("3", "תפוח", "תפוח", clickCount = 5, isCategory = false, parentId = "cat1").toCombinedTile(),
        AACTile("4", "שתייה", "שתייה", clickCount = 3, isCategory = false, parentId = "cat1").toCombinedTile(),
        AACTile("5", "לשחק", "לשחק", clickCount = 2, isCategory = false, parentId = "cat2").toCombinedTile(),
        AACTile("cat1", "אוכל", "אוכל", isCategory = true).toCombinedTile(),
        AACTile("cat2", "משחקים", "משחקים", isCategory = true).toCombinedTile()
    )
    val mockCategories = listOf(
        AACTile("cat1", "אוכל", "אוכל", isCategory = true).toCombinedTile(),
        AACTile("cat2", "משחקים", "משחקים", isCategory = true).toCombinedTile()
    )
    val mockEvents = listOf(
        TileClickEvent(tileId = "1"),
        TileClickEvent(tileId = "1"),
        TileClickEvent(tileId = "2")
    )
    MyAACAppTheme {
        AdminStatisticsScreenContent(
            allTiles = mockTiles,
            allCategories = mockCategories,
            clickEvents = mockEvents,
            selectedFilter = AnalyticsTimeFilter.WEEKLY,
            onFilterSelected = {}
        )
    }
}