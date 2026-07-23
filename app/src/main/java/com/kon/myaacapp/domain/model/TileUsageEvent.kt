package com.kon.myaacapp.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class TileUsageEvent(
    val id: Long = 0,
    val tileId: String,
    val profileId: String,
    val timestamp: Long,
)