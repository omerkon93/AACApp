package com.kon.myaacapp.ui.admin.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LayoutSettingsRoute(
    viewModelFactory: LayoutSettingsViewModelFactory,
    onMessage: (String) -> Unit = {},
    content: @Composable (
        state: LayoutSettingsState,
        onAction: (LayoutSettingsAction) -> Unit,
    ) -> Unit,
) {
    val layoutSettingsViewModel:
            LayoutSettingsViewModel = viewModel(
        key = "layout-settings",
        factory = viewModelFactory,
    )

    val state by layoutSettingsViewModel.state
        .collectAsStateWithLifecycle()

    LaunchedEffect(layoutSettingsViewModel) {
        layoutSettingsViewModel.effects.collect { effect ->
            when (effect) {
                LayoutSettingsEffect
                    .CurrentLayoutSavedAsDefault -> {
                    onMessage(
                        "Current layout saved as default."
                    )
                }

                LayoutSettingsEffect
                    .DefaultLayoutRestored -> {
                    onMessage(
                        "Default layout restored."
                    )
                }

                is LayoutSettingsEffect.ShowError -> {
                    onMessage(effect.message)
                }
            }
        }
    }

    content(
        state,
        layoutSettingsViewModel::onAction,
    )
}