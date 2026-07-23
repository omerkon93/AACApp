package com.kon.myaacapp.ui.admin

import com.kon.myaacapp.domain.model.CombinedTile

sealed interface AdminDashboardAction {

    data class OpenCategory(
        val categoryId: String,
    ) : AdminDashboardAction

    data object NavigateUp :
        AdminDashboardAction

    data object ResetToHome :
        AdminDashboardAction

    data class AttachTileToCategory(
        val tileId: String,
        val cellIndex: Int?,
    ) : AdminDashboardAction

    data class RemoveTileFromCategory(
        val tileId: String,
    ) : AdminDashboardAction

    data class DeleteTile(
        val tile: CombinedTile,
    ) : AdminDashboardAction

    data object ErrorConsumed :
        AdminDashboardAction
}