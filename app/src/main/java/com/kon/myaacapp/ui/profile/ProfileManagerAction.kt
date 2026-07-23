package com.kon.myaacapp.ui.profile

import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.UserProfile

sealed interface ProfileManagerAction {

    data object OpenCreateDialog :
        ProfileManagerAction

    data object CloseCreateDialog :
        ProfileManagerAction

    data class ProfileNameChanged(
        val value: String,
    ) : ProfileManagerAction

    data class CreationModeChanged(
        val value: ProfileCreationMode,
    ) : ProfileManagerAction

    data object CreateProfileClicked :
        ProfileManagerAction

    data class SwitchProfileClicked(
        val profileId: String,
    ) : ProfileManagerAction

    data class DeleteProfileClicked(
        val profile: UserProfile,
    ) : ProfileManagerAction

    data object ConfirmDeleteProfile :
        ProfileManagerAction

    data object CancelDeleteProfile :
        ProfileManagerAction

    data object ErrorConsumed :
        ProfileManagerAction
}