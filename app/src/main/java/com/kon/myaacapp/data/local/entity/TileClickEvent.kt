package com.kon.myaacapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tile_click_events",
    indices = [
        Index(value = ["tileId"]),
        Index(value = ["profileId"]),
        Index(value = ["timestamp"])
    ]
)
data class TileClickEvent(
    // FIX: Restored autoGenerate = true so Room correctly increments the ID.
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "tileId")
    val tileId: String,

    @ColumnInfo(name = "profileId", defaultValue = "default")
    val profileId: String = "default",

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)