package com.kon.myaacapp.ui.editor

sealed interface TileEditorEffect {

    data object CloseEditor : TileEditorEffect

    data class ShowError(
        val message: String,
    ) : TileEditorEffect
}