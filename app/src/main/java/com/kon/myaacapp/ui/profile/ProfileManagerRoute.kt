package com.kon.myaacapp.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val profileManagerViewModel:
            ProfileManagerViewModel =
        viewModel(
            key = "profile-manager",
            factory = viewModelFactory,
        )

    val state by profileManagerViewModel.state
        .collectAsStateWithLifecycle()

    LaunchedEffect(profileManagerViewModel) {
        profileManagerViewModel.onAction(ProfileManagerAction.RefreshBillingState)
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

                is ProfileManagerEffect.OpenSubscriptionManagement -> {
                    val primaryIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(effect.uri),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val launched = runCatching {
                        context.startActivity(primaryIntent)
                    }.isSuccess
                    if (!launched) {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/account/subscriptions"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
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
