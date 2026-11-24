package com.kon.myaacapp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.CreateProfileResult
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SelectFreeCustomProfileResult
import com.kon.myaacapp.domain.entitlement.BillingFeedback
import com.kon.myaacapp.domain.entitlement.FamilyEntitlementProvider
import com.kon.myaacapp.domain.entitlement.FamilyUpgradeOutcome
import com.kon.myaacapp.domain.entitlement.ProfileAvailabilityPartition
import com.kon.myaacapp.domain.entitlement.ProfileLimitPolicy
import com.kon.myaacapp.domain.entitlement.StoreFamilyEntitlementProvider
import com.kon.myaacapp.domain.model.ProfileCreationMode
import com.kon.myaacapp.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileManagerViewModel(
    private val profileRepository: ProfileRepository,
    private val aacRepository: AACRepository,
    private val familyEntitlementProvider: FamilyEntitlementProvider =
        StoreFamilyEntitlementProvider(),
) : ViewModel() {

    private val editorState =
        MutableStateFlow(ProfileEditorState())

    private val operationState =
        MutableStateFlow(ProfileOperationState())

    private val _effects =
        MutableSharedFlow<ProfileManagerEffect>()

    val effects: SharedFlow<ProfileManagerEffect> =
        _effects.asSharedFlow()

    private val repositorySnapshot =
        combine(
            profileRepository.profiles,
            profileRepository.activeProfile,
            profileRepository.profileAvailability,
        ) { profiles, activeProfile, availability ->
            ProfileRepositorySnapshot(
                profiles = profiles,
                activeProfile = activeProfile,
                availability = availability,
            )
        }

    val state: StateFlow<ProfileManagerState> =
        combine(
            repositorySnapshot,
            familyEntitlementProvider.entitlementState,
            editorState,
            operationState,
        ) {
                repoSnapshot,
                entitlement,
                editor,
                operation ->

            val profiles = repoSnapshot.profiles
            val activeProfile = repoSnapshot.activeProfile
            val effectivePartition =
                if (repoSnapshot.availability.availableProfiles.isNotEmpty() || profiles.isEmpty()) {
                    repoSnapshot.availability
                } else {
                    ProfileLimitPolicy.partitionProfiles(
                        profiles = profiles,
                        activeProfileId = activeProfile?.profileId,
                        selectedCustomProfileId = null,
                        entitlement = entitlement,
                    )
                }

            ProfileManagerState(
                profiles = profiles,
                activeProfile = activeProfile,
                availableProfiles = effectivePartition.availableProfiles,
                archivedProfiles = effectivePartition.archivedProfiles,
                selectedCustomProfileId = effectivePartition.selectedCustomProfileId,
                draftFreeProfileId = editor.draftFreeProfileId?.takeIf { candidate ->
                    effectivePartition.canChooseFreeProfile && profiles.any { it.profileId == candidate }
                },
                isFreeSlotLocked = effectivePartition.isFreeSlotLocked,
                canChooseFreeProfile = effectivePartition.canChooseFreeProfile,

                showCreateDialog =
                    editor.showCreateDialog,

                showLimitDialog =
                    editor.showLimitDialog && !entitlement.hasFamilyEntitlement,

                showConfirmFreeProfileDialog =
                    editor.showConfirmFreeProfileDialog &&
                        effectivePartition.canChooseFreeProfile,

                showBillingUnavailableNotice =
                    editor.showBillingUnavailableNotice,

                profileName =
                    editor.profileName,

                creationMode =
                    editor.creationMode,

                boardLanguageCode =
                    editor.boardLanguageCode,

                profilePendingDeletion =
                    editor.profilePendingDeletion,

                isCreatingProfile =
                    operation.isCreatingProfile,

                isSwitchingProfile =
                    operation.isSwitchingProfile,

                isDeletingProfile =
                    operation.isDeletingProfile,

                isUpdatingEntitlement =
                    operation.isUpdatingEntitlement,

                entitlement = entitlement,

                errorMessage =
                    operation.errorMessage,
            )
        }.asStateFlow(
            scope = viewModelScope,
            initialValue = ProfileManagerState(
                entitlement = familyEntitlementProvider.currentEntitlement(),
            ),
        )

    fun onAction(
        action: ProfileManagerAction,
    ) {
        when (action) {
            ProfileManagerAction.RefreshBillingState -> {
                viewModelScope.launch {
                    runCatching {
                        familyEntitlementProvider.refreshEntitlements()
                    }
                }
            }

            ProfileManagerAction.OpenCreateDialog -> {
                openCreateDialog()
            }

            ProfileManagerAction.CloseCreateDialog -> {
                closeCreateDialog()
            }

            ProfileManagerAction.DismissLimitDialog -> {
                familyEntitlementProvider.clearBillingFeedback()
                editorState.update { currentState ->
                    currentState.copy(
                        showLimitDialog = false,
                        showBillingUnavailableNotice = false,
                    )
                }
            }

            ProfileManagerAction.DismissBillingUnavailableNotice -> {
                familyEntitlementProvider.clearBillingFeedback()
                editorState.update { currentState ->
                    currentState.copy(
                        showBillingUnavailableNotice = false,
                    )
                }
            }

            ProfileManagerAction.UpgradeToFamilyClicked -> {
                handleFamilyUpgradeAction(restoreOnly = false)
            }

            ProfileManagerAction.RestoreFamilyPurchaseClicked -> {
                handleFamilyUpgradeAction(restoreOnly = true)
            }

            ProfileManagerAction.ManageSubscriptionClicked -> {
                viewModelScope.launch {
                    _effects.emit(
                        ProfileManagerEffect.OpenSubscriptionManagement(
                            uri = familyEntitlementProvider.getSubscriptionManagementUri(),
                        ),
                    )
                }
            }

            is ProfileManagerAction.ProfileNameChanged -> {
                editorState.update { currentState ->
                    currentState.copy(
                        profileName = action.value,
                    )
                }
            }

            is ProfileManagerAction.CreationModeChanged -> {
                editorState.update { currentState ->
                    currentState.copy(
                        creationMode = action.value,
                    )
                }
            }

            is ProfileManagerAction.BoardLanguageChanged -> {
                val normalized = LocaleHelper.normalize(action.value)
                if (normalized == "he" || normalized == "en") {
                    editorState.update { currentState ->
                        currentState.copy(
                            boardLanguageCode = normalized,
                        )
                    }
                }
            }

            ProfileManagerAction.CreateProfileClicked -> {
                createProfile()
            }

            is ProfileManagerAction.SwitchProfileClicked -> {
                switchProfile(
                    profileId = action.profileId,
                )
            }

            is ProfileManagerAction.SelectFreeCustomProfileClicked -> {
                selectFreeCustomProfile(
                    profileId = action.profileId,
                )
            }

            ProfileManagerAction.OpenConfirmFreeProfileDialog -> {
                if (!operationState.value.isBusy && state.value.canChooseFreeProfile) {
                    editorState.update { currentState ->
                        currentState.copy(
                            showConfirmFreeProfileDialog = true,
                        )
                    }
                }
            }

            ProfileManagerAction.DismissConfirmFreeProfileDialog -> {
                editorState.update { currentState ->
                    currentState.copy(
                        showConfirmFreeProfileDialog = false,
                    )
                }
            }

            ProfileManagerAction.DiscardFreeProfileChoice -> {
                editorState.update {
                    it.copy(draftFreeProfileId = null, showConfirmFreeProfileDialog = false)
                }
            }

            is ProfileManagerAction.ConfirmFreeCustomProfileClicked -> {
                confirmFreeCustomProfile(
                    profileId = action.profileId,
                )
            }

            is ProfileManagerAction.DeleteProfileClicked -> {
                requestProfileDeletion(
                    profile = action.profile,
                )
            }

            ProfileManagerAction.ConfirmDeleteProfile -> {
                confirmProfileDeletion()
            }

            ProfileManagerAction.CancelDeleteProfile -> {
                editorState.update { currentState ->
                    currentState.copy(
                        profilePendingDeletion = null,
                    )
                }
            }

            ProfileManagerAction.ErrorConsumed -> {
                operationState.update { currentState ->
                    currentState.copy(
                        errorMessage = null,
                    )
                }
            }
        }
    }

    private fun openCreateDialog() {
        if (operationState.value.isBusy) {
            return
        }

        val entitlement = familyEntitlementProvider.currentEntitlement()
        val savedCount = profileRepository.profiles.value.size
        if (!ProfileLimitPolicy.canCreateProfile(savedCount, entitlement)) {
            editorState.update { currentState ->
                currentState.copy(
                    showLimitDialog = true,
                    showBillingUnavailableNotice = false,
                )
            }
            return
        }

        val defaultBoardLanguage = state.value.activeProfile
            ?.activeLanguageCode
            ?.let { LocaleHelper.normalize(it) }
            ?.takeIf { it == "he" || it == "en" }
            ?: DEFAULT_LANGUAGE_CODE

        editorState.update { currentState ->
            currentState.copy(
                showCreateDialog = true,
                showLimitDialog = false,
                profileName = "",
                creationMode =
                    ProfileCreationMode.BLANK,
                boardLanguageCode = defaultBoardLanguage,
            )
        }
    }

    private fun closeCreateDialog() {
        if (operationState.value.isCreatingProfile) {
            return
        }

        editorState.update { currentState ->
            currentState.copy(
                showCreateDialog = false,
                profileName = "",
                creationMode =
                    ProfileCreationMode.BLANK,
                boardLanguageCode = DEFAULT_LANGUAGE_CODE,
            )
        }
    }

    private fun createProfile() {
        val currentEditorState =
            editorState.value

        val normalizedName =
            currentEditorState.profileName.trim()

        if (
            normalizedName.isBlank() ||
            operationState.value.isBusy
        ) {
            return
        }

        operationState.update { currentState ->
            currentState.copy(
                isCreatingProfile = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                val result = profileRepository.createProfile(
                    name = normalizedName,
                    creationMode =
                        currentEditorState.creationMode,
                    boardLanguageCode =
                        currentEditorState.boardLanguageCode,
                )
                if (result is CreateProfileResult.Success) {
                    aacRepository.reload()
                }
                result
            }.onSuccess { result ->
                operationState.update { currentState ->
                    currentState.copy(
                        isCreatingProfile = false,
                    )
                }

                when (result) {
                    is CreateProfileResult.Success -> {
                        editorState.update { currentState ->
                            currentState.copy(
                                showCreateDialog = false,
                                showLimitDialog = false,
                                showBillingUnavailableNotice = false,
                                profileName = "",
                                creationMode =
                                    ProfileCreationMode.BLANK,
                                boardLanguageCode = DEFAULT_LANGUAGE_CODE,
                            )
                        }

                        _effects.emit(
                            ProfileManagerEffect.ProfileCreated
                        )
                    }

                    is CreateProfileResult.LimitReached -> {
                        editorState.update { currentState ->
                            currentState.copy(
                                showCreateDialog = false,
                                showLimitDialog = true,
                                showBillingUnavailableNotice = false,
                            )
                        }
                    }

                    CreateProfileResult.InvalidName -> {
                        showError("Profile name cannot be empty.")
                    }

                    CreateProfileResult.SaveFailed -> {
                        showError("Failed to create profile.")
                    }
                }
            }.onFailure { error ->
                operationState.update { currentState ->
                    currentState.copy(
                        isCreatingProfile = false,
                    )
                }

                showError(
                    message =
                        error.message
                            ?: "Failed to create profile.",
                )
            }
        }
    }

    private fun handleFamilyUpgradeAction(restoreOnly: Boolean) {
        if (operationState.value.isBusy) {
            return
        }

        operationState.update { currentState ->
            currentState.copy(
                isUpdatingEntitlement = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                if (restoreOnly) {
                    familyEntitlementProvider.restoreFamilyPurchase()
                } else {
                    familyEntitlementProvider.requestFamilyUpgrade()
                }
            }.onSuccess { outcome ->
                operationState.update { currentState ->
                    currentState.copy(
                        isUpdatingEntitlement = false,
                    )
                }

                when (outcome) {
                    FamilyUpgradeOutcome.Activated -> {
                        editorState.update { currentState ->
                            currentState.copy(
                                showLimitDialog = false,
                                showBillingUnavailableNotice = false,
                            )
                        }
                    }

                    FamilyUpgradeOutcome.LaunchedCheckout,
                    FamilyUpgradeOutcome.Pending,
                    FamilyUpgradeOutcome.UserCanceled,
                    FamilyUpgradeOutcome.ProductUnavailable,
                    FamilyUpgradeOutcome.NoPurchaseFound,
                    -> {
                        editorState.update { currentState ->
                            currentState.copy(
                                showBillingUnavailableNotice = false,
                            )
                        }
                    }

                    FamilyUpgradeOutcome.BillingUnavailable -> {
                        editorState.update { currentState ->
                            currentState.copy(
                                showBillingUnavailableNotice = true,
                            )
                        }
                    }

                    is FamilyUpgradeOutcome.Failed -> {
                        editorState.update { currentState ->
                            currentState.copy(
                                showBillingUnavailableNotice = false,
                            )
                        }
                        if (familyEntitlementProvider.currentEntitlement().lastBillingFeedback == BillingFeedback.NONE) {
                            showError(
                                message =
                                    outcome.reason
                                        ?: "Failed to update Family plan.",
                            )
                        }
                    }
                }
            }.onFailure { error ->
                operationState.update { currentState ->
                    currentState.copy(
                        isUpdatingEntitlement = false,
                    )
                }

                showError(
                    message =
                        error.message
                            ?: "Failed to update Family plan.",
                )
            }
        }
    }

    private fun switchProfile(
        profileId: String,
    ) {
        val currentState = state.value

        if (
            operationState.value.isBusy ||
            currentState.activeProfile
                ?.profileId == profileId
        ) {
            return
        }

        val isAvailable =
            currentState.availableProfiles.any { profile ->
                profile.profileId == profileId
            }

        if (!isAvailable) {
            showError(
                message = if (currentState.canChooseFreeProfile) {
                    "This profile is archived. Select and confirm it before switching, or upgrade to Family."
                } else {
                    "This profile is archived and available with Family."
                },
            )

            return
        }

        operationState.update { currentState ->
            currentState.copy(
                isSwitchingProfile = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                profileRepository.switchProfile(
                    profileId = profileId,
                )
            }.onSuccess { switched ->
                operationState.update { currentState ->
                    currentState.copy(
                        isSwitchingProfile = false,
                    )
                }

                if (switched) {
                    _effects.emit(
                        ProfileManagerEffect.ProfileSwitched
                    )
                }
            }.onFailure { error ->
                operationState.update { currentState ->
                    currentState.copy(
                        isSwitchingProfile = false,
                    )
                }

                showError(
                    message =
                        error.message
                            ?: "Failed to switch profile.",
                )
            }
        }
    }

    private fun selectFreeCustomProfile(
        profileId: String,
    ) {
        val currentState = state.value
        if (operationState.value.isBusy) {
            return
        }
        if (!currentState.canChooseFreeProfile) {
            showError(
                message = "The Free custom profile choice is already locked. Upgrade to Family to use all profiles.",
            )
            return
        }

        val profileExists =
            currentState.profiles.any { profile ->
                profile.profileId == profileId
            }
        if (!profileExists || profileId == DEFAULT_PROFILE_ID) {
            showError(
                message = "The selected profile was not found.",
            )
            return
        }

        editorState.update { it.copy(draftFreeProfileId = profileId) }
    }

    private fun confirmFreeCustomProfile(
        profileId: String,
    ) {
        val currentState = state.value
        if (operationState.value.isBusy) {
            return
        }
        if (!currentState.canChooseFreeProfile) {
            editorState.update { it.copy(showConfirmFreeProfileDialog = false) }
            showError(
                message = "The Free custom profile choice is already locked. Upgrade to Family to use all profiles.",
            )
            return
        }

        val profileExists =
            currentState.profiles.any { profile ->
                profile.profileId == profileId
            }
        if (!profileExists || profileId == DEFAULT_PROFILE_ID) {
            editorState.update { it.copy(showConfirmFreeProfileDialog = false) }
            showError("The selected profile was not found.")
            return
        }

        operationState.update { state ->
            state.copy(
                isSwitchingProfile = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                profileRepository.confirmFreeCustomProfile(
                    profileId = profileId,
                )
            }.onSuccess { result ->
                operationState.update { state ->
                    state.copy(
                        isSwitchingProfile = false,
                    )
                }
                editorState.update {
                    it.copy(
                        showConfirmFreeProfileDialog = false,
                        draftFreeProfileId = if (result is SelectFreeCustomProfileResult.Success) {
                            null
                        } else {
                            it.draftFreeProfileId
                        },
                    )
                }

                when (result) {
                    is SelectFreeCustomProfileResult.Success -> {
                        if (result.activeProfileChanged) {
                            _effects.emit(
                                ProfileManagerEffect.ProfileSwitched
                            )
                        }
                    }

                    SelectFreeCustomProfileResult.Locked -> {
                        showError(
                            "The Free custom profile choice is already locked. Upgrade to Family to use all profiles.",
                        )
                    }

                    SelectFreeCustomProfileResult.NotApplicable -> Unit

                    SelectFreeCustomProfileResult.InvalidTarget -> {
                        showError("The selected profile was not found.")
                    }
                }
            }.onFailure { error ->
                operationState.update { state ->
                    state.copy(
                        isSwitchingProfile = false,
                    )
                }

                showError(
                    message =
                        error.message
                            ?: "Failed to confirm Free profile.",
                )
            }
        }
    }

    private fun requestProfileDeletion(
        profile: UserProfile,
    ) {
        val currentState = state.value

        if (
            operationState.value.isBusy ||
            profile.profileId ==
            currentState.activeProfile?.profileId ||
            profile.profileId == DEFAULT_PROFILE_ID
        ) {
            return
        }

        editorState.update { editor ->
            editor.copy(
                profilePendingDeletion = profile,
            )
        }
    }

    private fun confirmProfileDeletion() {
        val profile =
            editorState.value.profilePendingDeletion
                ?: return

        val currentState = state.value

        if (
            operationState.value.isBusy ||
            currentState.profiles.size <= 1 ||
            profile.profileId ==
            currentState.activeProfile?.profileId ||
            profile.profileId == DEFAULT_PROFILE_ID
        ) {
            return
        }

        operationState.update { operation ->
            operation.copy(
                isDeletingProfile = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                aacRepository.deleteProfileAnalytics(
                    profileId = profile.profileId,
                )

                profileRepository.deleteProfile(
                    profileId = profile.profileId,
                )
            }.onSuccess {
                operationState.update { operation ->
                    operation.copy(
                        isDeletingProfile = false,
                    )
                }

                editorState.update { editor ->
                    editor.copy(
                        profilePendingDeletion = null,
                    )
                }

                _effects.emit(
                    ProfileManagerEffect.ProfileDeleted
                )
            }.onFailure { error ->
                operationState.update { operation ->
                    operation.copy(
                        isDeletingProfile = false,
                    )
                }

                showError(
                    message =
                        error.message
                            ?: "Failed to delete profile.",
                )
            }
        }
    }

    private fun showError(
        message: String,
    ) {
        operationState.update { currentState ->
            currentState.copy(
                errorMessage = message,
            )
        }

        viewModelScope.launch {
            _effects.emit(
                ProfileManagerEffect.ShowError(
                    message = message,
                )
            )
        }
    }

    private companion object {
        const val DEFAULT_PROFILE_ID = "default"
        const val DEFAULT_LANGUAGE_CODE = "he"
    }
}

