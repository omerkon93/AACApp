package com.kon.myaacapp.ui.profile

import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.UserProfile

sealed interface ProfileManagerAction {

    data object RefreshBillingState :
        ProfileManagerAction

    data object OpenCreateDialog :
        ProfileManagerAction

    data object CloseCreateDialog :
        ProfileManagerAction

    data object DismissLimitDialog :
        ProfileManagerAction

    data object DismissBillingUnavailableNotice :
        ProfileManagerAction

    data object UpgradeToFamilyClicked :
        ProfileManagerAction

    data object RestoreFamilyPurchaseClicked :
        ProfileManagerAction

    data object ManageSubscriptionClicked :
        ProfileManagerAction

    data class ProfileNameChanged(
        val value: String,
    ) : ProfileManagerAction

    data class CreationModeChanged(
        val value: ProfileCreationMode,
    ) : ProfileManagerAction

    data class BoardLanguageChanged(
        val value: String,
    ) : ProfileManagerAction

    data object CreateProfileClicked :
        ProfileManagerAction

    data class SwitchProfileClicked(
        val profileId: String,
    ) : ProfileManagerAction

    data class SelectFreeCustomProfileClicked(
        val profileId: String,
    ) : ProfileManagerAction

    data object OpenConfirmFreeProfileDialog :
        ProfileManagerAction

    data object DismissConfirmFreeProfileDialog :
        ProfileManagerAction

    data object DiscardFreeProfileChoice : ProfileManagerAction

    data class ConfirmFreeCustomProfileClicked(
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
