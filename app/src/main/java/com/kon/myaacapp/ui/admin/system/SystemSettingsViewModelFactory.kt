package com.kon.myaacapp.ui.admin.system

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository

class SystemSettingsViewModelFactory(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val aacRepository: AACRepository,
    private val profileRepository: ProfileRepository,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                SystemSettingsViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return SystemSettingsViewModel(
                application = application,
                settingsRepository =
                    settingsRepository,
                aacRepository = aacRepository,
                profileRepository =
                    profileRepository,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}