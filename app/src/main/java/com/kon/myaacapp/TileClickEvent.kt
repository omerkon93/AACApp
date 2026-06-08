package com.kon.myaacapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tile_click_events")
data class TileClickEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tileId: String,
    val timestamp: Long = System.currentTimeMillis()
)
