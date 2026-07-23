package com.kon.myaacapp.ui.profile

import androidx.compose.runtime.Immutable
import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.UserProfile

@Immutable
data class ProfileManagerState(
    val profiles: List<UserProfile> = emptyList(),
    val activeProfile: UserProfile? = null,

    val showCreateDialog: Boolean = false,
    val profileName: String = "",

    val creationMode: ProfileCreationMode =
        ProfileCreationMode.BLANK,

    val profilePendingDeletion: UserProfile? = null,

    val isCreatingProfile: Boolean = false,
    val isSwitchingProfile: Boolean = false,
    val isDeletingProfile: Boolean = false,

    val errorMessage: String? = null,
) {
    val canCreateProfile: Boolean
        get() =
            profileName.isNotBlank() &&
                    !isBusy

    val canDeletePendingProfile: Boolean
        get() =
            profilePendingDeletion != null &&
                    profiles.size > 1 &&
                    !isBusy

    val isBusy: Boolean
        get() =
            isCreatingProfile ||
                    isSwitchingProfile ||
                    isDeletingProfile
}