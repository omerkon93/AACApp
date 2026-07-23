package com.kon.myaacapp.ui.admin.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kon.myaacapp.R
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.ui.communication.TileUI

@Composable
fun AdminLayoutSettingsScreen(
    state: LayoutSettingsState,
    onAction: (LayoutSettingsAction) -> Unit,
) {
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
            gridTileScale = state.safeGridTileScale,
            gridTileContainerScale =
                state.safeGridTileContainerScale,
            actionButtonScale =
                state.safeActionButtonScale,
            showSentenceBar = state.showSentenceBar,
        )

        HorizontalDivider()

        SettingIntSlider(
            label = stringResource(
                R.string.setting_grid_columns
            ),
            value = state.safeGridColumns,
            onValueChange = { value ->
                onAction(
                    LayoutSettingsAction.GridColumnsChanged(
                        value
                    )
                )
            },
            valueRange = 1..8,
        )

        SettingIntSlider(
            label = stringResource(
                R.string.setting_grid_rows
            ),
            value = state.safeGridRows,
            onValueChange = { value ->
                onAction(
                    LayoutSettingsAction.GridRowsChanged(
                        value
                    )
                )
            },
            valueRange = 1..10,
        )

        SettingSlider(
            label = stringResource(
                R.string.setting_grid_tile_container_size
            ),
            value = state.safeGridTileContainerScale,
            onValueChange = { value ->
                onAction(
                    LayoutSettingsAction
                        .GridTileContainerScaleChanged(
                            value
                        )
                )
            },
            valueRange = 0.5f..1f,
        )

        SettingSlider(
            label = stringResource(
                R.string.setting_grid_tile_content_size
            ),
            value = state.safeGridTileScale,
            onValueChange = { value ->
                onAction(
                    LayoutSettingsAction
                        .GridTileScaleChanged(value)
                )
            },
            valueRange = 0.5f..2f,
        )

        AnimatedVisibility(
            visible = state.showSentenceBar
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingSlider(
                    label = stringResource(
                        R.string.setting_bar_image_size
                    ),
                    value = state.safeBarTileImageScale,
                    onValueChange = { value ->
                        onAction(
                            LayoutSettingsAction
                                .BarTileImageScaleChanged(value)
                        )
                    },
                    valueRange = 0.5f..2f,
                )
                SettingSlider(
                    label = stringResource(
                        R.string.setting_bar_text_size
                    ),
                    value = state.safeBarTileTitleScale,
                    onValueChange = { value ->
                        onAction(
                            LayoutSettingsAction
                                .BarTileTitleScaleChanged(value)
                        )
                    },
                    valueRange = 0.5f..2f,
                )
            }
        }

        SettingSlider(
            label = stringResource(
                R.string.setting_action_button_size
            ),
            value = state.safeActionButtonScale,
            onValueChange = { value ->
                onAction(
                    LayoutSettingsAction
                        .ActionButtonScaleChanged(value)
                )
            },
            valueRange = 0.5f..2f,
        )

        HorizontalDivider()

        SettingToggle(
            title = stringResource(
                R.string.setting_show_sentence_bar
            ),
            description = stringResource(
                R.string.setting_show_sentence_bar_desc
            ),
            checked = state.showSentenceBar,
            onCheckedChange = { value ->
                onAction(
                    LayoutSettingsAction
                        .ShowSentenceBarChanged(value)
                )
            },
        )

        SettingToggle(
            title = stringResource(
                R.string.setting_show_back
            ),
            description = stringResource(
                R.string.setting_show_back_desc
            ),
            checked = state.showBackButton,
            onCheckedChange = { value ->
                onAction(
                    LayoutSettingsAction
                        .ShowBackButtonChanged(value)
                )
            },
        )

        AnimatedVisibility(
            visible = state.showSentenceBar
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingToggle(
                    title = stringResource(
                        R.string.setting_show_backspace
                    ),
                    description = stringResource(
                        R.string.setting_show_backspace_desc
                    ),
                    checked = state.showBackspaceButton,
                    onCheckedChange = { value ->
                        onAction(
                            LayoutSettingsAction
                                .ShowBackspaceButtonChanged(value)
                        )
                    },
                )
                SettingToggle(
                    title = stringResource(
                        R.string.setting_show_speak
                    ),
                    description = stringResource(
                        R.string.setting_show_speak_desc
                    ),
                    checked = state.showSpeakButton,
                    onCheckedChange = { value ->
                        onAction(
                            LayoutSettingsAction
                                .ShowSpeakButtonChanged(value)
                        )
                    },
                )
            }
        }

        SettingToggle(
            title = stringResource(
                R.string.setting_home_in_action_bar
            ),
            description = stringResource(
                R.string.setting_home_in_action_bar_desc
            ),
            checked = state.homeInActionBar,
            onCheckedChange = { value ->
                onAction(
                    LayoutSettingsAction
                        .HomeInActionBarChanged(value)
                )
            },
        )

        HorizontalDivider()

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    onAction(
                        LayoutSettingsAction
                            .SaveCurrentAsDefault
                    )
                },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        R.string.save_layout_as_default
                    )
                )
            }

            OutlinedButton(
                onClick = {
                    onAction(
                        LayoutSettingsAction.RestoreDefault
                    )
                },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        R.string.restore_default_layout_settings
                    )
                )
            }
        }

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
    gridTileScale: Float,
    gridTileContainerScale: Float,
    actionButtonScale: Float,
    showSentenceBar: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF1E1E24),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "תצוגה מקדימה",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "סרגל משפטים",
                style = MaterialTheme.typography.labelMedium,
                color = Color.LightGray
            )

            if (showSentenceBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(
                            color = Color(0xFF34343C),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF90CAF9)
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "אני",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF48FB1)
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "רוצה",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            color = Color(0xFF34343C),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "מוסתר",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "גודל הכפתור והתמונה",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.LightGray
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TileUI(
                        label = "עוד",
                        imageUri = null,
                        emoji = "➕",
                        backgroundColor = Color(0xFF90CAF9),
                        tileType = TileType.BASIC,
                        scale = gridTileScale,
                        modifier = Modifier.fillMaxSize(
                            fraction = gridTileContainerScale
                        ),
                        onClick = {}
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "גודל כפתור הפעולה",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.LightGray
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth(
                                fraction = actionButtonScale
                                    .coerceIn(0.5f, 1f)
                            )
                            .height((64f * actionButtonScale).dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4F535B)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(
                                (32f * actionButtonScale).dp
                            )
                        )
                    }
                }
            }
        }
    }
}