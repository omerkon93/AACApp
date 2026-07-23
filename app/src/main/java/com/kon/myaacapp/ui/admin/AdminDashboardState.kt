package com.kon.myaacapp.ui.admin

import androidx.compose.runtime.Immutable
import com.kon.myaacapp.domain.model.CombinedTile

@Immutable
data class AdminDashboardState(
    val currentParentId: String? = null,
    val languageCode: String = "he",

    val allTiles: List<CombinedTile> =
        emptyList(),

    val allCategories: List<CombinedTile> =
        emptyList(),

    val gridColumns: Int = 3,
    val gridRows: Int = 5,

    val gridTileScale: Float = 1.0f,
    val gridTileContainerScale: Float = 1.0f,

    val isDeletingTile: Boolean = false,
    val errorMessage: String? = null,
) {
    val canNavigateUp: Boolean
        get() = currentParentId != null
}