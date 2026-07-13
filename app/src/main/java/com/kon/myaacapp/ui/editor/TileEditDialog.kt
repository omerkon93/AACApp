package com.kon.myaacapp.ui.editor

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kon.myaacapp.data.local.entity.AACTile
import com.kon.myaacapp.AACViewModel
import com.kon.myaacapp.R
import com.kon.myaacapp.ui.communication.TileUI
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileType
import com.kon.myaacapp.ui.editor.components.AdvancedSection
import com.kon.myaacapp.ui.editor.components.AudioSection
import com.kon.myaacapp.ui.editor.components.BasicInfoSection
import com.kon.myaacapp.ui.editor.components.LocalizationSection
import com.kon.myaacapp.ui.editor.components.VisualsSection
import com.kon.myaacapp.ui.theme.MyAACAppTheme
import com.kon.myaacapp.ui.theme.resolveFitzgeraldColor

private const val DEFAULT_MAX_CAPACITY = 15
private const val DEFAULT_PART_OF_SPEECH = "NONE"
private const val DEFAULT_GRAMMATICAL_GENDER = "M"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileEditDialog(
    viewModel: AACViewModel,
    existingTile: AACTile? = null,
    initialCellIndex: Int? = null,
    onDismiss: () -> Unit,
) {
    /*
     * Keying editor state by tile ID makes the state reset when the caller
     * replaces the currently edited tile without destroying this composition.
     *
     * rememberSaveable also preserves primitive/String state across activity
     * recreation, such as an orientation change.
     */
    val editorKey = existingTile?.id ?: "new-tile"

    /*
     * Collect the StateFlow through the Android lifecycle instead of reading
     * currentParentId.value directly during composition.
     */
    val currentParentId by viewModel.currentParentId
        .collectAsStateWithLifecycle()

    var label by rememberSaveable(editorKey) {
        mutableStateOf(existingTile?.label.orEmpty())
    }

    var ttsText by rememberSaveable(editorKey) {
        mutableStateOf(existingTile?.ttsText.orEmpty())
    }

    var tileId by rememberSaveable(editorKey) {
        mutableStateOf(existingTile?.id.orEmpty())
    }

    var parentId by rememberSaveable(
        editorKey,
        currentParentId,
    ) {
        mutableStateOf(
            existingTile?.parentId ?: currentParentId
        )
    }

    var emoji by rememberSaveable(editorKey) {
        mutableStateOf(existingTile?.emoji.orEmpty())
    }

    var imageUri by rememberSaveable(editorKey) {
        mutableStateOf(existingTile?.imageUri)
    }

    var backgroundColorHex by rememberSaveable(editorKey) {
        mutableStateOf(existingTile?.backgroundColorHex.orEmpty())
    }

    var partOfSpeech by rememberSaveable(editorKey) {
        mutableStateOf(
            existingTile?.partOfSpeech
                ?: DEFAULT_PART_OF_SPEECH
        )
    }

    val initialTileType = remember(existingTile) {
        when {
            existingTile?.isCategory == true -> {
                TileType.FOLDER
            }

            existingTile?.linkedCategoryId != null -> {
                TileType.CONNECTOR
            }

            existingTile?.isQuickFire == true -> {
                TileType.QUICK_FIRE
            }

            else -> {
                TileType.BASIC
            }
        }
    }

    var tileType by rememberSaveable(editorKey) {
        mutableStateOf(initialTileType)
    }

    var linkedCategoryId by rememberSaveable(editorKey) {
        mutableStateOf(existingTile?.linkedCategoryId)
    }

    var cellIndex by rememberSaveable(
        editorKey,
        initialCellIndex,
    ) {
        mutableStateOf(
            existingTile?.cellIndex?.toString()
                ?: initialCellIndex?.toString()
                ?: ""
        )
    }

    var isHidden by rememberSaveable(editorKey) {
        mutableStateOf(existingTile?.isHidden ?: false)
    }

    var labelFeminine by rememberSaveable(editorKey) {
        mutableStateOf(existingTile?.labelFeminine.orEmpty())
    }

    var ttsTextFeminine by rememberSaveable(editorKey) {
        mutableStateOf(existingTile?.ttsTextFeminine.orEmpty())
    }

    var grammaticalGender by rememberSaveable(editorKey) {
        mutableStateOf(
            existingTile?.grammaticalGender
                ?: DEFAULT_GRAMMATICAL_GENDER
        )
    }

    var audioUri by rememberSaveable(editorKey) {
        mutableStateOf(existingTile?.audioUri)
    }

    var showOverwriteDialog by rememberSaveable(editorKey) {
        mutableStateOf(false)
    }

    var pendingCellIndex by rememberSaveable(editorKey) {
        mutableStateOf("")
    }

    var isSubmitting by remember(editorKey) {
        mutableStateOf(false)
    }

    /*
     * Request a new parent-specific Flow only when parentId changes.
     */
    val tilesFlow = remember(viewModel, parentId) {
        viewModel.getTilesByParentId(parentId)
    }

    /*
     * The explicit CombinedTile type prevents emptyList() and delegate
     * type-inference failures.
     */
    val tilesInParent: List<CombinedTile> by
    tilesFlow.collectAsStateWithLifecycle(
        initialValue = emptyList()
    )

    /*
     * These appear to be StateFlows, so their current values provide the initial
     * values automatically.
     */
    val categories by viewModel.allCategories
        .collectAsStateWithLifecycle()

    val languageCode by viewModel.languageCode
        .collectAsStateWithLifecycle()

    val canSave by remember {
        derivedStateOf {
            label.isNotBlank() &&
                    ttsText.isNotBlank() &&
                    !isSubmitting
        }
    }

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

    val orientation = LocalConfiguration.current.orientation

    /*
     * These preview values are derived only from the state they need.
     */
    val tileColor = remember(partOfSpeech) {
        resolveFitzgeraldColor(partOfSpeech)
    }

    val previewAspectRatio = remember(orientation) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            1.2f
        } else {
            1.0f
        }
    }

    BasicAlertDialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onDismiss()
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
                    isNewTile = existingTile == null,
                    onDismiss = {
                        if (!isSubmitting) {
                            onDismiss()
                        }
                    },
                )

                if (showOverwriteDialog) {
                    /*
                     * The board capacity is only 15, so an O(n) search has a negligible
                     * maximum cost and avoids allocating a separate lookup map.
                     */
                    val occupiedTile: CombinedTile? = remember(
                        tilesInParent,
                        pendingCellIndex,
                    ) {
                        tilesInParent.find { tile: CombinedTile ->
                            tile.cellIndex.toString() == pendingCellIndex
                        }
                    }

                    AlertDialog(
                        onDismissRequest = {
                            showOverwriteDialog = false
                            pendingCellIndex = ""
                        },
                        title = {
                            Text(
                                text = stringResource(
                                    R.string.cell_occupied_title
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(
                                    R.string.cell_occupied_msg,
                                    occupiedTile?.label.orEmpty(),
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    cellIndex = pendingCellIndex
                                    pendingCellIndex = ""
                                    showOverwriteDialog = false
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.ok)
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    pendingCellIndex = ""
                                    showOverwriteDialog = false
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel)
                                )
                            }
                        },
                    )
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                ) {
                    /*
                     * Stable keys preserve LazyColumn item identity and make
                     * movement/state retention more predictable.
                     */
                    item(key = "description") {
                        Text(
                            text = stringResource(
                                R.string.edit_tile_desc
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        )
                    }

                    item(key = "basic-info") {
                        BasicInfoSection(
                            label = label,
                            onLabelChange = { newLabel ->
                                label = newLabel
                            },
                            ttsText = ttsText,
                            onTtsTextChange = { newTtsText ->
                                ttsText = newTtsText
                            },
                        )
                    }

                    item(key = "visuals") {
                        /*
                         * Camera/gallery/image persistence belongs in
                         * VisualsSection. Keeping it there prevents duplicate
                         * launchers and keeps recomposition scope smaller.
                         */
                        VisualsSection(
                            emoji = emoji,
                            onEmojiChange = { newEmoji ->
                                emoji = newEmoji
                            },
                            imageUri = imageUri,
                            onImageUriChange = { newImageUri ->
                                imageUri = newImageUri
                            },
                            partOfSpeech = partOfSpeech,
                            onPartOfSpeechChange = { newPartOfSpeech ->
                                partOfSpeech = newPartOfSpeech
                            },
                        )
                    }

                    item(key = "audio") {
                        /*
                         * Permission, recording timer, recorder cleanup, and
                         * MediaPlayer lifecycle belong in AudioSection.
                         */
                        AudioSection(
                            audioUri = audioUri,
                            onAudioUriChange = { newAudioUri ->
                                audioUri = newAudioUri
                            },
                            languageCode = languageCode,
                            audioService = viewModel.audioService,
                        )
                    }

                    item(key = "advanced") {
                        AdvancedSection(
                            typeOptions = typeOptions,
                            tileType = tileType,
                            onTileTypeChange = { newTileType ->
                                tileType = newTileType

                                /*
                                 * Clear stale connector state when the selected
                                 * type no longer supports a linked category.
                                 */
                                if (
                                    newTileType != TileType.FOLDER &&
                                    newTileType != TileType.CONNECTOR
                                ) {
                                    linkedCategoryId = null
                                }
                            },
                            categories = categories,

                            /*
                             * AdvancedSection must accept AACTile?.
                             * See the required signature correction below.
                             */
                            existingTile = existingTile,
                            parentId = parentId,
                            onParentIdChange = { newParentId ->
                                parentId = newParentId

                                /*
                                 * A cell position belongs to its parent grid.
                                 * Clearing it prevents accidentally retaining a
                                 * now-invalid occupied position.
                                 */
                                if (
                                    newParentId !=
                                    existingTile?.parentId
                                ) {
                                    cellIndex = ""
                                }
                            },
                            linkedCategoryId = linkedCategoryId,
                            onLinkedCategoryIdChange = { newLinkedCategoryId ->
                                linkedCategoryId =
                                    newLinkedCategoryId
                            },
                            cellIndex = cellIndex,
                            onCellIndexChange = { newCellIndex ->
                                cellIndex = newCellIndex
                            },
                            tilesInParent = tilesInParent,
                            maxCapacity = DEFAULT_MAX_CAPACITY,
                            onOccupiedCellSelected = { occupiedIndex ->
                                pendingCellIndex = occupiedIndex
                                showOverwriteDialog = true
                            },
                            isHidden = isHidden,
                            onHiddenChange = { hidden ->
                                isHidden = hidden
                            },
                            tileId = tileId,
                            onTileIdChange = { newTileId ->
                                tileId = newTileId
                            },
                        )
                    }

                    item(key = "localization") {
                        LocalizationSection(
                            labelFeminine = labelFeminine,
                            onLabelFeminineChange = { newLabel ->
                                labelFeminine = newLabel
                            },
                            ttsTextFeminine = ttsTextFeminine,
                            onTtsTextFeminineChange = { newText ->
                                ttsTextFeminine = newText
                            },
                        )
                    }

                    item(key = "preview") {
                        TileEditorPreview(
                            label = label,
                            ttsText = ttsText,
                            imageUri = imageUri,
                            emoji = emoji,
                            tileType = tileType,
                            isHidden = isHidden,
                            audioUri = audioUri,
                            backgroundColor = tileColor,
                            aspectRatio = previewAspectRatio,
                            onPlayPreview = viewModel::playPreviewAudio,
                        )
                    }

                    item(key = "bottom-spacer") {
                        Spacer(
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                TileEditorFooter(
                    isNewTile = existingTile == null,
                    canSave = canSave,
                    isSubmitting = isSubmitting,
                    onCancel = {
                        if (!isSubmitting) {
                            onDismiss()
                        }
                    },
                    onSave = {
                        /*
                         * Guard again inside the callback because enabled state
                         * alone is not a transactional duplicate-submit guard.
                         */
                        if (!canSave || isSubmitting) {
                            return@TileEditorFooter
                        }

                        isSubmitting = true

                        val finalIsCategory =
                            tileType == TileType.FOLDER

                        val finalIsQuickFire =
                            tileType == TileType.QUICK_FIRE

                        val finalLinkedId =
                            when (tileType) {
                                TileType.FOLDER,
                                TileType.CONNECTOR -> {
                                    linkedCategoryId
                                }

                                else -> {
                                    null
                                }
                            }

                        val normalizedEmoji =
                            emoji.trim().ifBlank { null }

                        val normalizedBackgroundColor =
                            backgroundColorHex
                                .trim()
                                .ifBlank { null }

                        val normalizedPartOfSpeech =
                            partOfSpeech.takeUnless {
                                it == DEFAULT_PART_OF_SPEECH
                            }

                        val normalizedLabelFeminine =
                            labelFeminine.trim().ifBlank { null }

                        val normalizedTtsTextFeminine =
                            ttsTextFeminine.trim().ifBlank { null }

                        val normalizedCellIndex =
                            cellIndex.toIntOrNull()

                        if (existingTile == null) {
                            viewModel.addTile(
                                id = tileId.trim().ifBlank { null },
                                label = label.trim(),
                                ttsText = ttsText.trim(),
                                emoji = normalizedEmoji,
                                imageUri = imageUri,
                                isCategory = finalIsCategory,
                                parentId = parentId,
                                backgroundColorHex =
                                    normalizedBackgroundColor,
                                partOfSpeech =
                                    normalizedPartOfSpeech,
                                isQuickFire = finalIsQuickFire,
                                linkedCategoryId = finalLinkedId,
                                labelFeminine =
                                    normalizedLabelFeminine,
                                ttsTextFeminine =
                                    normalizedTtsTextFeminine,
                                grammaticalGender =
                                    grammaticalGender,
                                audioUri = audioUri,
                                cellIndex = normalizedCellIndex,
                                isHidden = isHidden,
                            )
                        } else {
                            viewModel.updateTile(
                                tile = existingTile.copy(
                                    label = label.trim(),
                                    ttsText = ttsText.trim(),
                                    emoji = normalizedEmoji,
                                    imageUri = imageUri,
                                    isCategory = finalIsCategory,
                                    parentId = parentId,
                                    backgroundColorHex =
                                        normalizedBackgroundColor,
                                    partOfSpeech =
                                        normalizedPartOfSpeech,
                                    isQuickFire =
                                        finalIsQuickFire,
                                    linkedCategoryId =
                                        finalLinkedId,
                                    labelFeminine =
                                        normalizedLabelFeminine,
                                    ttsTextFeminine =
                                        normalizedTtsTextFeminine,
                                    grammaticalGender =
                                        grammaticalGender,
                                    audioUri = audioUri,
                                    cellIndex =
                                        normalizedCellIndex,
                                    isHidden = isHidden,
                                ),
                            )
                        }

                        /*
                         * This preserves the original behavior, where the
                         * dialog closes immediately after requesting a save.
                         *
                         * For stronger reliability, ViewModel save functions
                         * should expose completion/failure state and dismissal
                         * should happen only after a successful transaction.
                         */
                        onDismiss()
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
            aspectRatio = aspectRatio,
            tileType = tileType,
            isHidden = isHidden,
            onClick = {
                onPlayPreview(ttsText, audioUri)
            },
            modifier = Modifier.width(200.dp),
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