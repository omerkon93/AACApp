package com.kon.myaacapp.ui.editor

import com.kon.myaacapp.domain.model.TileType

sealed interface TileEditorAction {

    data class LabelChanged(
        val value: String,
    ) : TileEditorAction

    data class TtsTextChanged(
        val value: String,
    ) : TileEditorAction

    data class LabelFeminineChanged(
        val value: String,
    ) : TileEditorAction

    data class TtsTextFeminineChanged(
        val value: String,
    ) : TileEditorAction

    data class EmojiChanged(
        val value: String,
    ) : TileEditorAction

    data class ImageUriChanged(
        val value: String?,
    ) : TileEditorAction

    data class AudioUriChanged(
        val value: String?,
    ) : TileEditorAction

    data class BackgroundColorChanged(
        val value: String,
    ) : TileEditorAction

    data class PartOfSpeechChanged(
        val value: String,
    ) : TileEditorAction

    data class GrammaticalGenderChanged(
        val value: String,
    ) : TileEditorAction

    data class TileTypeChanged(
        val value: TileType,
    ) : TileEditorAction

    data class ParentIdChanged(
        val value: String?,
    ) : TileEditorAction

    data class LinkedCategoryIdChanged(
        val value: String?,
    ) : TileEditorAction

    data class CellIndexChanged(
        val value: String,
    ) : TileEditorAction

    data class HiddenChanged(
        val value: Boolean,
    ) : TileEditorAction

    data class TileIdChanged(
        val value: String,
    ) : TileEditorAction

    data class OccupiedCellSelected(
        val cellIndex: String,
    ) : TileEditorAction

    data object ConfirmOccupiedCell : TileEditorAction

    data object DismissOccupiedCellDialog : TileEditorAction

    data object SaveClicked : TileEditorAction

    data object CancelClicked : TileEditorAction

    data object ErrorConsumed : TileEditorAction
}