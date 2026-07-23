package com.kon.myaacapp.ui.admin.layout

import androidx.compose.runtime.Immutable

@Immutable
data class LayoutSettingsState(
    val gridColumns: Int = 3,
    val gridRows: Int = 5,

    val gridTileScale: Float = 1f,
    val gridTileContainerScale: Float = 1f,

    val barTileImageScale: Float = 1f,
    val barTileTitleScale: Float = 1f,

    val actionButtonScale: Float = 1f,

    val showSentenceBar: Boolean = false,
    val showBackButton: Boolean = true,
    val showBackspaceButton: Boolean = true,
    val showSpeakButton: Boolean = true,
    val homeInActionBar: Boolean = true,

    val isSavingDefault: Boolean = false,
    val isRestoringDefault: Boolean = false,

    val errorMessage: String? = null,
) {
    val safeGridColumns: Int
        get() = gridColumns.coerceIn(
            minimumValue = 1,
            maximumValue = 8,
        )

    val safeGridRows: Int
        get() = gridRows.coerceIn(
            minimumValue = 1,
            maximumValue = 10,
        )

    val safeGridTileScale: Float
        get() = gridTileScale.coerceIn(
            minimumValue = 0.5f,
            maximumValue = 2f,
        )

    val safeGridTileContainerScale: Float
        get() = gridTileContainerScale.coerceIn(
            minimumValue = 0.5f,
            maximumValue = 1f,
        )

    val safeBarTileImageScale: Float
        get() = barTileImageScale.coerceIn(
            minimumValue = 0.5f,
            maximumValue = 2f,
        )

    val safeBarTileTitleScale: Float
        get() = barTileTitleScale.coerceIn(
            minimumValue = 0.5f,
            maximumValue = 2f,
        )

    val safeActionButtonScale: Float
        get() = actionButtonScale.coerceIn(
            minimumValue = 0.5f,
            maximumValue = 2f,
        )

    val isBusy: Boolean
        get() =
            isSavingDefault ||
                    isRestoringDefault
}