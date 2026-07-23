package com.kon.myaacapp.ui.admin.system

import androidx.compose.runtime.Immutable
import com.kon.myaacapp.core.locale.DownloadStatus
import com.kon.myaacapp.domain.service.Gender

@Immutable
data class SystemSettingsState(
    val speakOnTilePress: Boolean = true,
    val languageCode: String = "he",
    val userGender: Gender = Gender.MALE,

    val languageDownloadStatus: DownloadStatus =
        DownloadStatus.Idle,

    val showResetConfirmation: Boolean = false,

    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val isSharing: Boolean = false,
    val isResetting: Boolean = false,

    val statusMessage: String? = null,
    val errorMessage: String? = null,
) {
    val isLanguageOperationRunning: Boolean
        get() =
            languageDownloadStatus is
                    DownloadStatus.Downloading ||
                    languageDownloadStatus is
                            DownloadStatus.Installing

    val isBackupOperationRunning: Boolean
        get() =
            isImporting ||
                    isExporting ||
                    isSharing

    val isBusy: Boolean
        get() =
            isLanguageOperationRunning ||
                    isBackupOperationRunning ||
                    isResetting

    val canChangeLanguage: Boolean
        get() = !isLanguageOperationRunning

    val canRunBackupOperation: Boolean
        get() =
            !isBackupOperationRunning &&
                    !isResetting
}