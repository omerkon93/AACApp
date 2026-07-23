package com.kon.myaacapp.ui.admin.list

import com.kon.myaacapp.domain.model.CombinedTile

sealed interface AdminListEffect {

    data object OpenTileCreator :
        AdminListEffect

    data class OpenTileEditor(
        val tile: CombinedTile,
    ) : AdminListEffect

    data class RequestTileDeletion(
        val tile: CombinedTile,
    ) : AdminListEffect

    data class RequestMicrophonePermission(
        val tile: CombinedTile,
    ) : AdminListEffect

    data class StartRecording(
        val tile: CombinedTile,
        val languageCode: String,
    ) : AdminListEffect

    data object StopRecording :
        AdminListEffect

    data class PlayAudioPreview(
        val ttsText: String,
        val audioUri: String?,
    ) : AdminListEffect

    data class ShowError(
        val message: String,
    ) : AdminListEffect
}