package com.kon.myaacapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

@Entity(
    tableName = "tile_placements",
    primaryKeys = [
        "tileId",
        "parentId",
        "languageCode",
    ],
    foreignKeys = [
        ForeignKey(
            entity = AACTile::class,
            parentColumns = [
                "id",
                "languageCode",
            ],
            childColumns = [
                "tileId",
                "languageCode",
            ],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        /*
         * Speeds up foreign-key checks, tile deletion, and cascade operations.
         */
        Index(
            value = [
                "tileId",
                "languageCode",
            ],
            name = "index_tile_placements_tile_language",
        ),

        /*
         * Optimized for loading one category in display order.
         */
        Index(
            value = [
                "parentId",
                "languageCode",
                "sortOrder",
            ],
            name = "index_tile_placements_parent_language_sort",
        ),

        /*
         * Prevents two tiles from occupying the same cell.
         * SQLite still permits multiple null cellIndex values.
         */
        Index(
            value = [
                "parentId",
                "languageCode",
                "cellIndex",
            ],
            name = "index_tile_placements_unique_cell",
            unique = true,
        ),
    ],
)
@Serializable
data class TilePlacement(
    val tileId: String,
    val parentId: String,
    val languageCode: String,
    val cellIndex: Int? = null,
    val sortOrder: Int = 0,
)

/*
 * This value is stored in the database.
 * Changing it later requires a migration.
 */
const val ROOT_PARENT_ID = "ROOT_COLLECTION"