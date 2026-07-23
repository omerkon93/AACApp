package com.kon.myaacapp.ui.admin.list

import androidx.compose.runtime.Immutable
import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileType

@Immutable
data class AdminListState(
    val allTiles: List<CombinedTile> = emptyList(),
    val filteredTiles: List<CombinedTile> = emptyList(),

    val languageCode: String = "he",

    val searchQuery: String = "",

    val isFilterActive: Boolean = false,
    val showFilterSheet: Boolean = false,

    val selectedMediaFilters: Set<MediaFilter> =
        emptySet(),

    val selectedTypes: Set<TileType> =
        emptySet(),

    val selectedUsageFilters: Set<UsageFilter> =
        emptySet(),

    val quickRecordTile: CombinedTile? = null,
    val isRecording: Boolean = false,
    val temporaryAudioPath: String? = null,

    val isLoading: Boolean = false,
    val isSavingRecording: Boolean = false,

    val errorMessage: String? = null,
) {
    val activeFilterCount: Int
        get() =
            selectedMediaFilters.size +
                    selectedTypes.size +
                    selectedUsageFilters.size

    val hasActiveFilters: Boolean
        get() =
            isFilterActive &&
                    activeFilterCount > 0

    val canSaveRecording: Boolean
        get() =
            temporaryAudioPath != null &&
                    !isRecording &&
                    !isSavingRecording

    val isQuickRecordDialogVisible: Boolean
        get() = quickRecordTile != null
}