package com.kon.myaacapp.ui.profile

import androidx.compose.runtime.Immutable
import com.kon.myaacapp.domain.entitlement.BillingFeedback
import com.kon.myaacapp.domain.entitlement.FamilyEntitlementState
import com.kon.myaacapp.domain.entitlement.FamilyPlanOfferDetails
import com.kon.myaacapp.domain.entitlement.ProfileLimitPolicy
import com.kon.myaacapp.domain.entitlement.SubscriptionBillingState
import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.UserProfile

@Immutable
data class ProfileManagerState(
    val profiles: List<UserProfile> = emptyList(),
    val activeProfile: UserProfile? = null,
    val availableProfiles: List<UserProfile> = emptyList(),
    val archivedProfiles: List<UserProfile> = emptyList(),
    val selectedCustomProfileId: String? = null,
    val draftFreeProfileId: String? = null,
    val isFreeSlotLocked: Boolean = false,
    val canChooseFreeProfile: Boolean = false,

    val showCreateDialog: Boolean = false,
    val showLimitDialog: Boolean = false,
    val showConfirmFreeProfileDialog: Boolean = false,
    val showBillingUnavailableNotice: Boolean = false,
    val profileName: String = "",

    val creationMode: ProfileCreationMode =
        ProfileCreationMode.BLANK,

    val boardLanguageCode: String = "he",

    val profilePendingDeletion: UserProfile? = null,

    val isCreatingProfile: Boolean = false,
    val isSwitchingProfile: Boolean = false,
    val isDeletingProfile: Boolean = false,
    val isUpdatingEntitlement: Boolean = false,

    val entitlement: FamilyEntitlementState =
        FamilyEntitlementState(),

    val errorMessage: String? = null,
) {
    val savedProfileCount: Int
        get() = profiles.size

    val availableProfileCount: Int
        get() = availableProfiles.size

    val archivedProfileCount: Int
        get() = archivedProfiles.size

    val hasArchivedProfiles: Boolean
        get() = archivedProfiles.isNotEmpty()

    val pendingFreeCustomProfile: UserProfile?
        get() = profiles.firstOrNull { it.profileId == draftFreeProfileId }
            ?: availableProfiles.firstOrNull { profile ->
            profile.profileId != ProfileLimitPolicy.DEFAULT_PROFILE_ID &&
                (selectedCustomProfileId == null || profile.profileId == selectedCustomProfileId)
        } ?: profiles.firstOrNull { it.profileId == selectedCustomProfileId }

    val freeProfileLimit: Int
        get() = entitlement.freeProfileLimit

    val hasFamilyEntitlement: Boolean
        get() = entitlement.hasFamilyEntitlement

    val isProfileLimitEnforced: Boolean
        get() = entitlement.isProfileCapEnforced

    val isBillingConfigured: Boolean
        get() = entitlement.isBillingConfigured

    val subscriptionState: SubscriptionBillingState
        get() = entitlement.subscriptionState

    val offerDetails: FamilyPlanOfferDetails?
        get() = entitlement.offerDetails

    val isAutoRenewing: Boolean
        get() = entitlement.isAutoRenewing

    val hasPendingPurchase: Boolean
        get() = entitlement.hasPendingPurchase

    val lastBillingFeedback: BillingFeedback
        get() = entitlement.lastBillingFeedback

    val isCachedOfflineEntitlement: Boolean
        get() = entitlement.isCachedOfflineEntitlement

    val isLoadingStoreOffer: Boolean
        get() = entitlement.isLoadingStoreOffer

    val isFreeLimitReached: Boolean
        get() = ProfileLimitPolicy.isFreeLimitReached(
            savedProfileCount = profiles.size,
            entitlement = entitlement,
        )

    val canCreateAnotherProfile: Boolean
        get() = ProfileLimitPolicy.canCreateProfile(
            savedProfileCount = profiles.size,
            entitlement = entitlement,
        )

    val canCreateProfile: Boolean
        get() =
            profileName.isNotBlank() &&
                    canCreateAnotherProfile &&
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
                    isDeletingProfile ||
                    isUpdatingEntitlement
}
