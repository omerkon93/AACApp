package com.kon.myaacapp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.domain.entitlement.FamilyEntitlementProvider
import com.kon.myaacapp.domain.entitlement.StoreFamilyEntitlementProvider

class ProfileManagerViewModelFactory(
    private val profileRepository: ProfileRepository,
    private val aacRepository: AACRepository,
    private val familyEntitlementProvider: FamilyEntitlementProvider =
        StoreFamilyEntitlementProvider(),
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
                familyEntitlementProvider = familyEntitlementProvider,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}
