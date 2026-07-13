package com.kon.myaacapp.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class TileLayoutState(
    val tileId: String,
    val parentId: String? = null,
    val linkedCategoryId: String? = null,
    val cellIndex: Int,
    val isQuickFire: Boolean = false,
    val isHidden: Boolean = false,
    val clickCount: Int = 0,
)