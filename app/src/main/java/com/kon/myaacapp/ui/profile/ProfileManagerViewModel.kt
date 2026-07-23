package com.kon.myaacapp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository
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
) : ViewModel() {

    private val editorState =
        MutableStateFlow(ProfileEditorState())

    private val operationState =
        MutableStateFlow(ProfileOperationState())

    private val _effects =
        MutableSharedFlow<ProfileManagerEffect>()

    val effects: SharedFlow<ProfileManagerEffect> =
        _effects.asSharedFlow()

    val state: StateFlow<ProfileManagerState> =
        combine(
            profileRepository.profiles,
            profileRepository.activeProfile,
            editorState,
            operationState,
        ) {
                profiles,
                activeProfile,
                editor,
                operation ->

            ProfileManagerState(
                profiles = profiles,
                activeProfile = activeProfile,

                showCreateDialog =
                    editor.showCreateDialog,

                profileName =
                    editor.profileName,

                creationMode =
                    editor.creationMode,

                profilePendingDeletion =
                    editor.profilePendingDeletion,

                isCreatingProfile =
                    operation.isCreatingProfile,

                isSwitchingProfile =
                    operation.isSwitchingProfile,

                isDeletingProfile =
                    operation.isDeletingProfile,

                errorMessage =
                    operation.errorMessage,
            )
        }.asStateFlow(
            scope = viewModelScope,
            initialValue = ProfileManagerState(),
        )

    fun onAction(
        action: ProfileManagerAction,
    ) {
        when (action) {
            ProfileManagerAction.OpenCreateDialog -> {
                openCreateDialog()
            }

            ProfileManagerAction.CloseCreateDialog -> {
                closeCreateDialog()
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

            ProfileManagerAction.CreateProfileClicked -> {
                createProfile()
            }

            is ProfileManagerAction.SwitchProfileClicked -> {
                switchProfile(
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

        editorState.update { currentState ->
            currentState.copy(
                showCreateDialog = true,
                profileName = "",
                creationMode =
                    ProfileCreationMode.BLANK,
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
                profileRepository.createProfile(
                    name = normalizedName,
                    creationMode =
                        currentEditorState.creationMode,
                )
            }.onSuccess {
                operationState.update { currentState ->
                    currentState.copy(
                        isCreatingProfile = false,
                    )
                }

                editorState.update { currentState ->
                    currentState.copy(
                        showCreateDialog = false,
                        profileName = "",
                        creationMode =
                            ProfileCreationMode.BLANK,
                    )
                }

                _effects.emit(
                    ProfileManagerEffect.ProfileCreated
                )
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

        val profileExists =
            currentState.profiles.any { profile ->
                profile.profileId == profileId
            }

        if (!profileExists) {
            showError(
                message = "The selected profile was not found.",
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
            }.onSuccess {
                operationState.update { currentState ->
                    currentState.copy(
                        isSwitchingProfile = false,
                    )
                }

                _effects.emit(
                    ProfileManagerEffect.ProfileSwitched
                )
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
    }
}

private data class ProfileEditorState(
    val showCreateDialog: Boolean = false,
    val profileName: String = "",
    val creationMode: ProfileCreationMode =
        ProfileCreationMode.BLANK,
    val profilePendingDeletion: UserProfile? = null,
)

private data class ProfileOperationState(
    val isCreatingProfile: Boolean = false,
    val isSwitchingProfile: Boolean = false,
    val isDeletingProfile: Boolean = false,
    val errorMessage: String? = null,
) {
    val isBusy: Boolean
        get() =
            isCreatingProfile ||
                    isSwitchingProfile ||
                    isDeletingProfile
}

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