package com.kon.myaacapp.ui.communication

import androidx.compose.runtime.Immutable
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.service.Gender

@Immutable
data class CommunicationState(
    val tiles: List<CombinedTile> = emptyList(),
    val sentence: List<CombinedTile> = emptyList(),

    val currentParentId: String? = null,
    val userGender: Gender = Gender.MALE,
    val languageCode: String = "he",

    val gridColumns: Int = 3,
    val gridRows: Int = 5,

    val gridTileScale: Float = 1.0f,
    val gridTileContainerScale: Float = 1.0f,

    val barTileImageScale: Float = 1.0f,
    val barTileTitleScale: Float = 1.0f,

    val actionButtonScale: Float = 1.0f,

    val showSentenceBar: Boolean = false,
    val showBackButton: Boolean = true,
    val showBackspaceButton: Boolean = true,
    val showSpeakButton: Boolean = true,
    val homeInActionBar: Boolean = true,

    val layoutSettingsLoaded: Boolean = false,
    val isLoading: Boolean = true,

    val errorMessage: String? = null,
) {
    val canNavigateBack: Boolean
        get() = currentParentId != null

    val hasSentence: Boolean
        get() = sentence.isNotEmpty()

    val showSeparateHomeButton: Boolean
        get() = !homeInActionBar
}