package com.kon.myaacapp.ui.admin.grid

import androidx.compose.runtime.Immutable
import com.kon.myaacapp.domain.model.CombinedTile

@Immutable
data class AdminGridState(
    val tiles: List<CombinedTile> = emptyList(),

    val currentParentId: String? = null,
    val languageCode: String = "he",

    val gridColumns: Int = 3,
    val gridRows: Int = 4,

    val gridTileScale: Float = 1f,
    val gridTileContainerScale: Float = 1f,

    val selectedTile: CombinedTile? = null,

    val draggedIndex: Int? = null,
    val hoveredIndex: Int? = null,

    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val safeGridColumns: Int
        get() = gridColumns.coerceAtLeast(1)

    val safeGridRows: Int
        get() = gridRows.coerceAtLeast(1)

    val maximumTileIndex: Int
        get() = tiles.maxOfOrNull { tile ->
            tile.layoutState.cellIndex
        } ?: -1

    fun requiredRows(
        columnCount: Int,
    ): Int {
        val safeColumnCount =
            columnCount.coerceAtLeast(1)

        val rowsRequiredByTiles =
            if (maximumTileIndex >= 0) {
                (maximumTileIndex / safeColumnCount) + 1
            } else {
                1
            }

        return maxOf(
            safeGridRows,
            rowsRequiredByTiles,
        )
    }

    fun maximumCells(
        columnCount: Int,
    ): Int {
        val safeColumnCount =
            columnCount.coerceAtLeast(1)

        return safeColumnCount *
                requiredRows(safeColumnCount)
    }

    val tileMap: Map<Int, CombinedTile>
        get() = tiles.associateBy { tile ->
            tile.layoutState.cellIndex
        }
}