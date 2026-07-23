package com.kon.myaacapp.ui.admin.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kon.myaacapp.R
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileType

enum class MediaFilter { MISSING_AUDIO, MISSING_TTS, MISSING_IMAGE }
enum class UsageFilter { LOW_USAGE, HIDDEN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminListView(
    state: AdminListState,
    onAction: (AdminListAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 12.dp,
                    ),
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { value ->
                            onAction(
                                AdminListAction
                                    .SearchQueryChanged(value)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                stringResource(
                                    R.string
                                        .search_tiles_placeholder
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector =
                                    Icons.Default.Search,
                                contentDescription = null,
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceVariant
                                        .copy(alpha = 0.5f),

                                focusedContainerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceVariant
                                        .copy(alpha = 0.8f),

                                unfocusedBorderColor =
                                    Color.Transparent,

                                focusedBorderColor =
                                    MaterialTheme
                                        .colorScheme
                                        .primary,
                            ),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween,
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                onAction(
                                    AdminListAction
                                        .OpenFilterSheet
                                )
                            },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor =
                                        if (
                                            state.hasActiveFilters
                                        ) {
                                            MaterialTheme
                                                .colorScheme
                                                .primary
                                        } else {
                                            MaterialTheme
                                                .colorScheme
                                                .primaryContainer
                                        },

                                    contentColor =
                                        if (
                                            state.hasActiveFilters
                                        ) {
                                            MaterialTheme
                                                .colorScheme
                                                .onPrimary
                                        } else {
                                            MaterialTheme
                                                .colorScheme
                                                .onPrimaryContainer
                                        },
                                ),
                            shape =
                                RoundedCornerShape(100.dp),
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Default.FilterList,
                                contentDescription = null,
                                modifier =
                                    Modifier.size(18.dp),
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(8.dp)
                            )

