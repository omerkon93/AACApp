package com.kon.myaacapp.ui.communication

import com.kon.myaacapp.domain.model.CombinedTile

sealed interface CommunicationAction {

    data class TileClicked(
        val tile: CombinedTile,
    ) : CommunicationAction

    data object BackClicked :
        CommunicationAction

    data object HomeClicked :
        CommunicationAction

    data object SpeakSentenceClicked :
        CommunicationAction

    data object ClearSentenceClicked :
        CommunicationAction

    data object BackspaceSentenceClicked :
        CommunicationAction

    data object AdminSettingsClicked :
        CommunicationAction

    data object ErrorConsumed :
        CommunicationAction
}