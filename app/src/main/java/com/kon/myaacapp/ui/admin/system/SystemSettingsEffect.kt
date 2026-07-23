package com.kon.myaacapp.ui.admin.system

import android.net.Uri

sealed interface SystemSettingsEffect {

    data object OpenProfiles :
        SystemSettingsEffect

    data class OpenBackupDestinationPicker(
        val suggestedFileName: String,
    ) : SystemSettingsEffect

    data object OpenBackupImportPicker :
        SystemSettingsEffect

    data class ShareBackup(
        val uri: Uri,
    ) : SystemSettingsEffect

    data object RecreateActivity :
        SystemSettingsEffect

    data class ShowMessage(
        val message: String,
    ) : SystemSettingsEffect

    data class ShowError(
        val message: String,
    ) : SystemSettingsEffect
}