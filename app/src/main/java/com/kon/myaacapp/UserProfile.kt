package com.kon.myaacapp

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val profileId: String,
    val profileName: String,
    val activeLanguageCode: String,
    val layout: Map<String, TileLayoutState> = emptyMap() // Key is tileId
)
