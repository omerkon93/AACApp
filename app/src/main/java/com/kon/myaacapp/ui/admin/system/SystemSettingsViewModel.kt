package com.kon.myaacapp.ui.admin.system

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kon.myaacapp.core.locale.DownloadStatus
import com.kon.myaacapp.core.locale.LanguageDownloadHelper
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import com.kon.myaacapp.core.locale.LocaleHelper
import com.kon.myaacapp.data.repository.AACRepository
import com.kon.myaacapp.data.repository.ProfileRepository
import com.kon.myaacapp.data.repository.SettingsRepository
import com.kon.myaacapp.domain.service.BackupManager
import com.kon.myaacapp.domain.service.Gender
import com.kon.myaacapp.service.backup.BackupService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SystemSettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val aacRepository: AACRepository,
    private val profileRepository: ProfileRepository,
) : AndroidViewModel(application) {

    private val backupService =
        BackupService(
            application,
            aacRepository,
        )

    private val backupManager =
        BackupManager(
            application = application,
            backupService = backupService,
            repository = aacRepository,
            profileRepository = profileRepository,
            scope = viewModelScope,
        )

    private val languageDownloadHelper =
        LanguageDownloadHelper(application)

    private val operationState =
        MutableStateFlow(
            SystemSettingsOperationState()
        )

    private val _effects =
        MutableSharedFlow<SystemSettingsEffect>()

    val effects: SharedFlow<SystemSettingsEffect> =
        _effects.asSharedFlow()

    private val generalSettings =
        combine(
            settingsRepository.speakOnTilePressFlow,
            settingsRepository.languageCodeFlow,
            settingsRepository.userGenderFlow,
        ) {
                speakOnTilePress,
                languageCode,
                userGender ->

            GeneralSystemSettings(
                speakOnTilePress = speakOnTilePress,
                languageCode =
                    LocaleHelper.normalize(languageCode),
                userGender = userGender,
            )
        }

    private val systemOperationStatus =
        combine(
            languageDownloadHelper.downloadStatus,
            backupManager.status,
            operationState,
        ) {
                downloadStatus,
                backupStatus,
                operation ->

            SystemOperationStatus(
                downloadStatus = downloadStatus,
                backupStatus = backupStatus,
                operation = operation,
            )
        }

    val state: StateFlow<SystemSettingsState> =
        combine(
            generalSettings,
            systemOperationStatus,
        ) {
                settings,
                status ->

            SystemSettingsState(
                speakOnTilePress =
                    settings.speakOnTilePress,

                languageCode =
                    settings.languageCode,

                userGender =
                    settings.userGender,

                languageDownloadStatus =
                    status.downloadStatus,

                showResetConfirmation =
                    status
                        .operation
                        .showResetConfirmation,

                isImporting =
                    status.operation.isImporting,

                isExporting =
                    status.operation.isExporting,

                isSharing =
                    status.operation.isSharing,

                isResetting =
                    status.operation.isResetting,

                statusMessage =
                    status.backupStatus,

                errorMessage =
                    status.operation.errorMessage,
            )
        }.asStateFlow(
            scope = viewModelScope,
            initialValue = SystemSettingsState(),
        )

    fun onAction(
        action: SystemSettingsAction,
    ) {
        when (action) {
            SystemSettingsAction
                .OpenProfilesClicked -> {
                emitEffect(
                    SystemSettingsEffect.OpenProfiles
                )
            }

            is SystemSettingsAction
            .SpeakOnTilePressChanged -> {
                updateSpeakOnTilePress(
                    value = action.value,
                )
            }

            is SystemSettingsAction.GenderChanged -> {
                updateGender(
                    value = action.value,
                )
            }

            is SystemSettingsAction.LanguageChanged -> {
                changeLanguage(
                    requestedLanguage =
                        action.languageCode,
                )
            }

            SystemSettingsAction.SaveBackupClicked -> {
                requestBackupDestination()
            }

            is SystemSettingsAction
            .SaveBackupDestinationSelected -> {
                exportBackup(
                    uri = action.uri,
                )
            }

            SystemSettingsAction.ShareBackupClicked -> {
                shareBackup()
            }

            SystemSettingsAction.ImportBackupClicked -> {
                emitEffect(
                    SystemSettingsEffect
                        .OpenBackupImportPicker
                )
            }

            is SystemSettingsAction
            .ImportBackupFileSelected -> {
                importBackup(
                    uri = action.uri,
                )
            }

            SystemSettingsAction
                .ShowResetConfirmation -> {
                operationState.update { currentState ->
                    currentState.copy(
                        showResetConfirmation = true,
                    )
                }
            }

            SystemSettingsAction
                .HideResetConfirmation -> {
                operationState.update { currentState ->
                    currentState.copy(
                        showResetConfirmation = false,
                    )
                }
            }

            SystemSettingsAction.ConfirmReset -> {
                resetToDefault()
            }

            SystemSettingsAction
                .StatusMessageConsumed -> {
                backupManager.clearStatus()
            }

            SystemSettingsAction.ErrorConsumed -> {
                operationState.update { currentState ->
                    currentState.copy(
                        errorMessage = null,
                    )
                }
            }
        }
    }

    private fun updateSpeakOnTilePress(
        value: Boolean,
    ) {
        viewModelScope.launch {
            runCatching {
                settingsRepository
                    .updateSpeakOnTilePress(value)
            }.onFailure { error ->
                showError(
                    message = error.message
                        ?: "Failed to update speech setting.",
                )
            }
        }
    }

    private fun updateGender(
        value: Gender,
    ) {
        viewModelScope.launch {
            runCatching {
                settingsRepository
                    .updateUserGender(value)
            }.onFailure { error ->
                showError(
                    message = error.message
                        ?: "Failed to update grammatical gender.",
                )
            }
        }
    }

    private fun changeLanguage(
        requestedLanguage: String,
    ) {
        val normalizedLanguage =
            LocaleHelper.normalize(
                requestedLanguage
            )

        if (
            normalizedLanguage ==
            state.value.languageCode
        ) {
            return
        }

        if (
            state.value
                .isLanguageOperationRunning
        ) {
            return
        }

        languageDownloadHelper.downloadLanguage(
            languageCode = normalizedLanguage,
        ) { downloadSucceeded ->
            if (!downloadSucceeded) {
                showError(
                    message =
                        "Failed to download the selected language.",
                )

                return@downloadLanguage
            }

            viewModelScope.launch {
                runCatching {
                    settingsRepository
                        .updateLanguageCode(
                            normalizedLanguage
                        )

                    aacRepository.prepareLanguage(
                        languageCode =
                            normalizedLanguage,
                    )
                }.onSuccess {
                    emitEffect(
                        SystemSettingsEffect
                            .RecreateActivity
                    )
                }.onFailure { error ->
                    showError(
                        message = error.message
                            ?: "Failed to change language.",
                    )
                }
            }
        }
    }

    private fun requestBackupDestination() {
        if (!state.value.canRunBackupOperation) {
            return
        }

        emitEffect(
            SystemSettingsEffect
                .OpenBackupDestinationPicker(
                    suggestedFileName =
                        createBackupFileName(),
                )
        )
    }

    private fun exportBackup(
        uri: android.net.Uri,
    ) {
        if (!state.value.canRunBackupOperation) {
            return
        }

        backupManager.clearStatus()

        operationState.update { currentState ->
            currentState.copy(
                isExporting = true,
                errorMessage = null,
            )
        }

        backupManager.exportDatabase(
            uri = uri,
            contentResolver =
                getApplication<Application>()
                    .contentResolver,
        )

        observeBackupOperationCompletion(
            operation =
                SystemBackupOperation.EXPORT,
        )
    }

    private fun importBackup(
        uri: android.net.Uri,
    ) {
        if (!state.value.canRunBackupOperation) {
            return
        }

        backupManager.clearStatus()

        operationState.update { currentState ->
            currentState.copy(
                isImporting = true,
                errorMessage = null,
            )
        }

        backupManager.importDatabase(
            uri = uri,
            contentResolver =
                getApplication<Application>()
                    .contentResolver,
        )

        observeBackupOperationCompletion(
            operation =
                SystemBackupOperation.IMPORT,
        )
    }

    private fun shareBackup() {
        if (!state.value.canRunBackupOperation) {
            return
        }

        operationState.update { currentState ->
            currentState.copy(
                isSharing = true,
                errorMessage = null,
            )
        }

        backupManager.exportAndShare { uri ->
            operationState.update { currentState ->
                currentState.copy(
                    isSharing = false,
                )
            }

            if (uri != null) {
                emitEffect(
                    SystemSettingsEffect.ShareBackup(
                        uri = uri,
                    )
                )
            } else {
                showError(
                    message =
                        "Failed to create the backup file.",
                )
            }
        }
    }

    private fun resetToDefault() {
        if (state.value.isBusy) {
            return
        }

        backupManager.clearStatus()

        operationState.update { currentState ->
            currentState.copy(
                showResetConfirmation = false,
                isResetting = true,
                errorMessage = null,
            )
        }

        backupManager.resetToDefault()

        observeBackupOperationCompletion(
            operation =
                SystemBackupOperation.RESET,
        )
    }

    private fun observeBackupOperationCompletion(
        operation: SystemBackupOperation,
    ) {
        viewModelScope.launch {
            val message =
                backupManager.status
                    .filterNotNull()
                    .first()

            when (operation) {
                SystemBackupOperation.EXPORT -> {
                    operationState.update { currentState ->
                        currentState.copy(
                            isExporting = false,
                        )
                    }
                }

                SystemBackupOperation.IMPORT -> {
                    operationState.update { currentState ->
                        currentState.copy(
                            isImporting = false,
                        )
                    }
                }

                SystemBackupOperation.RESET -> {
                    operationState.update { currentState ->
                        currentState.copy(
                            isResetting = false,
                        )
                    }
                }
            }

            emitEffect(
                SystemSettingsEffect.ShowMessage(
                    message = message,
                )
            )
        }
    }

    private fun createBackupFileName(): String {
        val timestamp =
            SimpleDateFormat(
                "yyyy-MM-dd_HH-mm",
                Locale.US,
            ).format(Date())

        return "MyAACApp_Backup_$timestamp.zip"
    }

    private fun emitEffect(
        effect: SystemSettingsEffect,
    ) {
        viewModelScope.launch {
            _effects.emit(effect)
        }
    }

    private fun showError(
        message: String,
    ) {
        operationState.update { currentState ->
            currentState.copy(
                errorMessage = message,
            )
        }

        emitEffect(
            SystemSettingsEffect.ShowError(
                message = message,
            )
        )
    }

    override fun onCleared() {
        languageDownloadHelper.unregister()
    }
}

private data class GeneralSystemSettings(
    val speakOnTilePress: Boolean,
    val languageCode: String,
    val userGender: Gender,
)

private data class SystemOperationStatus(
    val downloadStatus: DownloadStatus,
    val backupStatus: String?,
    val operation: SystemSettingsOperationState,
)

private data class SystemSettingsOperationState(
    val showResetConfirmation: Boolean = false,
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val isSharing: Boolean = false,
    val isResetting: Boolean = false,
    val errorMessage: String? = null,
)

private enum class SystemBackupOperation {
    IMPORT,
    EXPORT,
    RESET,
}

private fun <T> Flow<T>.asStateFlow(
    scope: CoroutineScope,
    initialValue: T,
): StateFlow<T> {
    return stateIn(
        scope = scope,
        started =
            SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000,
            ),
        initialValue = initialValue,
    )
}