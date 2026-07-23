package com.kon.myaacapp.ui.communication

sealed interface CommunicationEffect {

    data object NavigateBackFromRoot :
        CommunicationEffect

    data object OpenAdminSettings :
        CommunicationEffect

    data class ShowError(
        val message: String,
    ) : CommunicationEffect
}