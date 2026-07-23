package com.kon.myaacapp.ui.editor

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.kon.myaacapp.R
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.service.audio.AudioRecordingService
import com.kon.myaacapp.ui.communication.TileUI
import com.kon.myaacapp.ui.editor.components.AdvancedSection
import com.kon.myaacapp.ui.editor.components.AudioSection
import com.kon.myaacapp.ui.editor.components.BasicInfoSection
import com.kon.myaacapp.ui.editor.components.LocalizationSection
import com.kon.myaacapp.ui.editor.components.VisualsSection
import com.kon.myaacapp.ui.theme.MyAACAppTheme
import com.kon.myaacapp.ui.theme.resolveFitzgeraldColor

private const val DEFAULT_MAX_CAPACITY = 15

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileEditDialogContent(
    state: TileEditorState,
    audioService: AudioRecordingService,
    onPlayPreview: (
        ttsText: String,
        audioUri: String?,
    ) -> Unit,
    onAction: (TileEditorAction) -> Unit,
) {
    val typeOptions = mapOf(
        TileType.BASIC to stringResource(
            R.string.tile_type_basic
        ),
        TileType.FOLDER to stringResource(
            R.string.tile_type_folder
        ),
        TileType.CONNECTOR to stringResource(
            R.string.tile_type_connector
        ),
        TileType.QUICK_FIRE to stringResource(
            R.string.tile_type_quick_fire
        ),
    )

    val orientation =
        LocalConfiguration.current.orientation

    val tileColor = remember(
        state.partOfSpeech
    ) {
        resolveFitzgeraldColor(
            state.partOfSpeech
        )
    }

    val previewAspectRatio = remember(
        orientation
    ) {
        if (
            orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        ) {
            1.2f
        } else {
            1f
        }
    }

    BasicAlertDialog(
        onDismissRequest = {
            if (!state.isSubmitting) {
                onAction(
                    TileEditorAction.CancelClicked
                )
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                TileEditorHeader(
                    isNewTile = state.isNewTile,
                    onDismiss = {
                        if (!state.isSubmitting) {
                            onAction(
                                TileEditorAction.CancelClicked
                            )
                        }
                    },
                )

                if (state.showOverwriteDialog) {
                    val occupiedTile =
                        remember(
                            state.tilesInParent,
                            state.pendingCellIndex,
                        ) {
                            state.tilesInParent.find { tile ->
                                tile.cellIndex.toString() ==
                                        state.pendingCellIndex
                            }
                        }

                    AlertDialog(
                        onDismissRequest = {
                            onAction(
                                TileEditorAction
                                    .DismissOccupiedCellDialog
                            )
                        },
                        title = {
                            Text(
                                text = stringResource(
                                    R.string.cell_occupied_title
                                ),
                                textAlign = TextAlign.Center,
                                modifier =
                                    Modifier.fillMaxWidth(),
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(
                                    R.string.cell_occupied_msg,
                                    occupiedTile
                                        ?.label
                                        .orEmpty(),
                                ),
                                textAlign = TextAlign.Center,
                                modifier =
                                    Modifier.fillMaxWidth(),
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    onAction(
                                        TileEditorAction
                                            .ConfirmOccupiedCell
                                    )
                                }
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.ok
                                    )
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    onAction(
                                        TileEditorAction
                                            .DismissOccupiedCellDialog
                                    )
                                }
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.cancel
                                    )
                                )
                            }
                        },
                    )
                }

                LazyColumn(
                    verticalArrangement =
                        Arrangement.spacedBy(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                ) {
                    item(key = "description") {
                        Text(
                            text = stringResource(
                                R.string.edit_tile_desc
                            ),
                            style =
                                MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        )
                    }

                    item(key = "basic-info") {
                        BasicInfoSection(
                            label = state.label,
                            onLabelChange = { value ->
                                onAction(
                                    TileEditorAction
                                        .LabelChanged(value)
                                )
                            },
                            ttsText = state.ttsText,
                            onTtsTextChange = { value ->
                                onAction(
                                    TileEditorAction
                                        .TtsTextChanged(value)
                                )
                            },
                        )
                    }

                    item(key = "visuals") {
                        VisualsSection(
                            emoji = state.emoji,
                            onEmojiChange = { value ->
                                onAction(
                                    TileEditorAction
                                        .EmojiChanged(value)
                                )
                            },
                            imageUri = state.imageUri,
                            onImageUriChange = { value ->
                                onAction(
                                    TileEditorAction
                                        .ImageUriChanged(value)
                                )
                            },
                            partOfSpeech =
                                state.partOfSpeech,
                            onPartOfSpeechChange = { value ->
                                onAction(
                                    TileEditorAction
                                        .PartOfSpeechChanged(
                                            value
                                        )
                                )
                            },
                        )
                    }

                    item(key = "audio") {
                        AudioSection(
                            audioUri = state.audioUri,
                            onAudioUriChange = { value ->
                                onAction(
                                    TileEditorAction
                                        .AudioUriChanged(value)
                                )
                            },
                            languageCode =
                                state.languageCode,
                            audioService = audioService,
                        )
                    }

                    item(key = "advanced") {
                        AdvancedSection(
                            typeOptions = typeOptions,
                            tileType = state.tileType,
                            onTileTypeChange = { value ->
                                onAction(
                                    TileEditorAction
                                        .TileTypeChanged(value)
                                )
                            },
                            categories = state.categories,
                            existingTile =
                                state.existingTile,
                            parentId = state.parentId,
                            onParentIdChange = { value ->
                                onAction(
                                    TileEditorAction
                                        .ParentIdChanged(value)
                                )
                            },
                            linkedCategoryId =
                                state.linkedCategoryId,
                            onLinkedCategoryIdChange = {
                                    value ->
                                onAction(
                                    TileEditorAction
                                        .LinkedCategoryIdChanged(
                                            value
                                        )
                                )
                            },
                            cellIndex = state.cellIndex,
                            onCellIndexChange = { value ->
                                onAction(
                                    TileEditorAction
                                        .CellIndexChanged(value)
                                )
                            },
                            tilesInParent =
                                state.tilesInParent,
                            maxCapacity =
                                DEFAULT_MAX_CAPACITY,
                            onOccupiedCellSelected = {
                                    occupiedIndex ->
                                onAction(
                                    TileEditorAction
                                        .OccupiedCellSelected(
                                            occupiedIndex
                                        )
                                )
                            },
                            isHidden = state.isHidden,
                            onHiddenChange = { value ->
                                onAction(
                                    TileEditorAction
                                        .HiddenChanged(value)
                                )
                            },
                            tileId = state.tileId,
                            onTileIdChange = { value ->
                                onAction(
                                    TileEditorAction
                                        .TileIdChanged(value)
                                )
                            },
                        )
                    }

                    item(key = "localization") {
                        LocalizationSection(
                            labelFeminine =
                                state.labelFeminine,
                            onLabelFeminineChange = {
                                    value ->
                                onAction(
                                    TileEditorAction
                                        .LabelFeminineChanged(
                                            value
                                        )
                                )
                            },
                            ttsTextFeminine =
                                state.ttsTextFeminine,
                            onTtsTextFeminineChange = {
                                    value ->
                                onAction(
                                    TileEditorAction
                                        .TtsTextFeminineChanged(
                                            value
                                        )
                                )
                            },
                        )
                    }

                    item(key = "preview") {
                        TileEditorPreview(
                            label = state.label,
                            ttsText = state.ttsText,
                            imageUri = state.imageUri,
                            emoji = state.emoji,
                            tileType = state.tileType,
                            isHidden = state.isHidden,
                            audioUri = state.audioUri,
                            backgroundColor = tileColor,
                            aspectRatio =
                                previewAspectRatio,
                            onPlayPreview =
                                onPlayPreview,
                        )
                    }

                    item(key = "bottom-spacer") {
                        Spacer(
                            modifier =
                                Modifier.height(32.dp)
                        )
                    }
                }

                TileEditorFooter(
                    isNewTile = state.isNewTile,
                    canSave = state.canSave,
                    isSubmitting =
                        state.isSubmitting,
                    onCancel = {
                        if (!state.isSubmitting) {
                            onAction(
                                TileEditorAction
                                    .CancelClicked
                            )
                        }
                    },
                    onSave = {
                        onAction(
                            TileEditorAction.SaveClicked
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun TileEditorHeader(
    isNewTile: Boolean,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (isNewTile) {
                stringResource(R.string.add_tile_title)
            } else {
                stringResource(R.string.edit_tile_title)
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        IconButton(
            onClick = onDismiss
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
            )
        }
    }
}

@Composable
private fun TileEditorPreview(
    label: String,
    ttsText: String,
    imageUri: String?,
    emoji: String,
    tileType: TileType,
    isHidden: Boolean,
    audioUri: String?,
    backgroundColor: androidx.compose.ui.graphics.Color,
    aspectRatio: Float,
    onPlayPreview: (String, String?) -> Unit,
) {
    val displayLabel = label.ifBlank {
        stringResource(R.string.label_placeholder)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.live_preview),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        TileUI(
            label = displayLabel,
            imageUri = imageUri,
            emoji = if (imageUri == null) {
                emoji.ifBlank { "🍎" }
            } else {
                null
            },
            backgroundColor = backgroundColor,
            // 👉 Removed the aspectRatio property and added it directly to the modifier!
            tileType = tileType,
            isHidden = isHidden,
            onClick = {
                onPlayPreview(ttsText, audioUri)
            },
            modifier = Modifier
                .width(200.dp)
                .aspectRatio(aspectRatio),
            labelFontSize = 24.sp,
        )
    }
}

@Composable
private fun TileEditorFooter(
    isNewTile: Boolean,
    canSave: Boolean,
    isSubmitting: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCancel,
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(
                        R.string.cancel_changes
                    )
                )
            }

            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.weight(1.5f),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        MaterialTheme.colorScheme.primary
                ),
            ) {
                Text(
                    text = if (isNewTile) {
                        stringResource(R.string.save_tile)
                    } else {
                        stringResource(R.string.update_tile)
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 400,
    heightDp = 800,
    apiLevel = 35,
)
@Composable
fun TileEditDialogPreview() {
    MyAACAppTheme {
        /*
         * TileEditDialog requires AACViewModel.
         * Use a fake editor state or preview-specific ViewModel if a complete
         * interactive preview is needed.
         */
    }
}