private data class ProfileEditorState(
    val draftFreeProfileId: String? = null,
    val showCreateDialog: Boolean = false,
    val showLimitDialog: Boolean = false,
    val showConfirmFreeProfileDialog: Boolean = false,
    val showBillingUnavailableNotice: Boolean = false,
    val profileName: String = "",
    val creationMode: ProfileCreationMode =
        ProfileCreationMode.BLANK,
    val boardLanguageCode: String = "he",
    val profilePendingDeletion: UserProfile? = null,
)

private data class ProfileOperationState(
    val isCreatingProfile: Boolean = false,
    val isSwitchingProfile: Boolean = false,
    val isDeletingProfile: Boolean = false,
    val isUpdatingEntitlement: Boolean = false,
    val errorMessage: String? = null,
) {
    val isBusy: Boolean
        get() =
            isCreatingProfile ||
                    isSwitchingProfile ||
                    isDeletingProfile ||
                    isUpdatingEntitlement
}

private data class ProfileRepositorySnapshot(
    val profiles: List<UserProfile>,
    val activeProfile: UserProfile?,
    val availability: ProfileAvailabilityPartition,
)

private fun <T> kotlinx.coroutines.flow.Flow<T>.asStateFlow(
    scope: kotlinx.coroutines.CoroutineScope,
    initialValue: T,
): StateFlow<T> {
    return stateIn(
        scope = scope,
        started =
            SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000,
            ),
        initialValue = initialValue,
    )
}
