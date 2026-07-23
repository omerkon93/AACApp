package com.kon.myaacapp.data.mapper

import com.kon.myaacapp.data.local.entity.TileClickEvent
import com.kon.myaacapp.domain.model.TileUsageEvent

fun TileClickEvent.toDomain(): TileUsageEvent {
    return TileUsageEvent(
        id = id,
        tileId = tileId,
        profileId = profileId,
        timestamp = timestamp,
    )
}

fun TileUsageEvent.toEntity(): TileClickEvent {
    return TileClickEvent(
        id = id,
        tileId = tileId,
        profileId = profileId,
        timestamp = timestamp,
    )
}