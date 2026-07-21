package com.kon.myaacapp.ui.admin.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kon.myaacapp.AACViewModel
import com.kon.myaacapp.R
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.ui.communication.ActionBar
import com.kon.myaacapp.ui.communication.BottomHomeBar
import com.kon.myaacapp.ui.communication.TileUI

@Composable
fun AdminLayoutSettingsScreen(viewModel: AACViewModel) {
    val gridColumns by viewModel.gridColumns.collectAsState()
    val gridRows by viewModel.gridRows.collectAsState()
    val gridTileScale by viewModel.gridTileScale.collectAsState()
    val barTileImageScale by viewModel.barTileImageScale.collectAsState()
    val barTileTitleScale by viewModel.barTileTitleScale.collectAsState()
    val actionButtonScale by viewModel.actionButtonScale.collectAsState()

    val showSentenceBar by viewModel.showSentenceBar.collectAsState()
    val showBackButton by viewModel.showBackButton.collectAsState()
    val showBackspaceButton by viewModel.showBackspaceButton.collectAsState()
    val showSpeakButton by viewModel.showSpeakButton.collectAsState()
    val homeInActionBar by viewModel.homeInActionBar.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.layout_settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        LayoutPreview(
            gridColumns = gridColumns,
            gridRows = gridRows,
            gridTileScale = gridTileScale,
            barTileImageScale = barTileImageScale,
            barTileTitleScale = barTileTitleScale,
            actionButtonScale = actionButtonScale,
            showSentenceBar = showSentenceBar,
            showBackButton = showBackButton,
            showBackspaceButton = showBackspaceButton,
            showSpeakButton = showSpeakButton,
            homeInActionBar = homeInActionBar
        )

        HorizontalDivider()

        SettingIntSlider(
            label = stringResource(R.string.setting_grid_columns),
            value = gridColumns,
            onValueChange = { viewModel.updateGridColumns(it) },
            valueRange = 1..8
        )
        SettingIntSlider(
            label = stringResource(R.string.setting_grid_rows),
            value = gridRows,
            onValueChange = { viewModel.updateGridRows(it) },
            valueRange = 1..10
        )

        SettingSlider(
            label = stringResource(R.string.setting_grid_tile_size),
            value = gridTileScale,
            onValueChange = { viewModel.updateGridTileScale(it) },
            valueRange = 0.5f..2.0f
        )

        // 👉 Wrap the sentence bar sliders so they hide when the bar is disabled
        AnimatedVisibility(visible = showSentenceBar) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingSlider(
                    label = stringResource(R.string.setting_bar_image_size),
                    value = barTileImageScale,
                    onValueChange = { viewModel.updateBarTileImageScale(it) },
                    valueRange = 0.5f..2.0f
                )
                SettingSlider(
                    label = stringResource(R.string.setting_bar_text_size),
                    value = barTileTitleScale,
                    onValueChange = { viewModel.updateBarTileTitleScale(it) },
                    valueRange = 0.5f..2.0f
                )
            }
        }

        SettingSlider(
            label = stringResource(R.string.setting_action_button_size),
            value = actionButtonScale,
            onValueChange = { viewModel.updateActionButtonScale(it) },
            valueRange = 0.5f..2.0f
        )

        HorizontalDivider()

        SettingToggle(
            title = stringResource(R.string.setting_show_sentence_bar),
            description = stringResource(R.string.setting_show_sentence_bar_desc),
            checked = showSentenceBar,
            onCheckedChange = { viewModel.updateShowSentenceBar(it) }
        )

        SettingToggle(
            title = stringResource(R.string.setting_show_back),
            description = stringResource(R.string.setting_show_back_desc),
            checked = showBackButton,
            onCheckedChange = { viewModel.updateShowBackButton(it) }
        )

        AnimatedVisibility(visible = showSentenceBar) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingToggle(
                    title = stringResource(R.string.setting_show_backspace),
                    description = stringResource(R.string.setting_show_backspace_desc),
                    checked = showBackspaceButton,
                    onCheckedChange = { viewModel.updateShowBackspaceButton(it) }
                )
                SettingToggle(
                    title = stringResource(R.string.setting_show_speak),
                    description = stringResource(R.string.setting_show_speak_desc),
                    checked = showSpeakButton,
                    onCheckedChange = { viewModel.updateShowSpeakButton(it) }
                )
            }
        }

        SettingToggle(
            title = stringResource(R.string.setting_home_in_action_bar),
            description = stringResource(R.string.setting_home_in_action_bar_desc),
            checked = homeInActionBar,
            onCheckedChange = { viewModel.updateHomeInActionBar(it) }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingIntSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontWeight = FontWeight.Medium)
            Text(text = value.toString())
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontWeight = FontWeight.Medium)
            Text(text = "${(value * 100).toInt()}%")
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun LayoutPreview(
    gridColumns: Int,
    gridRows: Int,
    gridTileScale: Float,
    barTileImageScale: Float,
    barTileTitleScale: Float,
    actionButtonScale: Float,
    showSentenceBar: Boolean,
    showBackButton: Boolean,
    showBackspaceButton: Boolean,
    showSpeakButton: Boolean,
    homeInActionBar: Boolean
) {
    val previewGridHeight = (gridRows * 76).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E24), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "תצוגה מקדימה (Preview)",
            style = MaterialTheme.typography.labelMedium,
            color = Color.LightGray,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (showSentenceBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier.size((86 * barTileImageScale).dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF59D))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(text = "👋", fontSize = (24 * barTileImageScale).sp)
                        }
                        Text(
                            text = "שלום",
                            fontSize = (9 * barTileTitleScale).sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = Color.Black
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { },
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        ActionBar(
            onSpeak = {}, onClear = {}, onBackspace = {}, onBack = {}, onSettingsClick = {}, onHomeClick = {},
            showSettingsFallback = !showSentenceBar, canGoBack = true, scale = actionButtonScale,
            showClearButton = showSentenceBar,
            showBackButton = showBackButton,
            showBackspaceButton = showSentenceBar && showBackspaceButton,
            showSpeakButton = showSentenceBar && showSpeakButton,
            homeInActionBar = homeInActionBar,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewGridHeight),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val mockData = listOf(
                Triple("כן", "👍", Color(0xFFF48FB1)),
                Triple("לא", "👎", Color(0xFFF48FB1)),
                Triple("תיקיה", "📁", Color(0xFFFFCC80)),
                Triple("עוד", "➕", Color(0xFF90CAF9)),
                Triple("תודה", "🙏", Color(0xFFF48FB1)),
                Triple("כואב", "🤕", Color(0xFF90CAF9)),
                Triple("לעזור", "🤝", Color(0xFFA5D6A7)),
                Triple("סיימתי", "✅", Color(0xFFF48FB1))
            )

            for (r in 0 until gridRows) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (c in 0 until gridColumns) {
                        val dataIndex = (r * gridColumns + c) % mockData.size
                        val data = mockData[dataIndex]
                        val type = if (data.first == "תיקיה") TileType.FOLDER else TileType.BASIC

                        TileUI(
                            label = data.first,
                            imageUri = null,
                            emoji = data.second,
                            backgroundColor = data.third,
                            tileType = type,
                            scale = gridTileScale,
                            onClick = {},
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!homeInActionBar) {
            BottomHomeBar(
                onHomeClick = {},
                scale = actionButtonScale
            )
        }
    }
}