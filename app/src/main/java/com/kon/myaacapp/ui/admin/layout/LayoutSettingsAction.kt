package com.kon.myaacapp.ui.admin.layout

sealed interface LayoutSettingsAction {

    data class GridColumnsChanged(
        val value: Int,
    ) : LayoutSettingsAction

    data class GridRowsChanged(
        val value: Int,
    ) : LayoutSettingsAction

    data class GridTileScaleChanged(
        val value: Float,
    ) : LayoutSettingsAction

    data class GridTileContainerScaleChanged(
        val value: Float,
    ) : LayoutSettingsAction

    data class BarTileImageScaleChanged(
        val value: Float,
    ) : LayoutSettingsAction

    data class BarTileTitleScaleChanged(
        val value: Float,
    ) : LayoutSettingsAction

    data class ActionButtonScaleChanged(
        val value: Float,
    ) : LayoutSettingsAction

    data class ShowSentenceBarChanged(
        val value: Boolean,
    ) : LayoutSettingsAction

    data class ShowBackButtonChanged(
        val value: Boolean,
    ) : LayoutSettingsAction

    data class ShowBackspaceButtonChanged(
        val value: Boolean,
    ) : LayoutSettingsAction

    data class ShowSpeakButtonChanged(
        val value: Boolean,
    ) : LayoutSettingsAction

    data class HomeInActionBarChanged(
        val value: Boolean,
    ) : LayoutSettingsAction

    data object SaveCurrentAsDefault :
        LayoutSettingsAction

    data object RestoreDefault :
        LayoutSettingsAction

    data object ErrorConsumed :
        LayoutSettingsAction
}