package com.kon.myaacapp.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ProfileManagerRoute(
    viewModelFactory: ProfileManagerViewModelFactory,
    onProfileChanged: () -> Unit,
    onError: (String) -> Unit = {},
    content: @Composable (
        state: ProfileManagerState,
        onAction: (ProfileManagerAction) -> Unit,
    ) -> Unit,
) {
    val profileManagerViewModel:
            ProfileManagerViewModel =
        viewModel(
            key = "profile-manager",
            factory = viewModelFactory,
        )

    val state by profileManagerViewModel.state
        .collectAsStateWithLifecycle()

    LaunchedEffect(profileManagerViewModel) {
        profileManagerViewModel.effects.collect { effect ->
            when (effect) {
                ProfileManagerEffect.ProfileCreated -> {
                    onProfileChanged()
                }

                ProfileManagerEffect.ProfileSwitched -> {
                    onProfileChanged()
                }

                ProfileManagerEffect.ProfileDeleted -> {
                    /*
                     * No communication-state reset is required
                     * because the active profile cannot be deleted.
                     */
                }

                is ProfileManagerEffect.ShowError -> {
                    onError(effect.message)
                }
            }
        }
    }

    content(
        state,
        profileManagerViewModel::onAction,
    )
}