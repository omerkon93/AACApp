package com.kon.myaacapp.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TileType {
    BASIC,
    FOLDER,
    CONNECTOR,
    QUICK_FIRE,
}