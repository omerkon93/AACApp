package com.kon.myaacapp.ui.communication

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CommunicationRoute(
    viewModelFactory: CommunicationViewModelFactory,
    onNavigateBackFromRoot: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    content: @Composable (
        state: CommunicationState,
        onAction: (CommunicationAction) -> Unit,
    ) -> Unit,
) {
    val context = LocalContext.current

    val communicationViewModel:
            CommunicationViewModel =
        viewModel(
            key = "communication",
            factory = viewModelFactory,
        )

    val state by communicationViewModel.state
        .collectAsStateWithLifecycle()

    LaunchedEffect(
        communicationViewModel,
        onNavigateBackFromRoot,
        onNavigateToAdmin,
    ) {
        communicationViewModel.effects.collect { effect ->
            when (effect) {
                CommunicationEffect
                    .NavigateBackFromRoot -> {
                    onNavigateBackFromRoot()
                }

                CommunicationEffect
                    .OpenAdminSettings -> {
                    onNavigateToAdmin()
                }

                is CommunicationEffect.ShowError -> {
                    Toast.makeText(
                        context,
                        effect.message,
                        Toast.LENGTH_LONG,
                    ).show()

                    communicationViewModel.onAction(
                        CommunicationAction.ErrorConsumed
                    )
                }
            }
        }
    }

    content(
        state,
        communicationViewModel::onAction,
    )
}