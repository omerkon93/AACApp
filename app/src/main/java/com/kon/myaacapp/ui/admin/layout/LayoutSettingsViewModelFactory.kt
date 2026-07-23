package com.kon.myaacapp.ui.admin.layout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kon.myaacapp.data.repository.SettingsRepository

class LayoutSettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                LayoutSettingsViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return LayoutSettingsViewModel(
                settingsRepository = settingsRepository,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}