                            Text(
                                text =
                                    if (
                                        state.activeFilterCount > 0
                                    ) {
                                        stringResource(
                                            R.string
                                                .filter_by_count,
                                            state.activeFilterCount,
                                        )
                                    } else {
                                        stringResource(
                                            R.string.filter_by
                                        )
                                    }
                            )
                        }

                        Surface(
                            shape =
                                RoundedCornerShape(100.dp),
                            color = MaterialTheme
                                .colorScheme
                                .surfaceVariant,
                            modifier = Modifier.clip(
                                RoundedCornerShape(100.dp)
                            ),
                        ) {
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 8.dp,
                                    top = 4.dp,
                                    bottom = 4.dp,
                                ),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.filter_active
                                    ),
                                    style =
                                        MaterialTheme
                                            .typography
                                            .labelLarge,
                                )

                                Spacer(
                                    modifier =
                                        Modifier.width(8.dp)
                                )

                                Switch(
                                    checked =
                                        state.isFilterActive,
                                    onCheckedChange = { value ->
                                        onAction(
                                            AdminListAction
                                                .FilterActiveChanged(
                                                    value
                                                )
                                        )
                                    },
                                    modifier =
                                        Modifier.scale(0.8f),
                                )
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 80.dp,
                ),
                verticalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = state.filteredTiles,
                    key = { tile ->
                        "${tile.id}_${tile.parentId}"
                    },
                ) { tile ->
                    AdminListRowItem(
                        tile = tile,
                        onEdit = {
                            onAction(
                                AdminListAction
                                    .EditTileClicked(tile)
                            )
                        },
                        onDelete = {
                            onAction(
                                AdminListAction
                                    .DeleteTileClicked(tile)
                            )
                        },
                        onQuickRecord = {
                            onAction(
                                AdminListAction
                                    .QuickRecordClicked(tile)
                            )
                        },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                onAction(
                    AdminListAction.AddTileClicked
                )
            },
            containerColor =
                MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription =
                    stringResource(R.string.add),
                tint = Color.White,
            )
        }
    }

    if (state.showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                onAction(
                    AdminListAction.CloseFilterSheet
                )
            },
            sheetState = sheetState,
            containerColor =
                MaterialTheme.colorScheme.surface,
        ) {
            val density = LocalDensity.current
            val windowInfo = LocalWindowInfo.current

            val maxScrollHeight =
                remember(density, windowInfo) {
                    with(density) {
                        (
                                windowInfo
                                    .containerSize
                                    .height * 0.65f
                                ).toDp()
                    }
                }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.filter_by
                    ),
                    style =
                        MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier.padding(bottom = 16.dp),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxScrollHeight)
                        .verticalScroll(
                            rememberScrollState()
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(16.dp),
                ) {
                    Column {
                        Text(
                            text = stringResource(
                                R.string
                                    .filter_section_media
                            ),
                            style = MaterialTheme
                                .typography
                                .labelLarge,
                            color = MaterialTheme
                                .colorScheme
                                .primary,
                            modifier = Modifier.padding(
                                bottom = 8.dp
                            ),
                        )

                        FilterCheckboxRow(
                            label = stringResource(
                                R.string
                                    .filter_missing_audio
                            ),
                            isChecked =
                                MediaFilter.MISSING_AUDIO in
                                        state
                                            .selectedMediaFilters,
                            onCheckedChange = { checked ->
                                onAction(
                                    AdminListAction
                                        .MediaFilterChanged(
                                            filter =
                                                MediaFilter
                                                    .MISSING_AUDIO,
                                            isSelected = checked,
                                        )
                                )
                            },
                        )

                        FilterCheckboxRow(
                            label = stringResource(
                                R.string
                                    .filter_missing_tts
                            ),
                            isChecked =
                                MediaFilter.MISSING_TTS in
                                        state
                                            .selectedMediaFilters,
                            onCheckedChange = { checked ->
                                onAction(
                                    AdminListAction
                                        .MediaFilterChanged(
                                            filter =
                                                MediaFilter
                                                    .MISSING_TTS,
                                            isSelected = checked,
                                        )
                                )
                            },
                        )

                        FilterCheckboxRow(
                            label = stringResource(
                                R.string
                                    .filter_missing_image
                            ),
                            isChecked =
                                MediaFilter.MISSING_IMAGE in
                                        state
                                            .selectedMediaFilters,
                            onCheckedChange = { checked ->
                                onAction(
                                    AdminListAction
                                        .MediaFilterChanged(
                                            filter =
                                                MediaFilter
                                                    .MISSING_IMAGE,
                                            isSelected = checked,
                                        )
                                )
                            },
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme
                            .colorScheme
                            .outlineVariant
                            .copy(alpha = 0.5f)
                    )

                    Column {
                        Text(
                            text = stringResource(
                                R.string
                                    .filter_section_type
                            ),
                            style = MaterialTheme
                                .typography
                                .labelLarge,
                            color = MaterialTheme
                                .colorScheme
                                .primary,
                            modifier = Modifier.padding(
                                bottom = 8.dp
                            ),
                        )

                        TileType.entries.forEach { type ->
                            val labelResource =
                                when (type) {
                                    TileType.BASIC -> {
                                        R.string
                                            .tile_type_basic
                                    }

                                    TileType.FOLDER -> {
                                        R.string
                                            .tile_type_folder
                                    }

                                    TileType.CONNECTOR -> {
                                        R.string
                                            .tile_type_connector
                                    }

                                    TileType.QUICK_FIRE -> {
                                        R.string
                                            .tile_type_quick_fire
                                    }
                                }

                            FilterCheckboxRow(
                                label = stringResource(
                                    labelResource
                                ),
                                isChecked =
                                    type in
                                            state.selectedTypes,
                                onCheckedChange = {
                                        checked ->
                                    onAction(
                                        AdminListAction
                                            .TileTypeFilterChanged(
                                                tileType =
                                                    type,
                                                isSelected =
                                                    checked,
                                            )
                                    )
                                },
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme
                            .colorScheme
                            .outlineVariant
                            .copy(alpha = 0.5f)
                    )

                    Column {
                        Text(
                            text = stringResource(
                                R.string
                                    .filter_section_usage
                            ),
                            style = MaterialTheme
                                .typography
                                .labelLarge,
                            color = MaterialTheme
                                .colorScheme
                                .primary,
                            modifier = Modifier.padding(
                                bottom = 8.dp
                            ),
                        )

                        FilterCheckboxRow(
                            label = stringResource(
                                R.string.filter_usage_low
                            ),
                            isChecked =
                                UsageFilter.LOW_USAGE in
                                        state
                                            .selectedUsageFilters,
                            onCheckedChange = { checked ->
                                onAction(
                                    AdminListAction
                                        .UsageFilterChanged(
                                            filter =
                                                UsageFilter
                                                    .LOW_USAGE,
                                            isSelected = checked,
                                        )
                                )
                            },
                        )

                        FilterCheckboxRow(
                            label = stringResource(
                                R.string
                                    .filter_usage_hidden
                            ),
                            isChecked =
                                UsageFilter.HIDDEN in
                                        state
                                            .selectedUsageFilters,
                            onCheckedChange = { checked ->
                                onAction(
                                    AdminListAction
                                        .UsageFilterChanged(
                                            filter =
                                                UsageFilter
                                                    .HIDDEN,
                                            isSelected = checked,
                                        )
                                )
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            onAction(
                                AdminListAction.ClearFilters
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.clear_action
                            )
                        )
                    }

                    Button(
                        onClick = {
                            onAction(
                                AdminListAction
                                    .CloseFilterSheet
                            )
                        },
                        modifier = Modifier.weight(1.5f),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.filter_apply
                            )
                        )
                    }
                }
            }
        }
    }

    state.quickRecordTile?.let { tile ->
        AlertDialog(
            onDismissRequest = {
                onAction(
                    AdminListAction
                        .CancelRecordingClicked
                )
            },
            title = {
                Text(
                    text =
                        "הקלטה מהירה עבור: ${tile.label}",
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                    verticalArrangement =
                        Arrangement.spacedBy(16.dp),
                    modifier =
                        Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                            if (state.isRecording) {
                                "מקליט כעת... לחץ לעצירה"
                            } else {
                                "לחץ על המיקרופון כדי להתחיל להקליט"
                            },
                        style =
                            MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )

                    IconButton(
                        onClick = {
                            if (state.isRecording) {
                                onAction(
                                    AdminListAction
                                        .StopRecordingClicked
                                )
                            } else {
                                onAction(
                                    AdminListAction
                                        .QuickRecordClicked(
                                            tile
                                        )
                                )
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                color =
                                    if (state.isRecording) {
                                        MaterialTheme
                                            .colorScheme
                                            .errorContainer
                                    } else {
                                        MaterialTheme
                                            .colorScheme
                                            .primaryContainer
                                    },
                                shape = CircleShape,
                            ),
                    ) {
                        Icon(
                            imageVector =
                                if (state.isRecording) {
                                    Icons.Default.Delete
                                } else {
                                    Icons.Default.Mic
                                },
                            contentDescription =
                                if (state.isRecording) {
                                    "עצור הקלטה"
                                } else {
                                    "התחל להקליט"
                                },
                            tint =
                                if (state.isRecording) {
                                    MaterialTheme
                                        .colorScheme
                                        .error
                                } else {
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                },
                            modifier =
                                Modifier.size(36.dp),
                        )
                    }

                    if (
                        state.temporaryAudioPath != null &&
                        !state.isRecording
                    ) {
                        Button(
                            onClick = {
                                onAction(
                                    AdminListAction
                                        .PreviewRecordingClicked
                                )
                            },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor =
                                        MaterialTheme
                                            .colorScheme
                                            .secondaryContainer
                                ),
                        ) {
                            Text(
                                text = "השמע בדיקה",
                                color = MaterialTheme
                                    .colorScheme
                                    .onSecondaryContainer,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAction(
                            AdminListAction
                                .SaveRecordingClicked
                        )
                    },
                    enabled = state.canSaveRecording,
                ) {
                    Text("שמור הקלטה")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onAction(
                            AdminListAction
                                .CancelRecordingClicked
                        )
                    },
                    enabled =
                        !state.isSavingRecording,
                ) {
                    Text("ביטול")
                }
            },
        )
    }
}

@Composable
fun FilterCheckboxRow(label: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val onRowClick = remember(isChecked, onCheckedChange) { { onCheckedChange(!isChecked) } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onRowClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isChecked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun AdminListRowItem(
    tile: CombinedTile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onQuickRecord: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onQuickRecord) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Quick Record",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Text(
                    text = tile.definition.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (tile.definition.ttsText.isNotBlank() && tile.definition.ttsText != tile.definition.label) {
                    Text(
                        text = tile.definition.ttsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (tile.definition.imageUri != null) {
                    AsyncImage(
                        model = tile.definition.imageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(text = tile.definition.emoji ?: "✨", fontSize = 28.sp)
                }
            }
        }
    }
}