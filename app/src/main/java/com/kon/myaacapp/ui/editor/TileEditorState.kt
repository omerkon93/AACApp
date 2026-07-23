package com.kon.myaacapp.ui.editor

import androidx.compose.runtime.Immutable
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileType

@Immutable
data class TileEditorState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,

    val existingTile: CombinedTile? = null,

    val tileId: String = "",
    val label: String = "",
    val ttsText: String = "",

    val labelFeminine: String = "",
    val ttsTextFeminine: String = "",

    val emoji: String = "",
    val imageUri: String? = null,
    val audioUri: String? = null,
    val backgroundColorHex: String = "",

    val partOfSpeech: String = "",
    val grammaticalGender: String = "",

    val tileType: TileType = TileType.BASIC,
    val languageCode: String = "he",

    val parentId: String? = null,
    val linkedCategoryId: String? = null,
    val cellIndex: String = "",

    val isHidden: Boolean = false,

    val categories: List<CombinedTile> = emptyList(),
    val tilesInParent: List<CombinedTile> = emptyList(),

    val showOverwriteDialog: Boolean = false,
    val pendingCellIndex: String = "",

    val errorMessage: String? = null,
) {
    val isNewTile: Boolean
        get() = existingTile == null

    val canSave: Boolean
        get() =
            label.isNotBlank() &&
                    ttsText.isNotBlank() &&
                    !isLoading &&
                    !isSubmitting
}