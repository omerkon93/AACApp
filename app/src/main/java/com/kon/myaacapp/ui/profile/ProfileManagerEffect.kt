package com.kon.myaacapp.ui.profile

sealed interface ProfileManagerEffect {

    data object ProfileCreated :
        ProfileManagerEffect

    data object ProfileSwitched :
        ProfileManagerEffect

    data object ProfileDeleted :
        ProfileManagerEffect

    data class ShowError(
        val message: String,
    ) : ProfileManagerEffect
}