package com.kon.myaacapp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository

class ProfileManagerViewModelFactory(
    private val profileRepository: ProfileRepository,
    private val aacRepository: AACRepository,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                ProfileManagerViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return ProfileManagerViewModel(
                profileRepository = profileRepository,
                aacRepository = aacRepository,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}