package com.kon.myaacapp

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

@Entity(
    tableName = "tile_placements",
    primaryKeys = ["tileId", "parentId", "languageCode"],
    foreignKeys = [
        ForeignKey(
            entity = AACTile::class,
            parentColumns = ["id", "languageCode"],
            childColumns = ["tileId", "languageCode"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["parentId", "languageCode"])]
)
@Serializable
data class TilePlacement(
    val tileId: String,
    val parentId: String, // Note: Root is represented by a special ID or we handle null. Room primary keys can't be null.
    val languageCode: String,
    val cellIndex: Int? = null,
    val sortOrder: Int = 0
)

// Since Room primary keys cannot be null, let's use a constant for the Root parent.
const val ROOT_PARENT_ID = "ROOT_COLLECTION"
