package com.kon.myaacapp.ui.admin.grid

import com.kon.myaacapp.domain.model.CombinedTile

sealed interface AdminGridAction {

    data class TileEditClicked(
        val tile: CombinedTile,
    ) : AdminGridAction

    data class EmptyCellClicked(
        val cellIndex: Int,
    ) : AdminGridAction

    data class DragStarted(
        val cellIndex: Int?,
    ) : AdminGridAction

    data class DragHovered(
        val cellIndex: Int?,
    ) : AdminGridAction

    data object DragEnded : AdminGridAction

    data object DragCancelled : AdminGridAction

    data object ErrorConsumed : AdminGridAction
}