package com.kon.myaacapp.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class UserProfile(
    val profileId: String,
    val profileName: String,
    val activeLanguageCode: String,
    val layout: Map<String, TileLayoutState> = emptyMap(),
)