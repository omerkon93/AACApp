package com.kon.myaacapp.ui.admin.system

import android.net.Uri
import com.kon.myaacapp.domain.service.Gender

sealed interface SystemSettingsAction {

    data object OpenProfilesClicked :
        SystemSettingsAction

    data class SpeakOnTilePressChanged(
        val value: Boolean,
    ) : SystemSettingsAction

    data class GenderChanged(
        val value: Gender,
    ) : SystemSettingsAction

    data class LanguageChanged(
        val languageCode: String,
    ) : SystemSettingsAction

    data object SaveBackupClicked :
        SystemSettingsAction

    data class SaveBackupDestinationSelected(
        val uri: Uri,
    ) : SystemSettingsAction

    data object ShareBackupClicked :
        SystemSettingsAction

    data object ImportBackupClicked :
        SystemSettingsAction

    data class ImportBackupFileSelected(
        val uri: Uri,
    ) : SystemSettingsAction

    data object ShowResetConfirmation :
        SystemSettingsAction

    data object HideResetConfirmation :
        SystemSettingsAction

    data object ConfirmReset :
        SystemSettingsAction

    data object StatusMessageConsumed :
        SystemSettingsAction

    data object ErrorConsumed :
        SystemSettingsAction
}