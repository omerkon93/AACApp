package com.kon.myaacapp.ui.admin.list

import com.kon.myaacapp.domain.model.CombinedTile
import com.kon.myaacapp.domain.model.TileType

sealed interface AdminListAction {

    data class SearchQueryChanged(
        val value: String,
    ) : AdminListAction

    data class FilterActiveChanged(
        val value: Boolean,
    ) : AdminListAction

    data object OpenFilterSheet :
        AdminListAction

    data object CloseFilterSheet :
        AdminListAction

    data class MediaFilterChanged(
        val filter: MediaFilter,
        val isSelected: Boolean,
    ) : AdminListAction

    data class TileTypeFilterChanged(
        val tileType: TileType,
        val isSelected: Boolean,
    ) : AdminListAction

    data class UsageFilterChanged(
        val filter: UsageFilter,
        val isSelected: Boolean,
    ) : AdminListAction

    data object ClearFilters :
        AdminListAction

    data object AddTileClicked :
        AdminListAction

    data class EditTileClicked(
        val tile: CombinedTile,
    ) : AdminListAction

    data class DeleteTileClicked(
        val tile: CombinedTile,
    ) : AdminListAction

    data class QuickRecordClicked(
        val tile: CombinedTile,
    ) : AdminListAction

    data object MicrophonePermissionGranted :
        AdminListAction

    data object MicrophonePermissionDenied :
        AdminListAction

    data class RecordingStarted(
        val temporaryAudioPath: String,
    ) : AdminListAction

    data object StopRecordingClicked :
        AdminListAction

    data object RecordingStopped :
        AdminListAction

    data object PreviewRecordingClicked :
        AdminListAction

    data object SaveRecordingClicked :
        AdminListAction

    data object CancelRecordingClicked :
        AdminListAction

    data object ErrorConsumed :
        AdminListAction
}