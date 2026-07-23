package com.kon.myaacapp.ui.admin.grid

import com.kon.myaacapp.domain.model.CombinedTile

sealed interface AdminGridEffect {

    data class OpenTileActions(
        val tile: CombinedTile,
    ) : AdminGridEffect

    data class OpenTileCreator(
        val cellIndex: Int,
    ) : AdminGridEffect

    data class ShowError(
        val message: String,
    ) : AdminGridEffect
}