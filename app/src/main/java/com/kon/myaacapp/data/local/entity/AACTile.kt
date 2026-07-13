package com.kon.myaacapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import kotlinx.serialization.Serializable

@Entity(
    tableName = "aac_tiles",
    primaryKeys = [
        "id",
        "languageCode",
    ],
)
@Serializable
data class AACTile(
    val id: String,
    val label: String,
    val ttsText: String,
    val labelFeminine: String? = null,
    val ttsTextFeminine: String? = null,
    val emoji: String? = null,
    val audioUri: String? = null,
    val imageUri: String? = null,
    val backgroundColorHex: String? = null,
    val partOfSpeech: String? = null,
    val grammaticalGender: String? = null,
    val isCategory: Boolean,
    val parentId: String? = null,
    val linkedCategoryId: String? = null,
    val cellIndex: Int? = null,
    val sortOrder: Int = 0,
    val isQuickFire: Boolean = false,
    val isHidden: Boolean = false,
    val clickCount: Int = 0,
    val languageCode: String = "he",
)

data class AACTileWithPlacement(
    @Embedded
    val tile: AACTile,

    @ColumnInfo(name = "placed_cellIndex")
    val placedCellIndex: Int?,

    @ColumnInfo(name = "placed_sortOrder")
    val placedSortOrder: Int,

    @ColumnInfo(name = "placed_parentId")
    val placedParentId: String?,
)

fun AACTileWithPlacement.toAACTile(): AACTile {
    return tile.copy(
        cellIndex = placedCellIndex,
        sortOrder = placedSortOrder,
        parentId = placedParentId?.takeUnless {
            it == ROOT_PARENT_ID
        },
    )
}