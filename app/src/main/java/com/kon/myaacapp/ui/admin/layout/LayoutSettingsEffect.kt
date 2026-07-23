package com.kon.myaacapp.ui.admin.layout

sealed interface LayoutSettingsEffect {

    data object CurrentLayoutSavedAsDefault :
        LayoutSettingsEffect

    data object DefaultLayoutRestored :
        LayoutSettingsEffect

    data class ShowError(
        val message: String,
    ) : LayoutSettingsEffect
}